package com.github.devjake123.jakeseconomy.api.event;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Fired when an auction is finalized — either naturally on expiry, via BIN purchase,
 * or when the server starts and finds auctions that expired during downtime.
 *
 * <p>{@code winnerId} is {@code null} when the auction ended with no bids (item returns
 * to the seller's escrow).
 *
 * @param auctionId  the auction's unique id
 * @param sellerId   the player who created the listing
 * @param winnerId   the top bidder / BIN buyer, or {@code null} if no bids
 * @param itemId     namespaced item id of the listed item
 * @param finalPrice winning bid amount (0 if no bids)
 */
public record AuctionFinalizedEvent(
        UUID      auctionId,
        UUID      sellerId,
        @Nullable UUID winnerId,
        String    itemId,
        long      finalPrice
) {}

