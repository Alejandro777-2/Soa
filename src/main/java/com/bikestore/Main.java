package com.bikestore;

/** Single entrypoint for the shaded jar: dispatches on the first argument. */
public final class Main {
    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "producer";
        switch (cmd) {
            case "producer": OrderProducer.run(); break;
            case "payment":  PaymentWorker.run(); break;
            case "email":    EmailWorker.run();   break;
            case "dlq":      DlqInspector.run();  break;
            default:
                System.out.println("Uso: java -jar bikestore-async-1.0.jar <producer|payment|email|dlq>");
                System.exit(1);
        }
    }

    private Main() {}
}
