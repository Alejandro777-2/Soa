package com.bikestore;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Consumes payment.queue with 2 threads, prefetch=1, manual ack.
 * Each attempt fails with 50% probability.
 *   success -> publish order.paid (PAID), ack
 *   failure -> send to payment.retry.queue (TTL 2000ms -> back to payment.queue), inc x-retry-count
 *   after MAX_RETRIES retries fail -> publish order.payment_failed (FAILED) + basicReject(requeue=false)
 *                                     so the broker routes it via bikestore.dlx to payment.dlq.
 */
public final class PaymentWorker {

    private static final int ATTEMPTS = Rabbit.MAX_RETRIES + 1; // 4

    public static void run() throws Exception {
        Log.init("PaymentWorker");
        // Consumer callbacks run on this executor -> threads named payment-worker-N.
        ExecutorService exec = Executors.newFixedThreadPool(2, Rabbit.namedThreads("payment-worker"));
        Connection conn = Rabbit.connect(exec);
        try (Channel setup = conn.createChannel()) {
            Rabbit.declareTopology(setup);
        }
        // Two channels, each prefetch=1 -> up to 2 messages in flight across the 2 threads.
        for (int i = 0; i < 2; i++) {
            Channel ch = conn.createChannel();
            ch.basicQos(1);
            startConsumer(ch);
        }
        Log.log(null, "PaymentWorker consumiendo payment.queue (2 hilos, prefetch=1)");
    }

    private static void startConsumer(Channel ch) throws IOException {
        DeliverCallback deliver = (consumerTag, d) -> handle(ch, d);
        ch.basicConsume(Rabbit.PAYMENT_QUEUE, false, deliver, consumerTag -> { });
    }

    private static void handle(Channel ch, Delivery d) {
        long tag = d.getEnvelope().getDeliveryTag();
        String pedidoId = "-";
        try {
            ObjectNode order = (ObjectNode) Json.MAPPER.readTree(d.getBody());
            pedidoId = order.path("pedidoId").asText("-");

            int retry = retryCount(d);
            int attempt = retry + 1;
            Log.log(pedidoId, "Intento de cobro " + attempt + "/" + ATTEMPTS);

            boolean ok = ThreadLocalRandom.current().nextDouble() >= 0.5; // 50% exito
            if (ok) {
                order.put("paymentStatus", "PAID");
                order.put("status", "PAID");
                publish(ch, Rabbit.RK_PAID, order);
                ch.basicAck(tag, false);
                Log.log(pedidoId, "Cobro EXITOSO -> publicado order.paid (PAID)");
                return;
            }

            Log.log(pedidoId, "Cobro RECHAZADO (intento " + attempt + "/" + ATTEMPTS + ")");
            if (retry >= Rabbit.MAX_RETRIES) {
                order.put("paymentStatus", "FAILED");
                order.put("status", "FAILED");
                publish(ch, Rabbit.RK_FAILED, order);
                Log.log(pedidoId, "Maximo de reintentos agotado -> order.payment_failed (FAILED), "
                        + "enviando a DLQ via bikestore.dlx (payment.dlq)");
                ch.basicReject(tag, false);
            } else {
                int next = retry + 1;
                Log.log(pedidoId, "Reintento #" + next + " -> payment.retry.queue (TTL "
                        + Rabbit.RETRY_TTL + "ms)");
                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                        .contentType("application/json")
                        .deliveryMode(2)
                        .headers(Collections.singletonMap("x-retry-count", (Object) next))
                        .build();
                ch.basicPublish("", Rabbit.RETRY_QUEUE, props, d.getBody());
                ch.basicAck(tag, false);
            }
        } catch (Exception e) {
            Log.log(pedidoId, "Error procesando el cobro: " + e);
            try {
                ch.basicReject(tag, false);
            } catch (IOException ignored) {
                // channel probably gone; broker will redeliver
            }
        }
    }

    private static int retryCount(Delivery d) {
        Map<String, Object> h = d.getProperties().getHeaders();
        if (h != null && h.get("x-retry-count") instanceof Number) {
            return ((Number) h.get("x-retry-count")).intValue();
        }
        return 0;
    }

    private static void publish(Channel ch, String routingKey, ObjectNode order) throws IOException {
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .deliveryMode(2)
                .build();
        ch.basicPublish(Rabbit.ORDERS_EXCHANGE, routingKey, props, Json.MAPPER.writeValueAsBytes(order));
    }

    private PaymentWorker() {}
}
