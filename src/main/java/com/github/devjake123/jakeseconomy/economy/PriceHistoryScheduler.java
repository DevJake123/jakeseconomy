package com.github.devjake123.jakeseconomy.economy;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Fires every 20 minutes (24,000 ticks at 20 TPS) and appends a fine-grained snapshot
 * of every configured market item to the recent price history in {@link EconomyState}.
 * Every 3rd fire (= every 60 minutes) also appends to the hourly archive history.
 *
 * Two tiers:
 *   Recent  — 20-min snapshots, max 72 entries per item (= 24 h). Used by the Day graph view.
 *   Archive — hourly snapshots, max 720 entries per item (= 30 days). Used by Week / Month views.
 */
public class PriceHistoryScheduler {

    // 20 min × 60 sec × 20 ticks/sec = 24,000 ticks
    private static final long RECENT_INTERVAL_TICKS = 20L * 60 * 20;
    // Write to archive once every 3 recent ticks (= every 60 minutes)
    private static final int  ARCHIVE_EVERY_N = 3;

    private static boolean registered    = false;
    private static long    tickCount     = 0;
    private static int     archiveCounter = 0;

    /**
     * Registers the server tick listener.
     * Called once from {@link com.github.devjake123.jakeseconomy.JakesEconomy#onInitialize()}.
     */
    public static void register() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(PriceHistoryScheduler::onTick);
    }

    private static void onTick(MinecraftServer server) {
        if (++tickCount >= RECENT_INTERVAL_TICKS) {
            tickCount = 0;
            try {
                MarketManager mgr = MarketManager.get();
                // Always record fine-grained recent snapshot (Day view)
                mgr.captureRecentPriceSnapshot(server);
                // Record hourly archive snapshot every 3rd recent tick (Week / Month views)
                if (++archiveCounter >= ARCHIVE_EVERY_N) {
                    archiveCounter = 0;
                    mgr.captureHourlyPriceSnapshot(server);
                }
            } catch (IllegalStateException e) {
                JakesEconomy.LOGGER.warn("PriceHistoryScheduler fired before MarketManager was ready.");
            }
        }
    }
}
