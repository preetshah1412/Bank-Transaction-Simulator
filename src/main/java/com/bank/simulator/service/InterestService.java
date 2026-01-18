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
        for (Account account : accountRepository.findAll()) {
            // We need write lock to change balance
            account.getRwLock().writeLock().lock();
            try {
                BigDecimal currentInfo = account.getBalance();
                BigDecimal interest = currentInfo.multiply(INTEREST_RATE).setScale(2, RoundingMode.HALF_UP);
                if (interest.compareTo(BigDecimal.ZERO) > 0) {
                    account.credit(interest);
                    // System.out.println("Interest paid to " + account.getAccountNumber() + ": " +
                    // interest);
                }
            } finally {
                account.getRwLock().writeLock().unlock();
            }
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
