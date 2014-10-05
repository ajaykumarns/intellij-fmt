package com.anadathur.intellij.fmtplugin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by ajay.nadathur on 10/1/14.
 */
public class ApplicationServer {
    private final int port;
    private final RequestHandler formatter;
    private com.sun.net.httpserver.HttpServer httpServer;
    private boolean debug = true;

    public ApplicationServer(int port, RequestHandler formatter) {
        this.port = port;
        this.formatter = formatter;
    }

    String asString(Enumeration<String> headers) {
        StringBuilder bldr = new StringBuilder();
        while (headers.hasMoreElements()) {
            bldr.append(headers.nextElement()).append(", ");
        }

        return bldr.toString();
    }

    public void start() throws Exception {
        this.httpServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService service = Executors.newFixedThreadPool(1);
        httpServer.setExecutor(service);
        httpServer.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange arg) throws IOException {
                long before = System.currentTimeMillis();
                String tName = Thread.currentThread().getName();
                debug("[%s] URI : %s\n", tName, arg.getRequestURI());
                String completeFilePath = arg.getRequestURI().toString();
                if (completeFilePath.startsWith("//")) {
                    completeFilePath = completeFilePath.replaceFirst("/", "");
                }

                File file = new File(completeFilePath);
                try {
                    formatter.format(file);
                    System.out.printf("[%s] Formatting %s completed in %s ms.\n", tName, completeFilePath, (System.currentTimeMillis() - before));
                    arg.sendResponseHeaders(200, 0);
                    arg.getResponseBody().close();
                } catch (Exception e) {
                    System.err.println("Encountered error trying to handle request: " + arg.getRequestURI());
                    e.printStackTrace();
                    arg.sendResponseHeaders(500, 0);
                    arg.getResponseBody().close();
                }
            }
        });

        httpServer.start();
    }

    public void join() throws InterruptedException {
        synchronized (httpServer) {
            httpServer.wait();
        }
    }

    private void debug(String s, Object... args) {
        if (debug) {
            System.out.printf(s, args);
        }
    }

    public void stop() {
        this.httpServer.stop(1);
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public static interface RequestHandler {
        void format(File file);
    }

}
