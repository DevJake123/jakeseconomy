package com.github.devjake123.jakeseconomy.client.network;

import com.github.devjake123.jakeseconomy.network.AuctionBidPayload;
import com.github.devjake123.jakeseconomy.network.AuctionBinPayload;
import com.github.devjake123.jakeseconomy.network.AuctionCancelPayload;
import com.github.devjake123.jakeseconomy.network.AuctionClaimPayload;
import com.github.devjake123.jakeseconomy.network.AuctionCreatePayload;
import com.github.devjake123.jakeseconomy.network.AuctionListRequestPayload;
import com.github.devjake123.jakeseconomy.network.MarketBuyPayload;
import com.github.devjake123.jakeseconomy.network.MarketSellPayload;
import com.github.devjake123.jakeseconomy.network.WithdrawPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class MarketPacketSender {

    public static void sendBuy(String itemId, long quantity) {
        ClientPlayNetworking.send(new MarketBuyPayload(itemId, quantity));
    }

    public static void sendSell(String itemId, long quantity) {
        ClientPlayNetworking.send(new MarketSellPayload(itemId, quantity));
    }

    public static void sendWithdraw(long copperCoins, long copperSacks,
                                    long silverCoins, long silverSacks,
                                    long goldCoins,   long goldSacks,
                                    long platinumCoins, long platinumSacks) {
        ClientPlayNetworking.send(new WithdrawPayload(
                copperCoins, copperSacks, silverCoins, silverSacks,
                goldCoins, goldSacks, platinumCoins, platinumSacks));
    }

    // ─── Auction ─────────────────────────────────────────────────────────────

    public static void sendAuctionCreate(int slot, long price, long durationMs, boolean isBin) {
        ClientPlayNetworking.send(new AuctionCreatePayload(slot, price, durationMs, isBin));
    }

    public static void sendAuctionBid(String auctionId, long amount) {
        ClientPlayNetworking.send(new AuctionBidPayload(auctionId, amount));
    }

    public static void sendAuctionCancel(String auctionId) {
        ClientPlayNetworking.send(new AuctionCancelPayload(auctionId));
    }

    public static void sendAuctionClaim() {
        ClientPlayNetworking.send(new AuctionClaimPayload());
    }

    public static void sendAuctionBin(String auctionId) {
        ClientPlayNetworking.send(new AuctionBinPayload(auctionId));
    }

    public static void requestAuctionList(int offset) {
        ClientPlayNetworking.send(new AuctionListRequestPayload(offset));
    }
}


