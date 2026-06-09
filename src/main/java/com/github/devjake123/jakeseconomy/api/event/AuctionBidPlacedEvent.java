package com.github.devjake123.jakeseconomy.api.event;

import java.util.UUID;

/**
 * Fired after a player successfully places a bid on an open auction.
 *
 * <p>The bid amount has already been deducted from the bidder's balance and the
 * previous top bidder (if any) has already been refunded to their escrow.
 *
 * @param auctionId    the auction's unique id
 * @param bidderId     the player who placed the bid
 * @param bidAmount    the new top-bid amount
 * @param itemId       namespaced item id of the listed item
 * @param antiSniped   {@code true} if the bid triggered an anti-snipe time extension
 */
public record AuctionBidPlacedEvent(
        UUID   auctionId,
        UUID   bidderId,
        long   bidAmount,
        String itemId,
        boolean antiSniped
) {}

