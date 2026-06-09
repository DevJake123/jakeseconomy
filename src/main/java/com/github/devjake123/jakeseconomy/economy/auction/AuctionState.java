package com.github.devjake123.jakeseconomy.economy.auction;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import com.github.devjake123.jakeseconomy.economy.AuctionEntry;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.*;

/**
 * Persists all auction data across server restarts via Minecraft's SavedData (NBT) system.
 *
 * Stored in the overworld data directory as "jakeseconomy_auctions.dat".
 *
 * Contains:
 *   - Active and recently-finalized auctions (Map<UUID, AuctionEntry>)
 *   - Pending currency payouts per player (Map<UUID, Long>)
 *   - Pending items to give to players (Map<UUID, List<CompoundTag>>)
 *   - Pending chat notifications for offline players (Map<UUID, List<String>>, capped at 10)
 *
 * Money and items only leave escrow when the player successfully claims them
 */
public class AuctionState extends SavedData {

    private static final String SAVE_KEY = "jakeseconomy_auctions";
    /** Maximum queued notifications per player — prevents chat-flooding on rejoin. */
    private static final int MAX_NOTIFICATIONS = 10;

    // All auctions — active and finalized-but-unclaimed
    final Map<UUID, AuctionEntry> auctions = new LinkedHashMap<>();

    // Currency waiting to be claimed (refunds, sale proceeds)
    final Map<UUID, Long> pendingPayouts = new HashMap<>();

    // Items waiting to be given to players (won items, cancelled seller returns)
    final Map<UUID, List<CompoundTag>> pendingItems = new HashMap<>();

    // Chat notifications queued for offline players — flushed on their next join
    final Map<UUID, List<String>> pendingNotifications = new HashMap<>();

    public AuctionState() {}

    // ─── Static factory ──────────────────────────────────────────────────────

