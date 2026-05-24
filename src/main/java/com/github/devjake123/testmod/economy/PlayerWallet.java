package com.github.devjake123.testmod.economy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerWallet {
    // Stores virtual currency balance per player UUID
    private final Map<UUID, Long> balances = new HashMap<>();

    // Returns the balance for a player, defaulting to 0 if never seen before
    public long getBalance(UUID playerId) {
        return balances.getOrDefault(playerId, 0L);
    }

    // Adds the given amount to a player's balance, clamping at Long.MAX_VALUE
    // to prevent signed overflow into a negative balance.
    public void deposit(UUID playerId, long amount) {
        if (amount <= 0) return;
        balances.merge(playerId, amount, (current, delta) -> {
            long sum = current + delta;
            return sum < 0 ? Long.MAX_VALUE : sum; // clamp on overflow
        });
    }

    // Deducts the given amount from a player's balance.
    // Returns true if successful, false if the player has insufficient funds.
    public boolean withdraw(UUID playerId, long amount){
        long current = getBalance(playerId);
        if (current < amount) return false;
        balances.put(playerId, current - amount);
        return true;
    }

    // Directly sets a player's balance — used by admin commands only
    public void setBalance(UUID playerId, long amount) {
        balances.put(playerId, Math.max(0L, amount));
    }

    // Returns a copy of all balances — used for NBT serialization
    public Map<UUID, Long> getAll() {
        return new HashMap<>(balances);
    }


    // Replaces all balances — used when loading from NBT
    public void loadAll(Map<UUID, Long> data) {
        balances.clear();
        balances.putAll(data);
    }
}
