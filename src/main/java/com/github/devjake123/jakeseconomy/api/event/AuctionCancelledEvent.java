package com.github.devjake123.jakeseconomy.api.event;

import java.util.UUID;

/**
 * Fired after an active auction is cancelled by its seller.
 *
 * <p>By the time this event fires, the item has already been moved to the seller's
 * pending-items escrow, and the top bidder (if any) has been refunded to their
 * pending-payouts escrow.
 *
 * @param auctionId        the auction's unique id
 * @param sellerId         the player who cancelled the listing
 * @param itemId           namespaced item id of the listed item
 * @param refundedBidderId the UUID of the top bidder who was refunded, or {@code null} if there were no bids
 * @param refundedAmount   the amount refunded to the top bidder (0 if no bids)
 */
public record AuctionCancelledEvent(
        UUID auctionId,
        UUID sellerId,
        String itemId,
        java.util.@org.jetbrains.annotations.Nullable UUID refundedBidderId,
        long refundedAmount
) {}

