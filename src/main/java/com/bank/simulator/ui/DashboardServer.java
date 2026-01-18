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
    private final com.bank.simulator.service.BankService bankService; // Added bridge

    public DashboardServer(AccountRepository accountRepository, TransactionRepository transactionRepository,
            com.bank.simulator.service.BankService bankService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.bankService = bankService;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", new StaticHandler());
        server.createContext("/api/metrics", new MetricsHandler());
        server.createContext("/api/logs", new LogsHandler());
        server.createContext("/api/transfer", new TransferHandler());
        server.createContext("/api/accounts", new CreateAccountHandler()); // New API for Expansion
        server.createContext("/api/loans", new CreateLoanHandler()); // New API for Loans

        server.setExecutor(null); // default executor
        server.start();
        System.out.println(">> Dashboard started at http://localhost:8080/");
    }

    // NEW: Handle Loans
    private class CreateLoanHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                StringBuilder sb = new StringBuilder();
                java.io.InputStream ios = exchange.getRequestBody();
                int i;
                while ((i = ios.read()) != -1) {
                    sb.append((char) i);
                }
                String body = sb.toString();
                try {
                    String accNum = extractJsonValue(body, "account");
                    String amountStr = extractJsonValue(body, "amount");

                    if (accNum == null || amountStr == null) {
                        throw new IllegalArgumentException("Missing fields");
                    }

                    java.math.BigDecimal amount = new java.math.BigDecimal(amountStr);
                    com.bank.simulator.model.Account acc = bankService.getAccountRepository()
                            .findByAccountNumber(accNum)
                            .orElseThrow(() -> new IllegalArgumentException("Account not found"));

                    // Loan Logic:
                    // 1. Add Cash to Balance
                    acc.credit(amount);
                    // 2. Add Debt
                    acc.addDebt(amount);

                    sendJson(exchange, "{\"status\":\"OK\", \"message\":\"Loan approved\"}");
                } catch (Exception e) {
                    e.printStackTrace();
                    String err = "{\"status\":\"ERROR\", " + "\"message\":\"" + e.getMessage() + "\"}";
                    byte[] bytes = err.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.getResponseBody().close();
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    private class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] fileBytes = Files.readAllBytes(Paths.get("src/main/resources/index.html"));
            // Ensure we send the correct encoding header
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, fileBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(fileBytes);
            os.close();
        }
    }

    // NEW: Handle Account Creation
    private class CreateAccountHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                StringBuilder sb = new StringBuilder();
                java.io.InputStream ios = exchange.getRequestBody();
                int i;
                while ((i = ios.read()) != -1) {
                    sb.append((char) i);
                }
                String body = sb.toString();
                try {
                    String accNum = extractJsonValue(body, "account");
                    String holder = extractJsonValue(body, "holder");
                    String initBal = extractJsonValue(body, "balance");

                    if (accNum == null || holder == null || initBal == null) {
                        throw new IllegalArgumentException("Missing fields");
                    }

                    // Logic to create account
                    com.bank.simulator.model.Account newAcc = new com.bank.simulator.model.Account(accNum, holder,
                            new java.math.BigDecimal(initBal));
                    accountRepository.save(newAcc);

                    sendJson(exchange, "{\"status\":\"OK\", \"message\":\"Account created\"}");
                } catch (Exception e) {
                    e.printStackTrace();
                    String err = "{\"status\":\"ERROR\", " + "\"message\":\"" + e.getMessage() + "\"}";
                    byte[] bytes = err.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.getResponseBody().close();
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder json = new StringBuilder("[");
            // Sorting for stable UI
            java.util.List<com.bank.simulator.model.Account> accounts = new java.util.ArrayList<>(
                    bankService.getAccountRepository().findAll());
            accounts.sort(java.util.Comparator.comparing(com.bank.simulator.model.Account::getAccountNumber));

            for (int i = 0; i < accounts.size(); i++) {
                com.bank.simulator.model.Account acc = accounts.get(i);
                json.append(String.format("{\"account\":\"%s\", \"holder\":\"%s\", \"balance\":%s, \"debt\":%s}",
                        acc.getAccountNumber(), acc.getHolderName(), acc.getBalance(), acc.getDebt()));
                if (i < accounts.size() - 1)
                    json.append(",");
            }
            json.append("]");

            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
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

    // NEW: Handle Manual Transfers
    private class TransferHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                StringBuilder sb = new StringBuilder();
                java.io.InputStream ios = exchange.getRequestBody();
                int i;
                while ((i = ios.read()) != -1) {
                    sb.append((char) i);
                }
                String body = sb.toString();
                // Expecting simple JSON: {"from":"A", "to":"B", "amount":10}
                // Very crude manual parsing for demo (no gson/jackson usage allowed to keep it
                // zero-dep)
                try {
                    String from = extractJsonValue(body, "from");
                    String to = extractJsonValue(body, "to");
                    String amountStr = extractJsonValue(body, "amount");

                    Account src = accountRepository.findByAccountNumber(from)
                            .orElseThrow(() -> new RuntimeException("Source not found"));
                    Account dest = accountRepository.findByAccountNumber(to)
                            .orElseThrow(() -> new RuntimeException("Dest not found"));
                    java.math.BigDecimal amt = new java.math.BigDecimal(amountStr);

                    bankService.transfer(src, dest, amt);
                    sendJson(exchange, "{\"status\":\"OK\"}");
                } catch (Exception e) {
                    e.printStackTrace();
                    String err = "{\"status\":\"ERROR\", " + "\"message\":\"" + e.getMessage() + "\"}";
                    byte[] bytes = err.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.getResponseBody().close();
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }

    private String extractJsonValue(String json, String key) {
        // Crude parser: find "key":"value" or "key":value
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx == -1)
            return null;
        int colonIdx = json.indexOf(":", keyIdx);
        int commaIdx = json.indexOf(",", colonIdx);
        int closeBraceIdx = json.indexOf("}", colonIdx);
        int endIdx = (commaIdx == -1) ? closeBraceIdx
                : (closeBraceIdx == -1 ? commaIdx : Math.min(commaIdx, closeBraceIdx));

        String raw = json.substring(colonIdx + 1, endIdx).trim();
        return raw.replace("\"", "");
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
