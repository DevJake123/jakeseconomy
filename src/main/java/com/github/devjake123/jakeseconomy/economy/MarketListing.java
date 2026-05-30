package com.github.devjake123.jakeseconomy.economy;

import static com.github.devjake123.jakeseconomy.economy.MarketManager.PRICE_CEILING;
import static com.github.devjake123.jakeseconomy.economy.MarketManager.PRICE_FLOOR;

/** * Represents the market state for a single tradeable item.
 * * * Each item tracked by the market has one MarketListing stored in MarketManager.
 * * The listing tracks the base price (set by config or op command) and the running
 * * netDeficit which drives price fluctuation.
 * * * Price formula:
 * *   multiplier = 1 + sensitivity * ln(1 + netDeficit / marketDepth)
 * *   currentPrice = basePrice * clamp(multiplier, PRICE_FLOOR, PRICE_CEILING) * * netDeficit:
 * *   Positive = more bought than sold = scarcity = price above base
 * *   Negative = more sold than bought = surplus = price below base
 * *   Zero     = balanced = exactly base price */
public class MarketListing {

    // The base price in virtual currency units — price when netDeficit = 0.
    // Set from jakeseconomy-prices.json or via /jakeseconomy market setprice.
    public double basePrice;

    // Per-item override for marketDepth. -1 means use the global value from server config.
    // Allows rare items to respond more dramatically to market pressure than common ones.
    public double marketDepthOverride;

    // Running total of (totalBought - totalSold) for this item across all players, all time.
    // Persisted to NBT so prices survive server restarts.
    // Decays toward 0 over time if priceDecayEnabled = true in server config.
    public long netDeficit;

    // Snapshot of netDeficit taken every ~3 minutes by TrendSnapshotScheduler.
    // getTrend() compares the current netDeficit against this value so the arrow
    // reflects recent price movement rather than the absolute direction from base price.
    // Persisted to NBT so the arrow survives a server restart.
    public long snapshotDeficit;

    public MarketListing(double basePrice, double marketDepthOverride) {
        this.basePrice = basePrice;
        this.marketDepthOverride = marketDepthOverride;
        this.netDeficit = 0;
        this.snapshotDeficit = 0;
    }

    /**     * Calculates the current price for this item given the current config settings.     * Uses the logarithmic price curve — small deficits barely move the price,     * large deficits cause exponential increases.     *     * @param globalMarketDepth  the server config's marketDepth (used if no per-item override)     * @param sensitivity        the server config's sensitivity value     * @return the current price per unit in virtual currency     */
    public double getCurrentPrice(double globalMarketDepth, double sensitivity) {
        double depth = (marketDepthOverride > 0) ? marketDepthOverride : globalMarketDepth;
        double multiplier = 1.0 + sensitivity * Math.log1p((double) netDeficit / depth);
        // Clamp to internal safeguards only — no gameplay cap by design
        multiplier = Math.clamp(multiplier, PRICE_FLOOR / basePrice, PRICE_CEILING / basePrice);
        return basePrice * multiplier;
    }

    /**     * Returns a simple trend indicator based on recent deficit movement.
     * getTrend() compares the current netDeficit to the last snapshot (taken every ~3 min)
     * so the arrow reflects whether prices have been rising or falling recently,
     * not just whether they are currently above or below the base price.
     */
    public PriceTrend getTrend() {
        if (netDeficit > snapshotDeficit) return PriceTrend.RISING;
        if (netDeficit < snapshotDeficit) return PriceTrend.FALLING;
        return PriceTrend.STABLE;
    }

    public enum PriceTrend {
        RISING,   // More bought than sold — price above base
        STABLE,   // Balanced — price at base
        FALLING   // More sold than bought — price below base
    }

}

