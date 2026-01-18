package com.bank.simulator.ui;

import com.bank.simulator.model.Account;
import com.bank.simulator.repository.AccountRepository;
import com.bank.simulator.repository.TransactionRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class DashboardServer {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public DashboardServer(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", new StaticHandler());
        server.createContext("/api/metrics", new MetricsHandler());
        server.createContext("/api/logs", new LogsHandler());

        server.setExecutor(null); // default executor
        server.start();
        System.out.println(">> Dashboard started at http://localhost:8080/");
    }

    private class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String content = new String(Files.readAllBytes(Paths.get("src/main/resources/index.html")));
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Very simple JSON construction (avoiding external libraries like Jackson/Gson
            // for this demo)
            String json = accountRepository.findAll().stream()
                    .map(a -> String.format("{\"account\":\"%s\", \"holder\":\"%s\", \"balance\":%s}",
                            a.getAccountNumber(), a.getHolderName(), a.getBalance()))
                    .collect(Collectors.joining(",", "[", "]"));

            sendJson(exchange, json);
        }
    }

    private class LogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Get last 20 transactions
            String json = transactionRepository.findAll().stream()
                    .sorted((t1, t2) -> t2.getTimestamp().compareTo(t1.getTimestamp())) // Newest first
                    .limit(20)
                    .map(t -> String.format(
                            "{\"id\":\"%s\", \"src\":\"%s\", \"dest\":\"%s\", \"amount\":%s, \"status\":\"%s\", \"time\":\"%s\"}",
                            t.getTransactionId(), t.getSourceAccount(), t.getDestinationAccount(), t.getAmount(),
                            t.getStatus(), t.getTimestamp()))
                    .collect(Collectors.joining(",", "[", "]"));

            sendJson(exchange, json);
        }
    }

    private void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
