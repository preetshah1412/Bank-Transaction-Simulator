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

        // Setup Accounts
        Account acc1 = new Account("A1001", "Alice", new BigDecimal("1000"));
        Account acc2 = new Account("A1002", "Bob", new BigDecimal("1000"));
        Account acc3 = new Account("A1003", "Charlie", new BigDecimal("2000"));
        Account fraudAcc = new Account("BLK_999", "EvilCorp", new BigDecimal("0")); // Blacklisted

        BankService bankService = new BankService();

        // Register accounts to repo for UI visibility
        bankService.getAccountRepository().save(acc1);
        bankService.getAccountRepository().save(acc2);
        bankService.getAccountRepository().save(acc3);
        bankService.getAccountRepository().save(fraudAcc);

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
