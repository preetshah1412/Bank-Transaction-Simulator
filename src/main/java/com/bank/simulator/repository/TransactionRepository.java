package com.bank.simulator.repository;

import com.bank.simulator.model.Transaction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TransactionRepository {

    // CopyOnWriteArrayList is efficient for frequent reads and infrequent writes,
    // or when we need to iterate safely without locking the whole list.
    // However, for a high-volume system, a synchronized list or DB is better.
    // For this simulator, we use a synchronized wrapper or internal locking.

    private final List<Transaction> transactionLog = Collections.synchronizedList(new ArrayList<>());

    public void save(Transaction transaction) {
        transactionLog.add(transaction);
    }

    public List<Transaction> findAll() {
        synchronized (transactionLog) {
            return new ArrayList<>(transactionLog);
        }
    }
}
