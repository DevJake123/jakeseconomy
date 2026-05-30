package com.github.devjake123.jakeseconomy.economy;

/**
 * A single market transaction record stored in a player's history.
 *
 * @param type      "BUY", "SELL", or "WITHDRAW"
 * @param itemId    Registry ID (e.g. "minecraft:diamond"), or "coins" for withdrawals
 * @param quantity  Number of items bought/sold, or total coin count for withdrawals
 * @param amount    Total currency spent (BUY/WITHDRAW) or earned (SELL)
 * @param timestamp System.currentTimeMillis() when the transaction occurred
 */
public record TransactionEntry(
        String type,
        String itemId,
        long quantity,
        long amount,
        long timestamp
) {}


