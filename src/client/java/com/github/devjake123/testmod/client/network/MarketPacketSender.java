package com.github.devjake123.testmod.client.network;

import com.github.devjake123.testmod.network.MarketBuyPayload;
import com.github.devjake123.testmod.network.MarketSellPayload;
import com.github.devjake123.testmod.network.WithdrawPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client-side helper for sending market packets to the server.
 * Called by the GUI screens when the player clicks Buy or Sell.
 */
public class MarketPacketSender {

    /**
     * Sends a buy request to the server.
     * @param itemId   e.g. "minecraft:diamond"
     * @param quantity how many to buy
     */
    public static void sendBuy(String itemId, long quantity) {
        ClientPlayNetworking.send(new MarketBuyPayload(itemId, quantity));
    }

    /**
     * Sends a sell request to the server.
     * @param itemId   e.g. "minecraft:diamond"
     * @param quantity how many to sell
     */
    public static void sendSell(String itemId, long quantity) {
        ClientPlayNetworking.send(new MarketSellPayload(itemId, quantity));
    }


    public static void sendWithdraw(long copperCoins, long copperSacks,
                                    long silverCoins, long silverSacks,
                                    long goldCoins,   long goldSacks,
                                    long platinumCoins, long platinumSacks) {
        ClientPlayNetworking.send(new WithdrawPayload(
                copperCoins, copperSacks,
                silverCoins, silverSacks,
                goldCoins,   goldSacks,
                platinumCoins, platinumSacks));
    }

}