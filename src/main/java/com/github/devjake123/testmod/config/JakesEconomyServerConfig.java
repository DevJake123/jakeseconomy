package com.github.devjake123.testmod.config;

public class JakesEconomyServerConfig {

    // --- Market Settings ---

    // Whether players can propose unlisted modded/vanilla items to be added to the market.
    // If false, only items defined in jakeseconomy-prices.json are tradeable.
    public boolean allowModdedItems = false;

    // If allowModdedItems is true, whether an op must approve a player's price proposal
    // before the item becomes tradeable. If false, proposals are auto-approved.
    public boolean requireOpApproval = true;

    // --- Price Decay Settings ---

    // Whether prices slowly recover back toward their base value over time.
    // Recommended ON for singleplayer (set automatically on first launch),
    public boolean priceDecayEnabled = true;

    // How much the price recovers toward base per interval, as a percentage.
    // e.g. 5.0 means each interval: netDeficit *= (1 - 0.05), shrinking the deficit by 5%.
    // Higher = faster recovery. Lower = prices stay shifted longer.
    public double priceDecayRatePercent = 5.0;

    // How many hours between each decay tick.
    // e.g. 1.0 = decay applies once per real-world hour of server uptime.
    public double priceDecayIntervalHours = 1.0;

    // --- Price Formula Settings ---

    // Controls how many items need to be bought/sold before the price feels meaningfully different.
    // Formula: multiplier = 1 + sensitivity * ln(1 + netDeficit / marketDepth)
    // Lower marketDepth = price moves faster with fewer trades.
    // Higher marketDepth = requires more trades to shift price (good for automation-heavy modpacks).
    // Recommended values:
    //   Vanilla survival:        500 - 2000
    //   Lightly modded:          5000  (default)
    //   Tech/automation modpack: 100000+
    public double marketDepth = 5000.0;

    // Controls the steepness of the price curve.
    // Higher sensitivity = more dramatic price swings for the same deficit.
    // At sensitivity=2.3 with marketDepth=5000:
    //   100k deficit  ≈ 8x base price
    //   1M deficit    ≈ 14.7x base price
    //   100k surplus  ≈ 0.13x base price (near crash)
    public double sensitivity = 2.3;

    // Maximum amount a single player can contribute to an item's deficit per time window.
    // Prevents a player from instantly crashing/spiking a price by buying/selling in bulk.
    // 64 = one full stack — a player can meaningfully impact the market up to one stack per window.
    public long deficitLimitPerWindow = 64L;

    // How many real-world hours before a player's deficit contribution resets,
    // allowing them to affect the market again up to deficitLimitPerWindow.
    public double deficitWindowHours = 1.0;
}