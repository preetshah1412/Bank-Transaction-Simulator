package com.bank.simulator.service;

import com.bank.simulator.model.Account;
import com.bank.simulator.repository.AccountRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InterestService {

    private final AccountRepository accountRepository;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final BigDecimal INTEREST_RATE = new BigDecimal("0.05"); // 5% per cycle

    public InterestService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public void start() {
        System.out.println(">> Interest Service Started: Accruing 5% every 5 seconds.");
        scheduler.scheduleAtFixedRate(this::applyInterest, 5, 5, TimeUnit.SECONDS);
    }

    private void applyInterest() {
        for (Account acc : accountRepository.findAll()) {
            // We need write lock to change balance and debt
            acc.getRwLock().writeLock().lock();
            try {
                // 1. Positive Interest (Savings) - 5%
                BigDecimal currentBalance = acc.getBalance();
                if (currentBalance.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal interest = currentBalance.multiply(new BigDecimal("0.05")).setScale(2,
                            RoundingMode.HALF_UP);
                    acc.credit(interest);
                    // System.out.println("Interest paid to " + acc.getAccountNumber() + ": " +
                    // interest);
                }

                // 2. Negative Interest (Debt) - 10%
                BigDecimal currentDebt = acc.getDebt();
                if (currentDebt.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal debtInterest = currentDebt.multiply(new BigDecimal("0.10")).setScale(2,
                            RoundingMode.HALF_UP);
                    acc.addDebt(debtInterest);
                    // System.out.println("Debt interest applied to " + acc.getAccountNumber() + ":
                    // " + debtInterest);
                }
            } finally {
                acc.getRwLock().writeLock().unlock();
            }
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
