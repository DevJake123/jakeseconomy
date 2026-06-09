package com.github.devjake123.jakeseconomy.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client-side cache of auction listings synced from the server.
 * Populated by AuctionListSyncPayload and AuctionDeltaSyncPayload receivers.
 * Read by AuctionScreen for rendering.
 */
public final class ClientAuctionCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("ClientAuctionCache");

    /** Lightweight client DTO — display data plus serialised item NBT for tooltips. */
    public static class AuctionDto {
        public final String  id;
        public final String  sellerId;
        public final String  sellerName;
        public final String  itemId;
        public final String  displayName;
        /** SNBT string of the original ItemStack — used to reconstruct the tooltip. */
        public final String  itemSnbt;
        public final long    startingPrice;
        public final boolean isBin;
        public final long    topBid;
        public final String  topBidderId;
        public final long    endTimeMs;
        public final boolean active;
        public final int     bidCount;
        /** Full bid list: [{"bidder":"uuid","amt":long}, ...] */
        public final List<BidDtoEntry> bids;

        public AuctionDto(String id, String sellerId, String sellerName, String itemId,
                          String displayName, String itemSnbt, long startingPrice, boolean isBin,
                          long topBid, String topBidderId, long endTimeMs,
                          boolean active, int bidCount, List<BidDtoEntry> bids) {
            this.id            = id;
            this.sellerId      = sellerId;
            this.sellerName    = sellerName;
            this.itemId        = itemId;
            this.displayName   = displayName;
            this.itemSnbt      = itemSnbt;
            this.startingPrice = startingPrice;
            this.isBin         = isBin;
            this.topBid        = topBid;
            this.topBidderId   = topBidderId;
            this.endTimeMs     = endTimeMs;
            this.active        = active;
            this.bidCount      = bidCount;
            this.bids          = bids;
        }

        /** Returns this player's current bid amount, or 0 if not the top bidder.
         *  The server only sends topBid + topBidderId — historical bids are not transmitted. */
        public long getMyBid(UUID myId) {
            if (myId != null && myId.toString().equals(topBidderId)) return topBid;
            return 0;
        }

        public record BidDtoEntry(String bidderId, long amount) {}
    }

    private static final List<AuctionDto> AUCTIONS = new ArrayList<>();
    private static int    totalKnown   = 0;
    private static boolean hasClaims   = false;
    private static boolean loading     = false;

    // ─── Accessors ────────────────────────────────────────────────────────────

    public static List<AuctionDto> get()        { return AUCTIONS; }
    public static int              total()       { return totalKnown; }
    public static boolean          hasClaims()   { return hasClaims; }
    public static boolean          isLoading()   { return loading; }

    public static void setHasClaims(boolean v)  { hasClaims = v; }
    public static void setLoading(boolean v)     { loading = v; }

    // ─── Update (full page / replace) ─────────────────────────────────────────

    /**
     * Merges an AuctionListSyncPayload JSON chunk into the cache.
     * If offset == 0, the cache is cleared first (new request cycle).
     */
    public static void update(String json) {
        if (json == null || json.isEmpty()) return;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            int offset = root.has("offset") ? root.get("offset").getAsInt() : 0;
            totalKnown = root.has("total")  ? root.get("total").getAsInt()  : 0;

            if (offset == 0) {
                AUCTIONS.clear();
                loading = false;
            }

            JsonArray entries = root.has("entries") ? root.get("entries").getAsJsonArray() : new JsonArray();
            for (JsonElement el : entries) {
                AUCTIONS.add(parseDto(el.getAsJsonObject()));
            }
        } catch (Exception e) {
            LOGGER.error("[AuctionCache] Failed to parse auction list JSON: {}", e.getMessage());
        }
    }

    /**
     * Merges a delta sync payload (single entry) — updates existing or adds new.
     */
    public static void applyDelta(String json) {
        if (json == null || json.isEmpty()) return;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray entries = root.has("entries") ? root.get("entries").getAsJsonArray() : new JsonArray();
            for (JsonElement el : entries) {
                AuctionDto dto = parseDto(el.getAsJsonObject());
                // Replace existing or add
                boolean found = false;
                for (int i = 0; i < AUCTIONS.size(); i++) {
                    if (AUCTIONS.get(i).id.equals(dto.id)) {
                        if (dto.active) {
                            AUCTIONS.set(i, dto);
                        } else {
                            AUCTIONS.remove(i);
                        }
                        found = true;
                        break;
                    }
                }
                if (!found && dto.active) AUCTIONS.add(dto);
            }
        } catch (Exception e) {
            LOGGER.error("[AuctionCache] Failed to apply auction delta JSON: {}", e.getMessage());
        }
    }

    public static void clear() {
        AUCTIONS.clear();
        totalKnown = 0;
        hasClaims  = false;
        loading    = false;
    }

    // ─── JSON parsing ─────────────────────────────────────────────────────────

    private static AuctionDto parseDto(JsonObject o) {
        String id           = o.has("id")           ? o.get("id").getAsString()           : "";
        String sellerId     = o.has("seller")        ? o.get("seller").getAsString()        : "";
        String sellerName   = o.has("sellerName")    ? o.get("sellerName").getAsString()    : "Unknown";
        String itemId       = o.has("itemId")        ? o.get("itemId").getAsString()        : "";
        String displayName  = o.has("displayName")   ? o.get("displayName").getAsString()   : itemId;
        String itemSnbt     = o.has("snbt")          ? o.get("snbt").getAsString()          : "";
        long startingPrice  = o.has("startingPrice") ? o.get("startingPrice").getAsLong()   : 0;
        boolean isBin       = o.has("isBin")         && o.get("isBin").getAsBoolean();
        long topBid         = o.has("topBid")        ? o.get("topBid").getAsLong()          : 0;
        String topBidderId  = o.has("topBidderId")   ? o.get("topBidderId").getAsString()   : "";
        long endTimeMs      = o.has("endTime")       ? o.get("endTime").getAsLong()         : 0;
        boolean active      = !o.has("active")       || o.get("active").getAsBoolean();
        int bidCount        = o.has("bidCount")      ? o.get("bidCount").getAsInt()         : 0;

        List<AuctionDto.BidDtoEntry> bids = new ArrayList<>();
        if (o.has("bids")) {
            for (JsonElement b : o.get("bids").getAsJsonArray()) {
                JsonObject bo = b.getAsJsonObject();
                bids.add(new AuctionDto.BidDtoEntry(
                        bo.has("bidder") ? bo.get("bidder").getAsString() : "",
                        bo.has("amt")    ? bo.get("amt").getAsLong()      : 0));
            }
        }

        return new AuctionDto(id, sellerId, sellerName, itemId, displayName, itemSnbt,
                startingPrice, isBin, topBid, topBidderId, endTimeMs,
                active, bidCount, bids);
    }
}


