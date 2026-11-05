package com.bikestore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * HTTP API (POST /api/orders) built on com.sun.net.httpserver.
 * Validates the order, assigns a pedidoId, recomputes the total server-side,
 * publishes a persistent order.created message, waits for the publisher confirm
 * and answers 202 Accepted.
 */
public final class OrderProducer {

    private static Connection conn;
    // One confirm-mode channel per HTTP worker thread (channels are not thread-safe).
    private static ThreadLocal<Channel> channels;

    public static void run() throws Exception {
        Log.init("OrderProducer");
        conn = Rabbit.connect();
        try (Channel setup = conn.createChannel()) {
            Rabbit.declareTopology(setup);
        }
        channels = ThreadLocal.withInitial(() -> {
            try {
                Channel c = conn.createChannel();
                c.confirmSelect();
                return c;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        int port = Integer.parseInt(Rabbit.env("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/orders", OrderProducer::handle);
        server.setExecutor(Executors.newFixedThreadPool(8, Rabbit.namedThreads("http-producer")));
        server.start();
        Log.log(null, "API escuchando en http://localhost:" + port + "/api/orders");
    }

    private static void handle(HttpExchange ex) throws IOException {
        String pedidoId = null;
        try {
            if (!"POST".equals(ex.getRequestMethod())) {
                send(ex, 405, "{\"error\":\"metodo no permitido\"}");
                return;
            }
            JsonNode body = Json.MAPPER.readTree(ex.getRequestBody());
            String err = validate(body);
            if (err != null) {
                Log.log(null, "Pedido invalido rechazado (400): " + err);
                send(ex, 400, "{\"error\":\"" + err + "\"}");
                return;
            }

            ObjectNode order = (ObjectNode) body;
            pedidoId = "PED-" + String.format("%08X", ThreadLocalRandom.current().nextInt());

            double total = 0;
            for (JsonNode it : order.get("items")) {
                total += it.get("cantidad").asInt() * it.get("precio").asDouble();
            }
            order.put("pedidoId", pedidoId);
            order.put("total", total);
            order.put("status", "CREATED");

            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .contentType("application/json")
                    .deliveryMode(2) // persistente
                    .build();

            Channel ch = channels.get();
            ch.basicPublish(Rabbit.ORDERS_EXCHANGE, Rabbit.RK_CREATED, props,
                    Json.MAPPER.writeValueAsBytes(order));
            ch.waitForConfirmsOrDie(5000); // publisher confirm

            Log.log(pedidoId, "Pedido creado y publicado (order.created), cliente="
                    + order.get("cliente").asText() + ", total=" + total);
            send(ex, 202, "{\"pedidoId\":\"" + pedidoId + "\",\"total\":" + total
                    + ",\"status\":\"ACCEPTED\"}");
        } catch (Exception e) {
            Log.log(pedidoId, "Error al procesar/publicar el pedido: " + e);
            send(ex, 500, "{\"error\":\"error interno\"}");
        } finally {
            ex.close();
        }
    }

    /** @return error message if invalid, or null if valid. */
    private static String validate(JsonNode b) {
        if (b == null || !b.isObject()) return "cuerpo JSON invalido";
        JsonNode cliente = b.get("cliente");
        if (cliente == null || !cliente.isTextual() || cliente.asText().trim().isEmpty()) {
            return "cliente requerido";
        }
        JsonNode items = b.get("items");
        if (items == null || !items.isArray() || items.size() == 0) {
            return "items requerido";
        }
        for (JsonNode it : items) {
            JsonNode cantidad = it.get("cantidad");
            JsonNode precio = it.get("precio");
            if (cantidad == null || !cantidad.isNumber() || cantidad.asInt() <= 0) {
                return "cantidad invalida";
            }
            if (precio == null || !precio.isNumber() || precio.asDouble() < 0) {
                return "precio invalido";
            }
        }
        return null;
    }

    private static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private OrderProducer() {}
}
