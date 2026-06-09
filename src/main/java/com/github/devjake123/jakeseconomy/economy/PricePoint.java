package com.github.devjake123.jakeseconomy.economy;

/**
 * A single price snapshot captured by {@link PriceHistoryScheduler}.
 * Two tiers: recent (every 20 min, 72 points = 24h) and archive (hourly, 720 points = 30 days).
 *
 * @param timestamp  epoch milliseconds when this snapshot was taken (server wall-clock time)
 * @param price      the item's calculated current price at that moment
 */
public record PricePoint(long timestamp, double price) {}

