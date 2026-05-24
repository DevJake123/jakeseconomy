package com.github.devjake123.testmod.client;

import com.github.devjake123.testmod.economy.TransactionEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache for the player's transaction history.
 * Updated whenever a TransactionHistoryPayload arrives from the server.
 */
public class ClientTransactionHistoryCache {

    private static final List<TransactionEntry> HISTORY = new ArrayList<>();

    public static void update(List<TransactionEntry> entries) {
        HISTORY.clear();
        HISTORY.addAll(entries);
    }

    /** Returns a snapshot of the current history (newest first). */
    public static List<TransactionEntry> get() {
        return List.copyOf(HISTORY);
    }
}

