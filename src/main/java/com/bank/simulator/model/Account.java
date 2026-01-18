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
    private BigDecimal debt;

    // Advanced: ReadWriteLock allows multiple readers (balance checks) but only one
    // writer (transfers)
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true); // Fair lock
    private final List<Transaction> transactionHistory = new ArrayList<>();

    public Account(String accountNumber, String holderName, BigDecimal initialBalance) {
        this(accountNumber, holderName, initialBalance, BigDecimal.ZERO);
    }

    public Account(String accountNumber, String holderName, BigDecimal initialBalance, BigDecimal initialDebt) {
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.balance = initialBalance;
        this.debt = initialDebt;
    }

    public BigDecimal getDebt() {
        rwLock.readLock().lock();
        try {
            return debt;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void addDebt(BigDecimal amount) {
        rwLock.writeLock().lock();
        try {
            debt = debt.add(amount);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private static final BigDecimal MIN_BALANCE = new BigDecimal("20");

    public void debit(BigDecimal amount) {
        rwLock.writeLock().lock();
        try {
            // Rule: Check if (Balance - Amount) < 20
            if (balance.subtract(amount).compareTo(MIN_BALANCE) < 0) {
                String msg = String.format(
                        "Transaction Declined. Insufficient Funds. Your balance is %s, but you need to maintain a minimum of %s.",
                        balance, MIN_BALANCE);
                throw new InsufficientFundsException(msg);
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
