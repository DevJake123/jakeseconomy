package com.github.devjake123.testmod.client;

/**
 * Stores the player's last-known virtual balance, sent from the server via BalanceSyncPayload.
 * Used by GUI screens to display the balance without a server round-trip.
 */
public class ClientBalanceCache {
    private static long balance = 0;

    public static void set(long b) { balance = b; }
    public static long get()       { return balance; }
}

