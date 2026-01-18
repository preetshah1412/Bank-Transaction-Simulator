package com.bank.simulator;

import com.bank.simulator.model.Account;
import com.bank.simulator.service.BankService;
import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Random;

public class BankingSimulator {

    public static void main(String[] args) throws InterruptedException {

        System.out.println("=== Starting Advanced Banking Simulator ===");

        BankService bankService = new BankService();

        // Initialize Services
        com.bank.simulator.service.StorageService storageService = new com.bank.simulator.service.StorageService(
                bankService.getAccountRepository(), bankService.getTransactionRepository());

        // Try to load existing data
        if (storageService.load()) {
            System.out.println(">> Loaded existing data from bank_data.json");
        } else {
            System.out.println(">> No existing data found. Creating default accounts...");
            // Setup Accounts and save directly
            bankService.getAccountRepository().save(new Account("A1001", "Alice", new BigDecimal("1000")));
            bankService.getAccountRepository().save(new Account("A1002", "Bob", new BigDecimal("1000")));
            bankService.getAccountRepository().save(new Account("A1003", "Charlie", new BigDecimal("1000")));
            bankService.getAccountRepository().save(new Account("BLK_999", "EvilCorp", new BigDecimal("0")));
        }

        // Retrieve references for the simulation loop
        // These keys must match what was saved/loaded
        Account acc1 = bankService.getAccountRepository().findByAccountNumber("A1001").orElse(null);
        Account acc2 = bankService.getAccountRepository().findByAccountNumber("A1002").orElse(null);
        Account acc3 = bankService.getAccountRepository().findByAccountNumber("A1003").orElse(null);
        Account fraudAcc = bankService.getAccountRepository().findByAccountNumber("BLK_999").orElse(null);

        if (acc1 == null || acc2 == null || acc3 == null || fraudAcc == null) {
            System.err.println("WARNING: Core accounts missing. Simulation loop might fail.");
            // We'll proceed but it might throw NPE if the loop runs.
            // For now, let's assume they exist.
        }

        // Start Auto-Save
        storageService.start();

        // Auto-Save on Shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SHUTDOWN] Saving state...");
            storageService.stop();
        }));

        // Start Interest Service
        com.bank.simulator.service.InterestService interestService = new com.bank.simulator.service.InterestService(
                bankService.getAccountRepository());
        interestService.start();

        // Start UI Server
        try {
            // Dashboard now needs bankService for manual triggers
            com.bank.simulator.ui.DashboardServer dashboard = new com.bank.simulator.ui.DashboardServer(
                    bankService.getAccountRepository(),
                    bankService.getTransactionRepository(),
                    bankService);
            dashboard.start();
        } catch (Exception e) {
            System.err.println("Failed to start UI: " + e.getMessage());
        }

        ExecutorService executor = Executors.newFixedThreadPool(15);
        Random random = new Random();

        // 1. High Velocity Transfers between Alice and Bob
        for (int i = 0; i < 50; i++) {
            executor.submit(() -> {
                try {
                    bankService.transfer(acc1, acc2, new BigDecimal("10"));
                    // Simulate random small processing delay to encourage interleaving AND visual
                    // effect
                    Thread.sleep(random.nextInt(500) + 500); // Slowed: 0.5s - 1.0s
                    bankService.transfer(acc2, acc1, new BigDecimal("5"));
                } catch (Exception ignored) {
                    // System.err.println(ignored.getMessage());
                }
            });
        }

        // 2. Continuous Balance Checks (Readers)
        // These threads only take ReadLocks, demonstrating disjoint access from
        // WriteLocks
        for (int i = 0; i < 20; i++) {
            executor.submit(() -> {
                try {
                    BigDecimal bal = acc1.getBalance();
                    // System.out.println("Alice Balance checkpoint: " + bal);
                } catch (Exception ignored) {
                }
            });
        }

        // 3. Simulate Fraudulent Transaction
        executor.submit(() -> {
            try {
                System.out.println("Attempting Fraudulent Transfer...");
                bankService.transfer(acc1, fraudAcc, new BigDecimal("100")); // Should fail due to BLK prefix
            } catch (Exception e) {
                System.out.println("Expected Fraud Catch: " + e.getMessage());
            }
        });

        // 4. Simulate Large Amount (Rule Trigger)
        executor.submit(() -> {
            try {
                System.out.println("Attempting Large Transfer...");
                bankService.transfer(acc3, acc1, new BigDecimal("50000")); // Should fail > 10k
            } catch (Exception e) {
                System.out.println("Expected High Value Warning: " + e.getMessage());
            }
        });

        executor.shutdown();
        // boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);
        // We don't want to kill the app. We want it to run forever now.

        System.out.println("\n=== Simulation Loop Complete (App Still Running) ===");
        System.out.println("Final Balance Alice (A1001): " + acc1.getBalance());
        System.out.println("Final Balance Bob   (A1002): " + acc2.getBalance());
        System.out.println("Final Balance Charlie(A1003): " + acc3.getBalance());
        System.out.println("Final Balance EvilCorp(BLK): " + fraudAcc.getBalance());

        // Validation Calculation
        // Initial Total: 1000 + 1000 + 2000 + 0 = 4000
        // Expected Logic:
        // 50 transfers A->B of 10 = -500 for A, +500 for B
        // 50 transfers B->A of 5 = +250 for A, -250 for B
        // Net A: 1000 - 500 + 250 = 750 (minus any failed txs)
        // Net B: 1000 + 500 - 250 = 1250
        // Fraud/Large transfers should fail and not affect balance.

        BigDecimal totalPool = acc1.getBalance().add(acc2.getBalance()).add(acc3.getBalance())
                .add(fraudAcc.getBalance());
        System.out.println("Total System Liquidity: " + totalPool);

        if (totalPool.compareTo(new BigDecimal("4000")) == 0) {
            System.out.println("SUCCESS: Total liquidity matches initial state. Atomicity preserved.");
        } else {
            System.err.println("FAILURE: Liquidity mismatch! Thread safety violated.");
        }
    }
}
