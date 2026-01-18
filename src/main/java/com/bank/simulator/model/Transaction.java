package com.bank.simulator.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Transaction {

    private final String transactionId;
    private final String sourceAccount;
    private final String destinationAccount;
    private final BigDecimal amount;
    private final Instant timestamp;
    private TransactionStatus status;

    public Transaction(String sourceAccount, String destinationAccount, BigDecimal amount) {
        this.transactionId = UUID.randomUUID().toString();
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.timestamp = Instant.now();
        this.status = TransactionStatus.FAILED; // Default initial state
    }

    public synchronized void markSuccess() {
        this.status = TransactionStatus.SUCCESS;
    }

    public synchronized void markRolledBack() {
        this.status = TransactionStatus.ROLLED_BACK;
    }

    public synchronized void markFraud() {
        this.status = TransactionStatus.REJECTED_FRAUD;
    }
    
    // Getters
    public String getTransactionId() { return transactionId; }
    public String getSourceAccount() { return sourceAccount; }
    public String getDestinationAccount() { return destinationAccount; }
    public BigDecimal getAmount() { return amount; }
    public Instant getTimestamp() { return timestamp; }
    public synchronized TransactionStatus getStatus() { return status; }
    
    @Override
    public String toString() {
        return "Transaction{id='" + transactionId + "', src='" + sourceAccount + "', dest='" + destinationAccount + "', amt=" + amount + ", stat=" + status + "}";
    }
}
