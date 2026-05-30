package com.github.devjake123.jakeseconomy.economy;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import com.github.devjake123.jakeseconomy.config.JakesEconomyConfigManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Schedules periodic price decay by counting server ticks.
 *
 * Every server tick (20 per second), this increments a counter. When the counter
 * reaches the configured decay interval (in hours, converted to ticks), it triggers
 * MarketManager.applyDecay() and resets the counter.
 *
 * Only applies decay if priceDecayEnabled = true in server config.
 * The interval is read from config each cycle so it can be changed at runtime.
 *
 * 1 hour = 3600 seconds = 72,000 ticks (at 20 TPS)
 */
public class PriceDecayScheduler {

    // Current tick count since last decay
    private static long tickCount = 0;

    /**
     * Registers the server tick event listener.
     * Called once during mod initialization in JakesEconomy.onInitialize().
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PriceDecayScheduler::onTick);
    }

    private static void onTick(MinecraftServer server) {
        var config = JakesEconomyConfigManager.getServer();

        // Skip decay entirely if disabled in config
        if (config == null || !config.priceDecayEnabled) return;

        tickCount++;

        // Convert configured hours to ticks (20 TPS × 3600 seconds/hour)
        long ticksPerInterval = (long)(config.priceDecayIntervalHours * 20 * 3600);

        if (tickCount >= ticksPerInterval) {
            tickCount = 0;
            try {
                MarketManager.get().applyDecay(server);
            } catch (IllegalStateException e) {
                // MarketManager not yet initialized (server still starting up) — skip this tick
                JakesEconomy.LOGGER.warn("PriceDecayScheduler fired before MarketManager was ready.");
            }
        }
    }
}
