package com.bank.simulator.service;

import com.bank.simulator.exception.InsufficientFundsException;
import com.bank.simulator.model.Account;
import com.bank.simulator.model.Transaction;
import com.bank.simulator.repository.AccountRepository;
import com.bank.simulator.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public class BankService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final FraudDetectionService fraudDetectionService;
    private final AuditService auditService;

    // Default constructor for simplicity in main
    public BankService() {
        this.transactionRepository = new TransactionRepository();
        this.accountRepository = new AccountRepository();
        this.fraudDetectionService = new FraudDetectionService();
        this.auditService = new AuditService();
    }

    // Dependency injection constructor
    public BankService(TransactionRepository repo, AccountRepository accRepo, FraudDetectionService fraud,
            AuditService audit) {
        this.transactionRepository = repo;
        this.accountRepository = accRepo;
        this.fraudDetectionService = fraud;
        this.auditService = audit;
    }

    public void transfer(Account from, Account to, BigDecimal amount) throws InterruptedException {
        // 1. Validation
        if (from.getAccountNumber().equals(to.getAccountNumber())) {
            throw new IllegalArgumentException("Cannot transfer to same account");
        }

        // 2. Fraud Check (Pre-lock)
        if (fraudDetectionService.isFraudulent(from, to, amount)) {
            Transaction failedTx = new Transaction(from.getAccountNumber(), to.getAccountNumber(), amount);
            failedTx.markFraud();
            transactionRepository.save(failedTx);
            auditService.logTransaction(failedTx);
            throw new SecurityException("Transaction rejected by fraud detection");
        }

        Transaction transaction = new Transaction(
                from.getAccountNumber(),
                to.getAccountNumber(),
                amount);

        // 3. Lock Ordering to avoid Deadlock (Classic strategy)
        // Even with ReadWriteLock, we need the WRITE lock for both accounts to transfer
        Account firstLock = from.getAccountNumber().compareTo(to.getAccountNumber()) < 0 ? from : to;
        Account secondLock = firstLock == from ? to : from;

        Lock lock1 = firstLock.getRwLock().writeLock();
        Lock lock2 = secondLock.getRwLock().writeLock();

        // Advanced: Try-Lock with timeout to fail gracefully instead of waiting forever
        boolean locked1 = false;
        boolean locked2 = false;

        try {
            locked1 = lock1.tryLock(1, TimeUnit.SECONDS);
            locked2 = lock2.tryLock(1, TimeUnit.SECONDS);

            if (locked1 && locked2) {
                // Critical Section
                try {
                    from.debit(amount);
                    to.credit(amount);
                    transaction.markSuccess();
                } catch (Exception ex) {
                    transaction.markRolledBack();
                    throw ex; // Rethrow to notify caller
                }
            } else {
                transaction.markRolledBack();
                throw new RuntimeException("Could not acquire locks - system busy or potential deadlock avoided");
            }
        } finally {
            if (locked2)
                lock2.unlock();
            if (locked1)
                lock1.unlock();

            // 4. Post-Processing
            transactionRepository.save(transaction);
            from.addTransaction(transaction);
            to.addTransaction(transaction);
            auditService.logTransaction(transaction);
        }
    }

    public void shutdown() {
        auditService.shutdown();
    }

    public TransactionRepository getTransactionRepository() {
        return transactionRepository;
    }

    public AccountRepository getAccountRepository() {
        return accountRepository;
    }

}
