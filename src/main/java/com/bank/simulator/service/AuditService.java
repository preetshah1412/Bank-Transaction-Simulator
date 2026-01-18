package com.bank.simulator.service;

import com.bank.simulator.model.Transaction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuditService {

    private final ExecutorService auditExecutor = Executors.newSingleThreadExecutor();

    public void logTransaction(Transaction transaction) {
        auditExecutor.submit(() -> {
            // Simulate I/O latency
            try {
                // Thread.sleep(10);
                System.out.println("[AUDIT] Logged: " + transaction);
            } catch (Exception e) {
                System.err.println("Audit log failed: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        auditExecutor.shutdown();
    }
}
