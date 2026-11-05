package com.bikestore;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Independent consumer of email.queue (binding order.#).
 * Sends the (simulated) e-mail only when paymentStatus=PAID.
 * Idempotent: a pedidoId already e-mailed is skipped on redelivery.
 */
public final class EmailWorker {

    // ponytail: in-memory dedupe set, fine for a single EmailWorker process; use a shared store if scaled out.
    private static final Set<String> sent = ConcurrentHashMap.newKeySet();

    public static void run() throws Exception {
        Log.init("EmailWorker");
        ExecutorService exec = Executors.newFixedThreadPool(1, Rabbit.namedThreads("email-worker"));
        Connection conn = Rabbit.connect(exec);
        Channel ch = conn.createChannel();
        Rabbit.declareTopology(ch);
        ch.basicQos(1);

        DeliverCallback deliver = (consumerTag, d) -> {
            String pedidoId = "-";
            try {
                JsonNode order = Json.MAPPER.readTree(d.getBody());
                pedidoId = order.path("pedidoId").asText("-");
                String paymentStatus = order.path("paymentStatus").asText(null);
                String cliente = order.path("cliente").asText("-");

                if ("PAID".equals(paymentStatus)) {
                    if (sent.add(pedidoId)) {
                        Log.log(pedidoId, "Enviando e-mail de confirmacion a " + cliente + " (simulado)");
                    } else {
                        Log.log(pedidoId, "E-mail ya enviado, ignorando reentrega (idempotente)");
                    }
                } else {
                    String motivo = paymentStatus == null
                            ? "evento order.created sin pago confirmado"
                            : "paymentStatus=" + paymentStatus;
                    Log.log(pedidoId, "No se envia e-mail (" + motivo + ")");
                }
            } catch (Exception e) {
                Log.log(pedidoId, "Error procesando e-mail: " + e);
            } finally {
                ch.basicAck(d.getEnvelope().getDeliveryTag(), false);
            }
        };
        ch.basicConsume(Rabbit.EMAIL_QUEUE, false, deliver, consumerTag -> { });
        Log.log(null, "EmailWorker consumiendo email.queue (binding order.#)");
    }

    private EmailWorker() {}
}
