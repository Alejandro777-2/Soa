package com.bikestore;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Connection factory + topology. Every component declares the topology on startup (idempotent). */
public final class Rabbit {
    public static final String ORDERS_EXCHANGE = "bikestore.orders";
    public static final String DLX = "bikestore.dlx";
    public static final String PAYMENT_QUEUE = "payment.queue";
    public static final String RETRY_QUEUE = "payment.retry.queue";
    public static final String DLQ = "payment.dlq";
    public static final String EMAIL_QUEUE = "email.queue";
    public static final String DEAD_RK = "payment.dead";
    public static final int RETRY_TTL = 2000;
    public static final int MAX_RETRIES = 3;

    public static final String RK_CREATED = "order.created";
    public static final String RK_PAID = "order.paid";
    public static final String RK_FAILED = "order.payment_failed";

    private Rabbit() {}

    public static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null ? def : v;
    }

    private static ConnectionFactory factory() {
        ConnectionFactory f = new ConnectionFactory();
        f.setHost(env("RABBITMQ_HOST", "localhost"));
        f.setPort(Integer.parseInt(env("RABBITMQ_PORT", "5672")));
        f.setUsername(env("RABBITMQ_USER", "guest"));
        f.setPassword(env("RABBITMQ_PASS", "guest"));
        f.setAutomaticRecoveryEnabled(true);
        return f;
    }

    public static Connection connect() throws Exception {
        return factory().newConnection();
    }

    /** Connection whose consumer callbacks run on the given executor (for explicit thread names). */
    public static Connection connect(ExecutorService executor) throws Exception {
        return factory().newConnection(executor);
    }

    /** Thread factory producing prefix-1, prefix-2, ... */
    public static ThreadFactory namedThreads(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> new Thread(r, prefix + "-" + n.incrementAndGet());
    }

    public static void declareTopology(Channel ch) throws IOException {
        ch.exchangeDeclare(ORDERS_EXCHANGE, "topic", true);
        ch.exchangeDeclare(DLX, "topic", true);

        // payment.queue: on basicReject(requeue=false) -> bikestore.dlx -> payment.dlq
        Map<String, Object> pq = new HashMap<>();
        pq.put("x-dead-letter-exchange", DLX);
        pq.put("x-dead-letter-routing-key", DEAD_RK);
        ch.queueDeclare(PAYMENT_QUEUE, true, false, false, pq);
        ch.queueBind(PAYMENT_QUEUE, ORDERS_EXCHANGE, RK_CREATED);

        // retry queue: holds the message TTL ms, then dead-letters back to payment.queue
        // via the default exchange (routing key = queue name).
        Map<String, Object> rq = new HashMap<>();
        rq.put("x-message-ttl", RETRY_TTL);
        rq.put("x-dead-letter-exchange", "");
        rq.put("x-dead-letter-routing-key", PAYMENT_QUEUE);
        ch.queueDeclare(RETRY_QUEUE, true, false, false, rq);

        // dead-letter queue
        ch.queueDeclare(DLQ, true, false, false, null);
        ch.queueBind(DLQ, DLX, DEAD_RK);

        // email queue: everything (order.created, order.paid, order.payment_failed)
        ch.queueDeclare(EMAIL_QUEUE, true, false, false, null);
        ch.queueBind(EMAIL_QUEUE, ORDERS_EXCHANGE, "order.#");
    }
}
