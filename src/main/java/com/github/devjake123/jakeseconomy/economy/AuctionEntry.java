package com.github.devjake123.jakeseconomy.economy;

import com.github.devjake123.jakeseconomy.economy.auction.BidEntry;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mutable server-side representation of a single auction or BIN listing.
 * Stored in AuctionState's NBT save data.
 */
public class AuctionEntry {

    public final UUID    auctionId;
    public final UUID    sellerId;
    /** Full ItemStack NBT */
    public final CompoundTag itemTag;
    /** Resource location string, cached so we don't need to decode itemTag for display. */
    public final String  itemId;
    /** Starting price (min bid for auction; buy price for BIN). */
    public final long    startingPrice;
    public final boolean isBin;
    /** Wall-clock expiry */
    public long  endTimeEpochMs;  // mutable
    /** All bids in chronological order. Only the CURRENT top bidder has live funds held;
     *  all previous top bidders were refunded immediately when outbid. */
    public final List<BidEntry> bids;
    public boolean active;

    public AuctionEntry(UUID auctionId, UUID sellerId, CompoundTag itemTag,
                        String itemId, long startingPrice, boolean isBin, long endTimeEpochMs) {
        this.auctionId      = auctionId;
        this.sellerId       = sellerId;
        this.itemTag        = itemTag;
        this.itemId         = itemId;
        this.startingPrice  = startingPrice;
        this.isBin          = isBin;
        this.endTimeEpochMs = endTimeEpochMs;
        this.bids           = new ArrayList<>();
        this.active         = true;
    }

    /** Current highest bid, or startingPrice if no bids placed yet. */
    public long getTopBid() {
        return bids.isEmpty() ? startingPrice : bids.get(bids.size() - 1).amount();
    }

    /** UUID of the current top bidder, or null if no bids placed yet. */
    public UUID getTopBidder() {
        return bids.isEmpty() ? null : bids.get(bids.size() - 1).bidderId();
    }

    /**
     * Returns the amount that the given player has bid, or 0 if they have never bid.
     * Because previous bidders are immediately refunded when outbid, this only returns
     * a non-zero value for the current top bidder.
     */
    public long getBidBy(UUID playerId) {
        // Walk backwards — the last bid by this player is their current held amount.
        for (int i = bids.size() - 1; i >= 0; i--) {
            if (bids.get(i).bidderId().equals(playerId)) {
                return bids.get(i).amount();
            }
        }
        return 0;
    }
}


