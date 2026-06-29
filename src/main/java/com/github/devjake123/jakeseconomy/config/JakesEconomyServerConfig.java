package com.github.devjake123.jakeseconomy.config;

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

    // --- Auction Settings ---

    // If a bid is placed within this many milliseconds of an auction's end time,
    // the auction is extended by this same duration to prevent last-second sniping.
    // Default: 120 000 ms = 2 minutes.
    public long antiSnipeExtensionMs = 120_000L;

    // Minimum percentage of the current top bid that a new bid must exceed it by.
    // e.g. 1.0 = the new bid must be at least 1% higher than the current top bid.
    // The minimum increment is always at least 1 currency unit regardless of this setting.
    public double minBidIncrementPercent = 1.0;

    // Percentage of the listing price charged as a non-refundable fee when a listing is created.
    // e.g. 2.0 = 2% of the listing price is deducted from the seller's balance at listing time.
    // Set to 0 to disable listing fees.
    public double listingFeePercent = 1.0;

    // Maximum price a player can set for a single listing (in base currency units).
    // Prevents overflow edge cases and unreasonable listings.
    // Default: 1 000 000 000 000 (1 trillion).
    public long maxListingPrice = 1_000_000_000_000L;

    // --- Auction Item Control ---

    // "all"       - Any item not in the market can be auctioned (default)
    // "whitelist" - Only items in auctionWhitelist can be auctioned
    // "blacklist" - Any item EXCEPT those in auctionBlacklist can be auctioned
    public String auctionItemMode = "all";

    // If true, items that are also in the main market can be auctioned
    // (as long as they also pass the whitelist/blacklist rules).
    public boolean allowMarketItemsInAuction = false;

    public java.util.List<String> auctionWhitelist = new java.util.ArrayList<>();
    public java.util.List<String> auctionBlacklist = new java.util.ArrayList<>();

    // --- GUI Tab Visibility ---

    // Control which tabs are visible in the market GUI.
    // Useful for map-makers who want to limit functionality or simplify the UI.
    public boolean showMarketTab = true;
    public boolean showWithdrawTab = true;
    public boolean showHistoryTab = true;
    public boolean showAuctionTab = true;

    // If false, the client-side keybind (default: ;) will not open the GUI.
    // Players can still open it via /jecon market open or an NPC trigger.
    // Useful for adventure maps that want full control over when the market appears.
    public boolean allowHotkeyOpen = true;

    // --- Command Permission Levels ---

    /**
     * Per-command permission level overrides. Defaults mirror the old hard-coded
     * behaviour: admin commands require level 2 (op), player commands require 0.
     *
     * Minecraft permission levels:
     *   0 = all players
     *   1 = moderator ops
     *   2 = operator (standard op)
     *   3 = admin ops
     *   4 = server console / owner
     */
    public static class Permissions {
        /** /jecon balance <player> — check another player's balance */
        public int balanceOther = 2;
        /** /jecon give */
        public int give = 2;
        /** /jecon set */
        public int set = 2;
        /** /jecon take */
        public int take = 2;
        /** /jecon market open — open the market GUI (set to 0 to allow all players) */
        public int marketOpen = 0;
        /** /jecon market setprice, addcategory, removeprice, setlock, price */
        public int marketAdmin = 2;
        /** /jecon auction open — open the auction GUI (set to 0 to allow all players) */
        public int auctionOpen = 0;
        /** /jecon debug */
        public int debug = 2;
    }

    public Permissions permissions = new Permissions();

    /**
     * Called after Gson deserialization to fill in any fields that Gson left at
     * their Java primitive default (0 / false) because the config file pre-dates them.
     * This means adding new fields here is always backwards-compatible — old config
     * files will automatically pick up the intended defaults on next load/save.
     */
    public void mergeDefaults() {
        JakesEconomyServerConfig d = new JakesEconomyServerConfig();
        if (marketDepth             <= 0)  marketDepth             = d.marketDepth;
        if (sensitivity             <= 0)  sensitivity             = d.sensitivity;
        if (deficitLimitPerWindow   <= 0)  deficitLimitPerWindow   = d.deficitLimitPerWindow;
        if (deficitWindowHours      <= 0)  deficitWindowHours      = d.deficitWindowHours;
        if (priceDecayRatePercent   <= 0)  priceDecayRatePercent   = d.priceDecayRatePercent;
        if (priceDecayIntervalHours <= 0)  priceDecayIntervalHours = d.priceDecayIntervalHours;
        if (antiSnipeExtensionMs    <= 0)  antiSnipeExtensionMs    = d.antiSnipeExtensionMs;
        if (minBidIncrementPercent  <= 0)  minBidIncrementPercent  = d.minBidIncrementPercent;
        // listingFeePercent == 0 is a valid setting (no fee), so only backfill if negative
        if (listingFeePercent        < 0)  listingFeePercent        = d.listingFeePercent;
        if (maxListingPrice         <= 0)  maxListingPrice          = d.maxListingPrice;

        if (auctionItemMode == null || auctionItemMode.isEmpty()) auctionItemMode = d.auctionItemMode;
        if (auctionWhitelist == null) auctionWhitelist = d.auctionWhitelist;
        if (auctionBlacklist == null) auctionBlacklist = d.auctionBlacklist;
        if (permissions == null) permissions = new Permissions();
    }
}
