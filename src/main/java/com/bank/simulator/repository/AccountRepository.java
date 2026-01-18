package com.bank.simulator.repository;

import com.bank.simulator.model.Account;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AccountRepository {

    private final Map<String, Account> accountStore = new ConcurrentHashMap<>();

    public void save(Account account) {
        accountStore.put(account.getAccountNumber(), account);
    }

    public Optional<Account> findByAccountNumber(String accountNumber) {
        return Optional.ofNullable(accountStore.get(accountNumber));
    }

    public Collection<Account> findAll() {
        return accountStore.values();
    }
}
