package com.github.devjake123.jakeseconomy.integration;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import com.github.devjake123.jakeseconomy.api.JakesEconomyApi;
import com.github.devjake123.jakeseconomy.api.event.BalanceChangedEvent;
import com.github.devjake123.jakeseconomy.api.EconomyApiEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bidirectional integration with Cobbledollars mod.
 * Keeps Jake's Economy currency synchronized with Cobbledollars currency.
 */
public class CobbledollarsIntegration {

    // Prevents infinite loops when syncing between the two mods
    private static final ThreadLocal<Boolean> SYNCING = ThreadLocal.withInitial(() -> false);

    // Cache of last known balances to detect external changes from Cobbledollars
    private static final Map<UUID, BigInteger> lastKnownCobbledollarsBalances = new HashMap<>();

    // Reflection references to Cobbledollars methods (cached for performance)
    private static Method getCobbleDollarsMethod;
    private static Method setCobbleDollarsMethod;
    private static boolean reflectionInitialized = false;
    private static boolean reflectionFailed = false;

    /**
     * Sets up the integration. Call this from JakesEconomy.onInitialize()
     * only if Cobbledollars is loaded.
     */
    public static void initialize() {
        if (!initializeReflection()) {
            JakesEconomy.LOGGER.error("Failed to initialize Cobbledollars integration - reflection setup failed");
            return;
        }

        // Listen to Jake's Economy balance changes and mirror them to Cobbledollars
        EconomyApiEvents.BALANCE_CHANGED.register(CobbledollarsIntegration::onJakesEconomyBalanceChanged);

        // Check for external Cobbledollars balance changes every 10 ticks (0.5 seconds)
        ServerTickEvents.END_SERVER_TICK.register(new ServerTickEvents.EndTick() {
            private int tickCounter = 0;

            @Override
            public void onEndTick(MinecraftServer server) {
                if (++tickCounter >= 10) {
                    tickCounter = 0;
                    checkForExternalCobbledollarsChanges(server);
                }
            }
        });

        JakesEconomy.LOGGER.info("Cobbledollars integration enabled - currencies will sync bidirectionally");
    }

    /**
     * Uses reflection to access Cobbledollars' Kotlin extension functions.
     * PlayerExtensionKt.getCobbleDollars(Player) and setCobbleDollars(Player, BigInteger)
     */
    private static boolean initializeReflection() {
        if (reflectionInitialized) return true;
        if (reflectionFailed) return false;

        try {
            // Cobbledollars uses Kotlin extension functions in PlayerExtensionKt class
            Class<?> playerExtensionKt = Class.forName("fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt");

            // public static BigInteger getCobbleDollars(Player player)
            getCobbleDollarsMethod = playerExtensionKt.getMethod("getCobbleDollars", net.minecraft.world.entity.player.Player.class);

            // public static void setCobbleDollars(Player player, BigInteger amount)
            setCobbleDollarsMethod = playerExtensionKt.getMethod("setCobbleDollars",
                    net.minecraft.world.entity.player.Player.class, BigInteger.class);

            reflectionInitialized = true;
            JakesEconomy.LOGGER.info("Successfully initialized Cobbledollars reflection access");
            return true;

        } catch (Exception e) {
            JakesEconomy.LOGGER.error("Failed to initialize Cobbledollars reflection", e);
            reflectionFailed = true;
            return false;
        }
    }

    /**
     * Gets a player's Cobbledollars balance using reflection.
     */
    private static BigInteger getCobbledollarsBalance(ServerPlayer player) {
        try {
            return (BigInteger) getCobbleDollarsMethod.invoke(null, player);
        } catch (Exception e) {
            JakesEconomy.LOGGER.warn("Failed to get Cobbledollars balance for {}: {}",
                    player.getName().getString(), e.getMessage());
            return BigInteger.ZERO;
        }
    }

    /**
     * Sets a player's Cobbledollars balance using reflection.
     */
    private static void setCobbledollarsBalance(ServerPlayer player, BigInteger amount) {
        try {
            setCobbleDollarsMethod.invoke(null, player, amount);
        } catch (Exception e) {
            JakesEconomy.LOGGER.warn("Failed to set Cobbledollars balance for {}: {}",
                    player.getName().getString(), e.getMessage());
        }
    }

    /**
     * Converts Jake's Economy long balance to Cobbledollars BigInteger.
     */
    private static BigInteger longToBigInteger(long value) {
        return BigInteger.valueOf(value);
    }

