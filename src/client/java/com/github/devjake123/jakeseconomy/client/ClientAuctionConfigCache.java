package com.github.devjake123.jakeseconomy.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache of auction item-filter configuration synced from the server.
 * Populated by AuctionConfigSyncPayload on player join.
 * Read by AuctionScreen to filter the "New Listing" item picker.
 */
public final class ClientAuctionConfigCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("ClientAuctionConfigCache");

    private static String       mode             = "all";
    private static List<String> whitelist        = new ArrayList<>();
    private static List<String> blacklist        = new ArrayList<>();
    private static boolean      allowMarketItems = false;

    // ─── Accessors ────────────────────────────────────────────────────────────

    /** "all", "whitelist", or "blacklist" */
    public static String mode()                        { return mode; }
    public static boolean isWhitelisted(String itemId) { return whitelist.contains(itemId); }
    public static boolean isBlacklisted(String itemId) { return blacklist.contains(itemId); }
    /** Whether items that are also in the main market can be auctioned. */
    public static boolean allowMarketItems()           { return allowMarketItems; }

    // ─── Update ───────────────────────────────────────────────────────────────

    public static void update(String json) {
        if (json == null || json.isEmpty()) return;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            mode             = root.has("mode")             ? root.get("mode").getAsString()             : "all";
            allowMarketItems = root.has("allowMarketItems") && root.get("allowMarketItems").getAsBoolean();

            whitelist = new ArrayList<>();
            if (root.has("whitelist")) {
                for (JsonElement e : root.get("whitelist").getAsJsonArray())
                    whitelist.add(e.getAsString());
            }
            blacklist = new ArrayList<>();
            if (root.has("blacklist")) {
                for (JsonElement e : root.get("blacklist").getAsJsonArray())
                    blacklist.add(e.getAsString());
            }
        } catch (Exception e) {
            LOGGER.error("[AuctionConfigCache] Failed to parse auction config JSON: {}", e.getMessage());
        }
    }

    /** Reset to defaults — called on disconnect so stale config doesn't bleed into a new session. */
    public static void reset() {
        mode             = "all";
        whitelist        = new ArrayList<>();
        blacklist        = new ArrayList<>();
        allowMarketItems = false;
    }
}

