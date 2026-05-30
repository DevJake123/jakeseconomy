package com.github.devjake123.jakeseconomy.economy.auction;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/** Checks for expired auctions once per second (every 20 server ticks). */
public class AuctionExpireScheduler {

    private static int tickCount = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AuctionExpireScheduler::onTick);
    }

    private static void onTick(MinecraftServer server) {
        if (++tickCount < 20) return;
        tickCount = 0;
        try {
            AuctionManager.get().tick(server);
        } catch (IllegalStateException e) {
            JakesEconomy.LOGGER.warn("[Auction] AuctionExpireScheduler fired before AuctionManager was ready.");
        }
    }
}


