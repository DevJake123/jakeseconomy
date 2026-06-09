package com.github.devjake123.jakeseconomy.api;

import com.github.devjake123.jakeseconomy.economy.EconomyState;
import com.github.devjake123.jakeseconomy.economy.MarketManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Public API for Jake's Economy — intended for use by other mods.
 *
 * <p>All methods must be called from the <b>server tick thread</b>.
 * Obtain a {@link MinecraftServer} reference from your own lifecycle hooks
 * (e.g. {@code ServerLifecycleEvents.SERVER_STARTED}).
 *
 * <h2>Example</h2>
 * <pre>{@code
 * long balance = JakesEconomyApi.getBalance(player.getUUID(), server);
 * boolean success = JakesEconomyApi.withdraw(player.getUUID(), 100L, server);
 *
 * // Transfer 500 coins from one player to another
 * boolean ok = JakesEconomyApi.transferBalance(fromUUID, toUUID, 500L, server);
 * }</pre>
 *
 * <p>To listen for economy events, see {@link EconomyApiEvents}.
 *
 * <p><b>Balance sync:</b> All write methods automatically push the updated balance
 * to the affected player's client if they are currently online.
 */
public final class JakesEconomyApi {

    private JakesEconomyApi() {}

    // ─── Read ─────────────────────────────────────────────────────────────────

    /**
     * Returns the current virtual balance of the given player.
     * Returns {@code 0} if the player has no recorded balance.
     */
    public static long getBalance(UUID playerId, MinecraftServer server) {
        return EconomyState.get(server).getBalance(playerId);
    }

    /**
     * Returns {@code true} if the player currently has at least {@code amount} in their
     * virtual balance. Equivalent to {@code getBalance(id, server) >= amount}.
     */
    public static boolean hasEnoughBalance(UUID playerId, long amount, MinecraftServer server) {
        return EconomyState.get(server).getBalance(playerId) >= amount;
    }

    /**
     * Returns {@code true} if the given namespaced item ID (e.g. {@code "minecraft:diamond"})
     * is currently configured as a tradeable market item.
     */
    public static boolean isMarketItem(String itemId, MinecraftServer server) {
        return MarketManager.get().isMarketItem(itemId);
    }

    /**
     * Returns the current market price per unit for the given item ID, or {@code -1}
     * if the item is not listed in the market.
     */
    public static double getMarketPrice(String itemId, MinecraftServer server) {
        return MarketManager.get().getCurrentPrice(itemId);
    }

    // ─── Write ────────────────────────────────────────────────────────────────

    /**
     * Deposits {@code amount} into the player's virtual balance.
     * The amount must be positive; values ≤ 0 are silently ignored.
     *
     * <p>Fires {@link EconomyApiEvents#BALANCE_CHANGED} and syncs the client
     * display if the player is online.
     */
    public static void deposit(UUID playerId, long amount, MinecraftServer server) {
        if (amount <= 0) return;
        EconomyState.get(server).deposit(playerId, amount);
        // EconomyState.deposit already fires BALANCE_CHANGED internally
        syncBalance(playerId, server);
    }

    /**
     * Withdraws {@code amount} from the player's virtual balance.
     *
     * @return {@code true} if the player had sufficient funds and the withdrawal succeeded;
     *         {@code false} if the balance was insufficient (no change is made).
     *
     * <p>Fires {@link EconomyApiEvents#BALANCE_CHANGED} on success and syncs the
     * client display if the player is online.
     */
    public static boolean withdraw(UUID playerId, long amount, MinecraftServer server) {
        if (amount <= 0) return true;
        boolean success = EconomyState.get(server).withdraw(playerId, amount);
        // EconomyState.withdraw already fires BALANCE_CHANGED internally on success
        if (success) syncBalance(playerId, server);
        return success;
    }

    /**
     * Sets the player's virtual balance to exactly {@code amount}.
     * The amount must be non-negative; negative values are clamped to {@code 0}.
     *
     * <p>Fires {@link EconomyApiEvents#BALANCE_CHANGED} and syncs the client
     * display if the player is online.
     */
    public static void setBalance(UUID playerId, long amount, MinecraftServer server) {
        EconomyState.get(server).setBalance(playerId, Math.max(0, amount));
        // EconomyState.setBalance already fires BALANCE_CHANGED internally
        syncBalance(playerId, server);
    }

    /**
     * Atomically transfers {@code amount} from one player's balance to another.
     * Both players must be known to the server (they do not need to be online).
     *
     * @return {@code true} if the transfer succeeded (sender had sufficient funds);
     *         {@code false} if the sender could not afford it (no change is made to either balance).
     *
     * <p>Fires {@link EconomyApiEvents#BALANCE_CHANGED} for both players on success.
     */
    public static boolean transferBalance(UUID fromId, UUID toId, long amount, MinecraftServer server) {
        if (amount <= 0) return true;
        EconomyState economy = EconomyState.get(server);
        boolean success = economy.withdraw(fromId, amount);
        if (success) {
            economy.deposit(toId, amount);
            syncBalance(fromId, server);
            syncBalance(toId, server);
        }
        return success;
    }

    // ─── Client sync ──────────────────────────────────────────────────────────

    /**
     * Pushes the player's current virtual balance to their client if they are
     * currently online. No-op if the player is offline.
     *
     * <p>Call this after any balance mutation when you need the HUD to update
     * immediately (the write methods above do this automatically).
     */
    public static void syncBalance(UUID playerId, MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) EconomyState.syncBalance(player, server);
    }
}
