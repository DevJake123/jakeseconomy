package com.github.devjake123.jakeseconomy.economy;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Periodically captures a snapshot of every item's netDeficit so the trend arrow
 * in the market GUI reflects recent price movement rather than the absolute
 * distance from base price.
 *
 * Every SNAPSHOT_INTERVAL_TICKS (3 minutes = 3600 ticks at 20 TPS), each
 * MarketListing's snapshotDeficit is updated to the current netDeficit.
 * The trend arrow then shows whether the price has risen, fallen, or stayed the
 * same *since the last snapshot*.
 */
public class TrendSnapshotScheduler {

    // 3 minutes × 60 seconds/minute × 20 ticks/second = 3600 ticks
    private static final long SNAPSHOT_INTERVAL_TICKS = 3 * 60 * 20;

    private static boolean registered = false;
    private static long tickCount = 0;

    /**
     * Registers the server tick event listener.
     * Called once during mod initialization in JakesEconomy.onInitialize().
     */
    public static void register() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(TrendSnapshotScheduler::onTick);
    }

    private static void onTick(MinecraftServer server) {
        tickCount++;

        if (tickCount >= SNAPSHOT_INTERVAL_TICKS) {
            tickCount = 0;
            try {
                MarketManager.get().updateTrendSnapshots(server);
            } catch (IllegalStateException e) {
                // MarketManager not yet initialized (server still starting up) — skip this tick
                JakesEconomy.LOGGER.warn("TrendSnapshotScheduler fired before MarketManager was ready.");
            }
        }
    }
}


