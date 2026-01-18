package com.bank.simulator.model;

import com.bank.simulator.exception.InsufficientFundsException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Account {

    private final String accountNumber;
    private final String holderName;
    private BigDecimal balance;

    // Advanced: ReadWriteLock allows multiple readers (balance checks) but only one
    // writer (transfers)
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true); // Fair lock
    private final List<Transaction> transactionHistory = new ArrayList<>();

    public Account(String accountNumber, String holderName, BigDecimal initialBalance) {
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.balance = initialBalance;
    }

    public void debit(BigDecimal amount) {
        rwLock.writeLock().lock();
        try {
            if (balance.compareTo(amount) < 0) {
                throw new InsufficientFundsException("Insufficient balance in account " + accountNumber);
            }
            balance = balance.subtract(amount);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void credit(BigDecimal amount) {
        rwLock.writeLock().lock();
        try {
            balance = balance.add(amount);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public BigDecimal getBalance() {
        rwLock.readLock().lock();
        try {
            return balance;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Exposes the lock for external coordination (BankService).
     * CAUTION: Manually managing locks requires strict discipline to avoid
     * deadlocks.
     */
    public ReentrantReadWriteLock getRwLock() {
        return rwLock;
    }

    public void addTransaction(Transaction tx) {
        // History is often appending, but can be read frequently.
        // We protect the list with a write lock for modification.
        rwLock.writeLock().lock();
        try {
            transactionHistory.add(tx);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public List<Transaction> getTransactionHistory() {
        rwLock.readLock().lock();
        try {
            return new ArrayList<>(transactionHistory); // Return copy to prevent external mutation issues
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getHolderName() {
        return holderName;
    }
}
