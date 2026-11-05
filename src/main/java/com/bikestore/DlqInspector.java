package com.bikestore;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.GetResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lists the messages currently in payment.dlq without losing them.
 * basicGet leaves each message unacknowledged (so it is NOT re-fetched during the loop);
 * after reading them all, each is nacked with requeue=true so it returns to the DLQ.
 */
public final class DlqInspector {

    public static void run() throws Exception {
        Log.init("DlqInspector");
        Connection conn = Rabbit.connect();
        Channel ch = conn.createChannel();
        Rabbit.declareTopology(ch);

        Log.log(null, "Inspeccionando payment.dlq ...");
        List<Long> tags = new ArrayList<>();
        int count = 0;
        GetResponse r;
        while ((r = ch.basicGet(Rabbit.DLQ, false)) != null) {
            count++;
            tags.add(r.getEnvelope().getDeliveryTag());
            JsonNode o = Json.MAPPER.readTree(r.getBody());
            int reintentos = retries(r);
            Log.log(o.path("pedidoId").asText("-"),
                    String.format("DLQ #%d | cliente=%s | total=%s | reintentos=%d",
                            count,
                            o.path("cliente").asText("-"),
                            o.path("total").asText("-"),
                            reintentos));
        }

        // Return everything to the queue (requeue=true) so nothing is lost.
        for (long tag : tags) {
            ch.basicNack(tag, false, true);
        }
        Log.log(null, "Total de mensajes en payment.dlq: " + count + " (devueltos a la cola)");

        ch.close();
        conn.close();
    }

    private static int retries(GetResponse r) {
        Map<String, Object> h = r.getProps().getHeaders();
        if (h != null && h.get("x-retry-count") instanceof Number) {
            return ((Number) h.get("x-retry-count")).intValue();
        }
        return Rabbit.MAX_RETRIES;
    }

    private DlqInspector() {}
}
