package com.github.devjake123.testmod.economy;

import com.github.devjake123.testmod.JakesEconomy;
import com.github.devjake123.testmod.config.JakesEconomyServerConfig;
import com.github.devjake123.testmod.network.BalanceSyncPayload;
import com.github.devjake123.testmod.network.TransactionHistoryPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EconomyState extends SavedData {

    // NBT key used to store the wallet data
    private static final String SAVE_KEY = "jakeseconomy_economy";
    private static final int MAX_HISTORY = 100;

    // The wallet instance — holds all player balances
    private final PlayerWallet wallet = new PlayerWallet();

    // Persisted netDeficit values per item — loaded into MarketManager on server start
    private final Map<String, Long> deficits = new HashMap<>();

    // Persisted snapshotDeficit values per item — used to compute the trend arrow
    private final Map<String, Long> snapshotDeficits = new HashMap<>();
    private final Map<UUID, Map<String, Long>> deficitContributions = new HashMap<>();

    // Tracks when each player's window started (epoch milliseconds)
    private final Map<UUID, Long> windowStartTimes = new HashMap<>();

    // Per-player transaction history (newest at front, max MAX_HISTORY entries)
    private final Map<UUID, Deque<TransactionEntry>> transactionHistory = new HashMap<>();

    public EconomyState() {}

    // ── Wallet ──────────────────────────────────────────────────────────────────

    public long getBalance(UUID playerId) { return wallet.getBalance(playerId); }

    public void deposit(UUID playerId, long amount) {
        wallet.deposit(playerId, amount);
        setDirty(); // Mark data as changed so it will be saved on next auto-save
    }

    public boolean withdraw(UUID playerId, long amount) {
        boolean success = wallet.withdraw(playerId, amount);
        if (success) setDirty();
        return success;
    }

    public void setBalance(UUID playerId, long amount) {
        wallet.setBalance(playerId, amount);
        setDirty();
    }

    // ── Transaction History ──────────────────────────────────────────────────────

    /** Records a transaction for a player. Keeps only the most recent MAX_HISTORY entries. */
    public void addTransaction(UUID playerId, String type, String itemId, long quantity, long amount) {
        Deque<TransactionEntry> history = transactionHistory.computeIfAbsent(playerId, k -> new ArrayDeque<>());
        history.addFirst(new TransactionEntry(type, itemId, quantity, amount, System.currentTimeMillis()));
        while (history.size() > MAX_HISTORY) history.removeLast();
        setDirty();
    }

    /** Returns the player's transaction history, newest first (up to MAX_HISTORY entries). */
    public List<TransactionEntry> getHistory(UUID playerId) {
        Deque<TransactionEntry> history = transactionHistory.get(playerId);
        return history == null ? List.of() : new ArrayList<>(history);
    }

    // ── Serialization ────────────────────────────────────────────────────────────

    @Override
    @NotNull
    public CompoundTag save(@NotNull CompoundTag tag, @NotNull net.minecraft.core.HolderLookup.Provider registries) {
        // Wallets
        CompoundTag walletTag = new CompoundTag();
        for (Map.Entry<UUID, Long> entry : wallet.getAll().entrySet()) {
            walletTag.putLong(entry.getKey().toString(), entry.getValue());
        }
        tag.put("wallets", walletTag);

        // Save deficit contributions
        CompoundTag deficitTag = new CompoundTag();
        for (Map.Entry<UUID, Map<String, Long>> playerEntry : deficitContributions.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            for (Map.Entry<String, Long> itemEntry : playerEntry.getValue().entrySet()) {
                playerTag.putLong(itemEntry.getKey(), itemEntry.getValue());
            }
            deficitTag.put(playerEntry.getKey().toString(), playerTag);
        }
        tag.put("deficitContributions", deficitTag);

        // Save window start times
        CompoundTag windowTag = new CompoundTag();
        windowStartTimes.forEach((uuid, time) -> windowTag.putLong(uuid.toString(), time));
        tag.put("windowStartTimes", windowTag);

        // Save item market deficits
        CompoundTag deficitsTag = new CompoundTag();
        deficits.forEach(deficitsTag::putLong);
        tag.put("itemDeficits", deficitsTag);

        // Save trend snapshot deficits
        CompoundTag snapshotTag = new CompoundTag();
        snapshotDeficits.forEach(snapshotTag::putLong);
        tag.put("itemSnapshotDeficits", snapshotTag);

        // Transaction history
        CompoundTag historyTag = new CompoundTag();
        for (Map.Entry<UUID, Deque<TransactionEntry>> playerEntry : transactionHistory.entrySet()) {
            ListTag listTag = new ListTag();
            for (TransactionEntry tx : playerEntry.getValue()) {
                CompoundTag txTag = new CompoundTag();
                txTag.putString("type",     tx.type());
                txTag.putString("item",     tx.itemId());
                txTag.putLong("qty",        tx.quantity());
                txTag.putLong("amt",        tx.amount());
                txTag.putLong("time",       tx.timestamp());
                listTag.add(txTag);
            }
            historyTag.put(playerEntry.getKey().toString(), listTag);
        }
        tag.put("txHistory", historyTag);

        return tag;
    }

    //Deserializes from NBT - called automatically by Minecraft when loading the world
    public static EconomyState load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        EconomyState state = new EconomyState();

        // Wallets
        CompoundTag walletTag = tag.getCompound("wallets");
        for (String key : walletTag.getAllKeys()) {
            try { state.wallet.deposit(UUID.fromString(key), walletTag.getLong(key)); }
            catch (IllegalArgumentException e) { JakesEconomy.LOGGER.warn("Invalid UUID in wallet data: {}", key); }
        }

        // Load deficit contributions
        CompoundTag deficitTag = tag.getCompound("deficitContributions");
        for (String playerKey : deficitTag.getAllKeys()) {
            try {
                UUID playerId = UUID.fromString(playerKey);
                CompoundTag playerTag = deficitTag.getCompound(playerKey);
                Map<String, Long> items = new HashMap<>();
                for (String itemKey : playerTag.getAllKeys()) items.put(itemKey, playerTag.getLong(itemKey));
                state.deficitContributions.put(playerId, items);
            } catch (IllegalArgumentException e) { JakesEconomy.LOGGER.warn("Invalid UUID in deficit data: {}", playerKey); }
        }

        // Window start times
        CompoundTag windowTag = tag.getCompound("windowStartTimes");
        for (String key : windowTag.getAllKeys()) {
            try { state.windowStartTimes.put(UUID.fromString(key), windowTag.getLong(key)); }
            catch (IllegalArgumentException ignored) {}
        }

        // Item market deficits
        CompoundTag deficitsTag = tag.getCompound("itemDeficits");
        for (String key : deficitsTag.getAllKeys()) state.deficits.put(key, deficitsTag.getLong(key));

        // Trend snapshot deficits
        CompoundTag snapshotTag = tag.getCompound("itemSnapshotDeficits");
        for (String key : snapshotTag.getAllKeys()) state.snapshotDeficits.put(key, snapshotTag.getLong(key));

        // Transaction history
        CompoundTag historyTag = tag.getCompound("txHistory");
        for (String playerKey : historyTag.getAllKeys()) {
            try {
                UUID playerId = UUID.fromString(playerKey);
                ListTag listTag = historyTag.getList(playerKey, Tag.TAG_COMPOUND);
                Deque<TransactionEntry> history = new ArrayDeque<>();
                for (int i = 0; i < listTag.size(); i++) {
                    try {
                        CompoundTag txTag = listTag.getCompound(i);
                        history.addLast(new TransactionEntry(
                                txTag.getString("type"),
                                txTag.getString("item"),
                                txTag.getLong("qty"),
                                txTag.getLong("amt"),
                                txTag.getLong("time")
                        ));
                    } catch (Exception e) {
                        JakesEconomy.LOGGER.warn("Skipping corrupt transaction entry for player {}: {}", playerKey, e.getMessage());
                    }
                }
                state.transactionHistory.put(playerId, history);
            } catch (IllegalArgumentException ignored) {}
        }


        return state;
    }

    /**
     * NBT corruption guard — wraps load() in a try/catch so that a corrupt save file
     * does not crash the server. Falls back to a clean empty state and logs the error.
     */
    private static EconomyState safeLoad(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        try {
            return load(tag, registries);
        } catch (Exception e) {
            JakesEconomy.LOGGER.error(
                    "Economy save data is corrupted — starting with an empty state. Error: {}", e.getMessage(), e);
            return new EconomyState();
        }
    }

    // ── Rate Limiting ────────────────────────────────────────────────────────────

    public long getRemainingBuyAllowance(UUID playerId, String itemId, long netDeficit, JakesEconomyServerConfig config) {
        if (netDeficit < 0) return Long.MAX_VALUE;
        long windowMillis = (long)(config.deficitWindowHours * 3_600_000L);
        long now = System.currentTimeMillis();
        long windowStart = windowStartTimes.getOrDefault(playerId, 0L);
        if (now - windowStart >= windowMillis) {
            windowStartTimes.put(playerId, now);
            deficitContributions.computeIfAbsent(playerId, k -> new HashMap<>()).clear();
        }
        Map<String, Long> contributions = deficitContributions.computeIfAbsent(playerId, k -> new HashMap<>());
        long current = contributions.getOrDefault(itemId, 0L);
        return Math.max(0, config.deficitLimitPerWindow - current);
    }

    /** Records a buy contribution (positive). */
    public void recordBuyContribution(UUID playerId, String itemId, long amount) {
        deficitContributions.computeIfAbsent(playerId, k -> new HashMap<>()).merge(itemId, amount, Long::sum);
        setDirty();
    }

    /** Records a sell contribution (subtracts — can go negative, meaning the player is in personal surplus). */
    public void recordSellContribution(UUID playerId, String itemId, long amount) {
        deficitContributions.computeIfAbsent(playerId, k -> new HashMap<>()).merge(itemId, -amount, Long::sum);
        setDirty();
    }

    // ── Market Deficits ──────────────────────────────────────────────────────────

    public void saveDeficitsFrom(Map<String, MarketListing> listings) {
        deficits.clear();
        deficits.putAll(listings.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().netDeficit)));
        setDirty();
    }

    /**
     * Saves the current snapshotDeficit of every listing to NBT.
     * Called by TrendSnapshotScheduler after it captures a new snapshot.
     */
    public void saveSnapshotsFrom(Map<String, MarketListing> listings) {
        snapshotDeficits.clear();
        snapshotDeficits.putAll(listings.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().snapshotDeficit)));
        setDirty();
    }

    /**
     * Restores netDeficit values into the listings map from persisted NBT data.
     * Called by MarketManager.initialize() on server start.
     */
    public void loadDeficitsInto(Map<String, MarketListing> listings) {
        for (Map.Entry<String, Long> entry : deficits.entrySet()) {
            MarketListing listing = listings.get(entry.getKey());
            if (listing != null) listing.netDeficit = entry.getValue();
        }
    }

    /**
     * Restores snapshotDeficit values into the listings map from persisted NBT data.
     * Called by MarketManager.initialize() on server start.
     */
    public void loadSnapshotsInto(Map<String, MarketListing> listings) {
        for (Map.Entry<String, Long> entry : snapshotDeficits.entrySet()) {
            MarketListing listing = listings.get(entry.getKey());
            if (listing != null) listing.snapshotDeficit = entry.getValue();
        }
    }

    // ── Static Accessors ─────────────────────────────────────────────────────────

    public static EconomyState get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(new SavedData.Factory<>(EconomyState::new, EconomyState::safeLoad, null), SAVE_KEY);
    }

    /** Sends the player's current balance to their client. */
    public static void syncBalance(ServerPlayer player, MinecraftServer server) {
        long balance = EconomyState.get(server).getBalance(player.getUUID());
        ServerPlayNetworking.send(player, new BalanceSyncPayload(balance));
    }

    /** Sends the player's transaction history to their client. */
    public static void syncHistory(ServerPlayer player, MinecraftServer server) {
        List<TransactionEntry> entries = EconomyState.get(server).getHistory(player.getUUID());
        ServerPlayNetworking.send(player, new TransactionHistoryPayload(entries));
    }
}
