package com.github.devjake123.jakeseconomy.economy.auction;

import java.util.UUID;

/** Represents a single bid in an auction — immutable snapshot stored in AuctionEntry. */
public record BidEntry(UUID bidderId, long amount, long timestampMs) {}


