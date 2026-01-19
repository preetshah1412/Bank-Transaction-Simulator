package com.bank.simulator.service;

import com.bank.simulator.model.Account;
import java.math.BigDecimal;

public class FraudDetectionService {

    // Arbitrary threshold for "suspicious" large transfers
    private static final BigDecimal FRAUD_THRESHOLD = new BigDecimal("10000");

    public boolean isFraudulent(Account source, Account destination, BigDecimal amount) {
        // Standard check: amount too large?
        if (amount.compareTo(FRAUD_THRESHOLD) > 0) {
            System.err.println("FRAUD DETECTED: Transfer of " + amount + " exceeds threshold.");
            return true;
        }

        // Check 2: Blacklisted accounts (simple hardcoded example)
        if (source.getAccountNumber().startsWith("BLK") || destination.getAccountNumber().startsWith("BLK")) {
            System.err.println("FRAUD DETECTED: Blacklisted account involved.");
            return true;
        }

        return false;
    }
}
