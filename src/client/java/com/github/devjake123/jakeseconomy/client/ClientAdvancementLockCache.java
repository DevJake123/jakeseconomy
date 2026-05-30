package com.github.devjake123.jakeseconomy.client;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Caches which achievement lock IDs the local player has unlocked.
 * Populated by AdvancementLockSyncPayload sent from the server on join
 * and whenever a relevant advancement is granted.
 *
 * Lock ID 0 (default, no lock) is always considered unlocked.
 */
public class ClientAdvancementLockCache {

    private static Set<Integer> unlockedIds = Collections.emptySet();

    /** Called when the server sends an AdvancementLockSyncPayload. */
    public static void set(Set<Integer> ids) {
        unlockedIds = new HashSet<>(ids);
    }

    /**
     * Returns true if the given lock ID is NOT yet unlocked by the player.
     * Lock IDs <= 0 are always unlocked (no advancement required).
     */
    public static boolean isLocked(int lockId) {
        if (lockId <= 0) return false;
        return !unlockedIds.contains(lockId);
    }

    /** Clears the cache on disconnect so stale data does not leak between sessions. */
    public static void clear() {
        unlockedIds = Collections.emptySet();
    }
}


