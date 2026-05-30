package com.github.devjake123.jakeseconomy.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache of live market listing data synced from the server.
 *
 * Populated by MarketListingSyncPayload receivers registered in JakesEconomyClient.
 * The GUI reads from here instead of calling MarketManager.get() (which is only
 * available in the integrated server / singleplayer, not on dedicated servers).
 *
 * Data format received:
 *   {"itemId": {"p": <currentPrice>, "nd": <netDeficit>, "snd": <snapshotDeficit>}}
 * where "nd" and "snd" are used together to derive the trend arrow direction.
 */
public final class ClientMarketListingCache {

    private ClientMarketListingCache() {}

    /** Holds the live price and deficit info for a single item. */
    public record ListingData(double price, long netDeficit, long snapshotDeficit) {

        /** Short trend arrow: ↑ rising, ↓ falling, — stable (compared against last snapshot). */
        public String trend() {
            if (netDeficit > snapshotDeficit) return "↑";
            if (netDeficit < snapshotDeficit) return "↓";
            return "—";
        }

        /** Trend label with description (for the item detail screen). */
        public String trendLong() {
            if (netDeficit > snapshotDeficit) return "↑ Rising";
            if (netDeficit < snapshotDeficit) return "↓ Falling";
            return "— Stable";
        }

        /** Colour to render the trend arrow (green = cheap/falling, red = expensive/rising). */
        public int trendColor() {
            if (netDeficit > snapshotDeficit) return 0xFFFF4444;
            if (netDeficit < snapshotDeficit) return 0xFF44FF44;
            return 0xFF888888;
        }
    }

    private static final Map<String, ListingData> CACHE = new HashMap<>();

    /**
     * Merges a JSON blob of listing data into the local cache.
     * Handles both full-sync (many entries) and delta-sync (single entry) payloads.
     */
    public static void update(String json) {
        if (json == null || json.isEmpty()) return;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonObject val = entry.getValue().getAsJsonObject();
                double price = val.get("p").getAsDouble();
                long   nd    = val.get("nd").getAsLong();
                long   snd   = val.get("snd").getAsLong();
                CACHE.put(entry.getKey(), new ListingData(price, nd, snd));
            }
        } catch (Exception ignored) {
            // Malformed payload — silently discard
        }
    }

    /**
     * Returns the cached live price for the given item, or the provided fallback
     * (typically the base price from the price config) if no data has arrived yet.
     */
    public static double getPrice(String itemId, double fallback) {
        ListingData d = CACHE.get(itemId);
        return (d != null && d.price() > 0) ? d.price() : fallback;
    }

    /** Returns the short trend arrow for an item ("↑", "↓", or "—"). */
    public static String getTrend(String itemId) {
        ListingData d = CACHE.get(itemId);
        return d != null ? d.trend() : "—";
    }

    /** Returns the long trend label for an item ("↑ Rising", "↓ Falling", "— Stable"). */
    public static String getTrendLong(String itemId) {
        ListingData d = CACHE.get(itemId);
        return d != null ? d.trendLong() : "— Stable";
    }

    /** Returns the colour for the trend text. */
    public static int getTrendColor(String itemId) {
        ListingData d = CACHE.get(itemId);
        return d != null ? d.trendColor() : 0xFF888888;
    }

    /** Clears the cache — called on client disconnect to prevent stale data across sessions. */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * Returns {@code true} if the given item ID is a market (shop) item.
     * The cache is populated from the server's price config on join, so any item
     * present here is tradeable in the market and must not be listed in the Auction House.
     */
    public static boolean isMarketItem(String itemId) {
        return CACHE.containsKey(itemId);
    }
}


