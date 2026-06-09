package com.github.devjake123.jakeseconomy.client;

import com.github.devjake123.jakeseconomy.economy.PricePoint;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side store for price history data received from the server.
 *
 * Two tiers:
 *   Recent  — 20-minute snapshots for the last 24 h. Used by the Day graph view.
 *   Archive — hourly snapshots for up to 30 days.  Used by Week / Month views.
 *
 * Populated on demand: when a player opens the trend graph screen for a market item,
 * the client sends a {@code PriceHistoryRequestPayload} and the server replies with
 * a {@code PriceHistoryResponsePayload} whose JSON is parsed and cached here.
 *
 * Cleared on disconnect to prevent stale data from persisting across sessions.
 */
public final class ClientPriceHistoryCache {

    private ClientPriceHistoryCache() {}

    /** Hourly archive snapshots (Week / Month views). */
    private static final Map<String, List<PricePoint>> ARCHIVE_CACHE = new HashMap<>();
    /** 20-minute recent snapshots (Day view). */
    private static final Map<String, List<PricePoint>> RECENT_CACHE  = new HashMap<>();

    /**
     * Parses and stores a price history JSON response for an item.
     *
     * Accepts two formats:
     *   Tiered (new):  {@code {"recent":[{"t":...,"p":...},...], "archive":[...]}}
     *   Flat (legacy): {@code [{"t":...,"p":...},...]}  — treated as archive-only
     */
    public static void update(String itemId, String json) {
        if (json == null || json.isEmpty()) return;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonObject()) {
                // Tiered format
                JsonObject obj = root.getAsJsonObject();
                RECENT_CACHE .put(itemId, parsePoints(obj.has("recent")  ? obj.get("recent") .getAsJsonArray() : new JsonArray()));
                ARCHIVE_CACHE.put(itemId, parsePoints(obj.has("archive") ? obj.get("archive").getAsJsonArray() : new JsonArray()));
            } else {
                // Legacy flat array — treat as archive only
                ARCHIVE_CACHE.put(itemId, parsePoints(root.getAsJsonArray()));
                RECENT_CACHE .put(itemId, List.of());
            }
        } catch (Exception ignored) {
            // Malformed payload — silently discard
        }
    }

    private static List<PricePoint> parsePoints(JsonArray arr) {
        List<PricePoint> points = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            var obj = el.getAsJsonObject();
            points.add(new PricePoint(obj.get("t").getAsLong(), obj.get("p").getAsDouble()));
        }
        return Collections.unmodifiableList(points);
    }

    /** Returns the 20-minute recent history for an item (Day view), oldest first. */
    public static List<PricePoint> getRecent(String itemId) {
        return RECENT_CACHE.getOrDefault(itemId, List.of());
    }

    /** Returns the hourly archive history for an item (Week / Month views), oldest first. */
    public static List<PricePoint> get(String itemId) {
        return ARCHIVE_CACHE.getOrDefault(itemId, List.of());
    }

    /**
     * Returns {@code true} if the server has responded for this item at least once
     * this session (even if both lists are empty).
     */
    public static boolean has(String itemId) {
        return ARCHIVE_CACHE.containsKey(itemId) || RECENT_CACHE.containsKey(itemId);
    }

    /** Clears all cached history on disconnect. */
    public static void clear() {
        ARCHIVE_CACHE.clear();
        RECENT_CACHE .clear();
    }
}
