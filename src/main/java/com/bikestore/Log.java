package com.bikestore;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Console + file logger with the format:
 *   HH:mm:ss.SSS [thread] Componente | pedidoId=... | mensaje
 * Each component writes its own logs/<Componente>.log.
 */
public final class Log {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static String component = "app";
    private static PrintWriter file;

    private Log() {}

    public static synchronized void init(String comp) {
        component = comp;
        try {
            new File("logs").mkdirs();
            file = new PrintWriter(new FileWriter("logs/" + comp + ".log", true), true);
        } catch (IOException e) {
            System.err.println("No se pudo abrir el archivo de log: " + e.getMessage());
        }
    }

    public static synchronized void log(String pedidoId, String msg) {
        String line = String.format("%s [%s] %s | pedidoId=%s | %s",
                LocalTime.now().format(TS),
                Thread.currentThread().getName(),
                component,
                pedidoId == null || pedidoId.isEmpty() ? "-" : pedidoId,
                msg);
        System.out.println(line);
        if (file != null) {
            file.println(line);
        }
    }
}
