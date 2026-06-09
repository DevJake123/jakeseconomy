package com.github.devjake123.jakeseconomy.api;

import com.github.devjake123.jakeseconomy.api.event.AuctionBidPlacedEvent;
import com.github.devjake123.jakeseconomy.api.event.AuctionCancelledEvent;
import com.github.devjake123.jakeseconomy.api.event.AuctionFinalizedEvent;
import com.github.devjake123.jakeseconomy.api.event.BalanceChangedEvent;
import com.github.devjake123.jakeseconomy.api.event.MarketBuyEvent;
import com.github.devjake123.jakeseconomy.api.event.MarketSellEvent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Fabric events fired by Jake's Economy for downstream mods to consume.
 *
 * <p>All events are fired on the <b>server tick thread</b>. Do not perform heavy
 * blocking work inside a listener.
 *
 * <h2>Usage example</h2>
 * <pre>{@code
 * // In your mod's onInitialize():
 * EconomyApiEvents.BALANCE_CHANGED.register(e -> {
 *     LOGGER.info("{} balance: {} → {}", e.playerId(), e.oldBalance(), e.newBalance());
 * });
 *
 * EconomyApiEvents.AUCTION_FINALIZED.register(e -> {
 *     if (e.winnerId() != null) {
 *         LOGGER.info("Auction {} sold {} to {} for {}",
 *                 e.auctionId(), e.itemId(), e.winnerId(), e.finalPrice());
 *     }
 * });
 *
 * EconomyApiEvents.AUCTION_BID_PLACED.register(e ->
 *     LOGGER.info("{} bid {} on auction {}", e.bidderId(), e.bidAmount(), e.auctionId()));
 *
 * EconomyApiEvents.AUCTION_CANCELLED.register(e ->
 *     LOGGER.info("Auction {} for {} was cancelled by {}", e.auctionId(), e.itemId(), e.sellerId()));
 * }</pre>
 */
public final class EconomyApiEvents {

    private EconomyApiEvents() {}

    // ─── Balance ─────────────────────────────────────────────────────────────

    @FunctionalInterface
    public interface BalanceChangedListener {
        void onBalanceChanged(BalanceChangedEvent event);
    }

    /**
     * Fired after any virtual-balance mutation (deposit, withdraw, or set).
     * Carries the player UUID, old balance, and new balance.
     */
    public static final Event<BalanceChangedListener> BALANCE_CHANGED =
            EventFactory.createArrayBacked(BalanceChangedListener.class,
                    listeners -> event -> {
                        for (BalanceChangedListener l : listeners) l.onBalanceChanged(event);
                    });

    // ─── Market ───────────────────────────────────────────────────────────────

    @FunctionalInterface
    public interface MarketBuyListener {
        void onMarketBuy(MarketBuyEvent event);
    }

    /**
     * Fired after a player successfully buys items from the market.
     * Carries the buyer UUID, item ID, quantity, and total cost.
     */
    public static final Event<MarketBuyListener> MARKET_BUY =
            EventFactory.createArrayBacked(MarketBuyListener.class,
                    listeners -> event -> {
                        for (MarketBuyListener l : listeners) l.onMarketBuy(event);
                    });

    @FunctionalInterface
    public interface MarketSellListener {
        void onMarketSell(MarketSellEvent event);
    }

    /**
     * Fired after a player successfully sells items to the market.
     * Carries the seller UUID, item ID, quantity, and total payout.
     */
    public static final Event<MarketSellListener> MARKET_SELL =
            EventFactory.createArrayBacked(MarketSellListener.class,
                    listeners -> event -> {
                        for (MarketSellListener l : listeners) l.onMarketSell(event);
                    });

    // ─── Auction ──────────────────────────────────────────────────────────────

    @FunctionalInterface
    public interface AuctionFinalizedListener {
        void onAuctionFinalized(AuctionFinalizedEvent event);
    }

    /**
     * Fired when an auction is finalized (natural expiry, BIN purchase, or server-start
     * catch-up for auctions that expired during downtime).
     * {@link AuctionFinalizedEvent#winnerId()} is {@code null} when there were no bids.
     */
    public static final Event<AuctionFinalizedListener> AUCTION_FINALIZED =
            EventFactory.createArrayBacked(AuctionFinalizedListener.class,
                    listeners -> event -> {
                        for (AuctionFinalizedListener l : listeners) l.onAuctionFinalized(event);
                    });

    // ─── Auction – bid & cancel ───────────────────────────────────────────────

    @FunctionalInterface
    public interface AuctionBidPlacedListener {
        void onAuctionBidPlaced(AuctionBidPlacedEvent event);
    }

    /**
     * Fired after a player's bid is accepted on an open auction.
     * The previous top bidder has already been refunded to escrow before this event fires.
     */
    public static final Event<AuctionBidPlacedListener> AUCTION_BID_PLACED =
            EventFactory.createArrayBacked(AuctionBidPlacedListener.class,
                    listeners -> event -> {
                        for (AuctionBidPlacedListener l : listeners) l.onAuctionBidPlaced(event);
                    });

    @FunctionalInterface
    public interface AuctionCancelledListener {
        void onAuctionCancelled(AuctionCancelledEvent event);
    }

    /**
     * Fired after the seller successfully cancels an active auction.
     * The item is already in the seller's pending-items escrow and any top bidder
     * has already been refunded when this event fires.
     */
    public static final Event<AuctionCancelledListener> AUCTION_CANCELLED =
            EventFactory.createArrayBacked(AuctionCancelledListener.class,
                    listeners -> event -> {
                        for (AuctionCancelledListener l : listeners) l.onAuctionCancelled(event);
                    });
}