    public static AuctionState get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(AuctionState::new, AuctionState::safeLoad, null),
                SAVE_KEY);
    }

    private static AuctionState safeLoad(CompoundTag tag, net.minecraft.core.HolderLookup.Provider reg) {
        try {
            return load(tag, reg);
        } catch (Exception e) {
            JakesEconomy.LOGGER.error("[Auction] Save data corrupted — starting fresh. Error: {}", e.getMessage(), e);
            return new AuctionState();
        }
    }

    static AuctionState load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider reg) {
        AuctionState state = new AuctionState();

        // Auctions
        CompoundTag auctionsTag = tag.getCompound("auctions");
        for (String key : auctionsTag.getAllKeys()) {
            try {
                UUID auctionId = UUID.fromString(key);
                CompoundTag a  = auctionsTag.getCompound(key);
                UUID sellerId      = UUID.fromString(a.getString("seller"));
                CompoundTag itemTag = a.getCompound("item");
                String itemId      = a.getString("itemId");
                long startingPrice = a.getLong("startingPrice");
                boolean isBin      = a.getBoolean("isBin");
                long endTime       = a.getLong("endTime");
                boolean active     = a.getBoolean("active");

                AuctionEntry entry = new AuctionEntry(auctionId, sellerId, itemTag,
                        itemId, startingPrice, isBin, endTime);
                entry.active = active;

                ListTag bidsTag = a.getList("bids", Tag.TAG_COMPOUND);
                for (int i = 0; i < bidsTag.size(); i++) {
                    CompoundTag b = bidsTag.getCompound(i);
                    entry.bids.add(new BidEntry(
                            UUID.fromString(b.getString("bidder")),
                            b.getLong("amount"),
                            b.getLong("time")));
                }
                state.auctions.put(auctionId, entry);
            } catch (Exception e) {
                JakesEconomy.LOGGER.warn("[Auction] Skipping corrupt auction entry '{}': {}", key, e.getMessage());
            }
        }

        // Pending payouts
        CompoundTag payoutsTag = tag.getCompound("pendingPayouts");
        for (String key : payoutsTag.getAllKeys()) {
            try {
                state.pendingPayouts.put(UUID.fromString(key), payoutsTag.getLong(key));
            } catch (Exception e) {
                JakesEconomy.LOGGER.warn("[Auction] Skipping corrupt payout for '{}'", key);
            }
        }

        // Pending items
        CompoundTag pendingItemsTag = tag.getCompound("pendingItems");
        for (String key : pendingItemsTag.getAllKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                ListTag itemList = pendingItemsTag.getList(key, Tag.TAG_COMPOUND);
                List<CompoundTag> items = new ArrayList<>();
                for (int i = 0; i < itemList.size(); i++) items.add(itemList.getCompound(i));
                state.pendingItems.put(playerId, items);
            } catch (Exception e) {
                JakesEconomy.LOGGER.warn("[Auction] Skipping corrupt pending items for '{}'", key);
            }
        }

        // Pending notifications
        CompoundTag notifTag = tag.getCompound("pendingNotifications");
        for (String key : notifTag.getAllKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                ListTag msgList = notifTag.getList(key, Tag.TAG_STRING);
                List<String> msgs = new ArrayList<>();
                for (int i = 0; i < msgList.size(); i++) msgs.add(msgList.getString(i));
                state.pendingNotifications.put(playerId, msgs);
            } catch (Exception e) {
                JakesEconomy.LOGGER.warn("[Auction] Skipping corrupt pending notifications for '{}'", key);
            }
        }

        return state;
    }

    // ─── Serialization ───────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider reg) {
        // Auctions
        CompoundTag auctionsTag = new CompoundTag();
        for (Map.Entry<UUID, AuctionEntry> entry : auctions.entrySet()) {
            AuctionEntry a = entry.getValue();
            CompoundTag aTag = new CompoundTag();
            aTag.putString("seller",       a.sellerId.toString());
            aTag.put      ("item",         a.itemTag);
            aTag.putString("itemId",       a.itemId);
            aTag.putLong  ("startingPrice",a.startingPrice);
            aTag.putBoolean("isBin",       a.isBin);
            aTag.putLong  ("endTime",      a.endTimeEpochMs);
            aTag.putBoolean("active",      a.active);
            ListTag bidsTag = new ListTag();
            for (BidEntry bid : a.bids) {
                CompoundTag bTag = new CompoundTag();
                bTag.putString("bidder", bid.bidderId().toString());
                bTag.putLong  ("amount", bid.amount());
                bTag.putLong  ("time",   bid.timestampMs());
                bidsTag.add(bTag);
            }
            aTag.put("bids", bidsTag);
            auctionsTag.put(entry.getKey().toString(), aTag);
        }
        tag.put("auctions", auctionsTag);

        // Pending payouts
        CompoundTag payoutsTag = new CompoundTag();
        pendingPayouts.forEach((uuid, amt) -> payoutsTag.putLong(uuid.toString(), amt));
        tag.put("pendingPayouts", payoutsTag);

        // Pending items
        CompoundTag pendingItemsTag = new CompoundTag();
        for (Map.Entry<UUID, List<CompoundTag>> entry : pendingItems.entrySet()) {
            ListTag list = new ListTag();
            for (CompoundTag itemTag : entry.getValue()) list.add(itemTag);
            pendingItemsTag.put(entry.getKey().toString(), list);
        }
        tag.put("pendingItems", pendingItemsTag);

        // Pending notifications
        CompoundTag notifTag = new CompoundTag();
        for (Map.Entry<UUID, List<String>> entry : pendingNotifications.entrySet()) {
            ListTag list = new ListTag();
            for (String msg : entry.getValue()) list.add(net.minecraft.nbt.StringTag.valueOf(msg));
            notifTag.put(entry.getKey().toString(), list);
        }
        tag.put("pendingNotifications", notifTag);

        return tag;
    }

    // ─── Escrow helpers ───────────────────────────────────────────────────────

    public void addPendingPayout(UUID playerId, long amount) {
        if (amount <= 0) return;
        pendingPayouts.merge(playerId, amount, Long::sum);
        setDirty();
    }

    public void addPendingItem(UUID playerId, CompoundTag itemTag) {
        pendingItems.computeIfAbsent(playerId, k -> new ArrayList<>()).add(itemTag);
        setDirty();
    }

    // ─── Notification helpers ────────────────────────────────────────────────

    /**
     * Queues a chat notification for a player who is currently offline.
     * Capped at {@value MAX_NOTIFICATIONS} messages per player to prevent chat-flooding on rejoin.
     */
    public void addPendingNotification(UUID playerId, String message) {
        List<String> msgs = pendingNotifications.computeIfAbsent(playerId, k -> new ArrayList<>());
        if (msgs.size() < MAX_NOTIFICATIONS) {
            msgs.add(message);
            setDirty();
        }
    }

    /**
     * Returns and removes all queued notifications for the given player.
     * Returns an empty list if there are none.
     */
    public List<String> drainNotifications(UUID playerId) {
        List<String> msgs = pendingNotifications.remove(playerId);
        if (msgs != null && !msgs.isEmpty()) setDirty();
        return msgs != null ? msgs : List.of();
    }

    /** Returns true if the player has any unclaimed currency or items. */
    public boolean hasPendingClaims(UUID playerId) {
        long payout = pendingPayouts.getOrDefault(playerId, 0L);
        List<CompoundTag> items = pendingItems.get(playerId);
        return payout > 0 || (items != null && !items.isEmpty());
    }

    /**
     * Removes inactive auction entries whose end time is older than {@code keepForMs} milliseconds.
     * Safe to call at any time — escrow (pendingItems / pendingPayouts) is stored separately
     * and is NOT affected by pruning. Only the auction record itself is removed.
     *
     * @param keepForMs how long after an auction ends to keep the record (e.g. 30 min = 1_800_000L)
     * @return number of entries removed
     */
    public int pruneFinalized(long keepForMs) {
        long cutoff = System.currentTimeMillis() - keepForMs;
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, AuctionEntry> e : auctions.entrySet()) {
            AuctionEntry a = e.getValue();
            if (!a.active && a.endTimeEpochMs < cutoff) {
                toRemove.add(e.getKey());
            }
        }
        if (!toRemove.isEmpty()) {
            toRemove.forEach(auctions::remove);
            setDirty();
        }
        return toRemove.size();
    }
}