    /**
     * Converts Cobbledollars BigInteger to Jake's Economy long.
     * Clamps at Long.MAX_VALUE if Cobbledollars balance is higher.
     */
    private static long bigIntegerToLong(BigInteger value) {
        if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            JakesEconomy.LOGGER.warn("Cobbledollars balance {} exceeds Long.MAX_VALUE, clamping", value);
            return Long.MAX_VALUE;
        }
        return value.longValue();
    }

    /**
     * Called when Jake's Economy balance changes - mirrors the change to Cobbledollars.
     */
    private static void onJakesEconomyBalanceChanged(BalanceChangedEvent event) {
        if (SYNCING.get()) return; // Already syncing, prevent infinite loop

        SYNCING.set(true);
        try {
            MinecraftServer server = getServerInstance();
            if (server == null) return;

            ServerPlayer player = server.getPlayerList().getPlayer(event.playerId());
            if (player == null) return; // Player offline, can't sync to Cobbledollars

            // Update Cobbledollars to match Jake's Economy
            BigInteger newCobbledollarsBalance = longToBigInteger(event.newBalance());
            setCobbledollarsBalance(player, newCobbledollarsBalance);

            // Update our cache
            lastKnownCobbledollarsBalances.put(event.playerId(), newCobbledollarsBalance);

        } finally {
            SYNCING.set(false);
        }
    }

    /**
     * Checks all online players for external Cobbledollars balance changes
     * (e.g., from Cobbledollars commands, Pokemon battle rewards, etc.)
     */
    private static void checkForExternalCobbledollarsChanges(MinecraftServer server) {
        if (SYNCING.get()) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();

            BigInteger currentCobbledollars = getCobbledollarsBalance(player);
            BigInteger lastKnown = lastKnownCobbledollarsBalances.get(playerId);

            // First time seeing this player, or balance changed externally
            if (lastKnown == null || !lastKnown.equals(currentCobbledollars)) {
                lastKnownCobbledollarsBalances.put(playerId, currentCobbledollars);

                // Only sync if this wasn't already synced by us
                if (lastKnown != null) {
                    syncCobbledollarsToJakes(player, currentCobbledollars, server);
                }
            }
        }
    }

    /**
     * Syncs an external Cobbledollars balance change to Jake's Economy.
     */
    private static void syncCobbledollarsToJakes(ServerPlayer player, BigInteger cobbledollarsBalance, MinecraftServer server) {
        SYNCING.set(true);
        try {
            long jakesBalance = bigIntegerToLong(cobbledollarsBalance);
            JakesEconomyApi.setBalance(player.getUUID(), jakesBalance, server);

        } finally {
            SYNCING.set(false);
        }
    }

    /**
     * Helper to get MinecraftServer instance (cached on first access).
     */
    private static MinecraftServer serverInstance;
    private static MinecraftServer getServerInstance() {
        // Server instance is set when ServerLifecycleEvents.SERVER_STARTED fires
        // We'll grab it from the first player we see
        if (serverInstance != null) return serverInstance;
        return null; // Will be set when we first encounter a player
    }

    /**
     * Called when a player joins - initializes their balance sync.
     */
    public static void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
        serverInstance = server;

        // Initialize cache with current Cobbledollars balance
        BigInteger cobbledollarsBalance = getCobbledollarsBalance(player);
        lastKnownCobbledollarsBalances.put(player.getUUID(), cobbledollarsBalance);

        // Sync to Jake's Economy if player has Cobbledollars balance but no Jake's balance
        long jakesBalance = JakesEconomyApi.getBalance(player.getUUID(), server);
        if (jakesBalance == 0 && cobbledollarsBalance.compareTo(BigInteger.ZERO) > 0) {
            JakesEconomy.LOGGER.info("Importing {} Cobbledollars balance to Jake's Economy for {}",
                    cobbledollarsBalance, player.getName().getString());
            SYNCING.set(true);
            try {
                JakesEconomyApi.setBalance(player.getUUID(), bigIntegerToLong(cobbledollarsBalance), server);
            } finally {
                SYNCING.set(false);
            }
        }
    }

    /**
     * Called when a player leaves - cleanup cache.
     */
    public static void onPlayerLeave(ServerPlayer player) {
        lastKnownCobbledollarsBalances.remove(player.getUUID());
    }
}

