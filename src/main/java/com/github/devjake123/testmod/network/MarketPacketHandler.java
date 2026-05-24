package com.github.devjake123.testmod.network;

import com.github.devjake123.testmod.config.JakesEconomyConfigManager;
import com.github.devjake123.testmod.config.JakesEconomyPriceConfig;
import com.github.devjake123.testmod.economy.EconomyState;
import com.github.devjake123.testmod.economy.MarketListing;
import com.github.devjake123.testmod.economy.MarketManager;
import com.github.devjake123.testmod.init.JakesEconomyItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Registers network packets and their server-side handlers.
 *
 * Called once from JakesEconomy.onInitialize().
 * Registers both payload types so Fabric knows how to decode them,
 * then registers the server-side handler that processes them.
 *
 * Flow:
 *   Player clicks Buy/Sell in GUI
 *   → client sends MarketBuyPayload / MarketSellPayload
 *   → server receives it here
 *   → MarketManager.buy() / sell() runs on server tick thread
 */
public class MarketPacketHandler {

    /** Guard against duplicate registration on server reconnect. */
    private static boolean registered = false;

    // Input validation limits for incoming C2S packets
    private static final long   MAX_TRADE_QUANTITY = 100_000L;
    // a player can carry at most ~2700 items in vanilla, but allow some extra headroom for mods
    private static final long   MAX_SELL_QUANTITY  =  25_000L;
    private static final long   MAX_COIN_COUNT     =  10_000L;
    private static final int    MAX_ITEM_ID_LENGTH =     250;

    /**
     * Tracks the last lock set sent to each player.
     * Used to avoid re-sending the same payload every tick when nothing changed.
     */
    private static final Map<UUID, Set<Integer>> lastSentLocks = new HashMap<>();

    public static void register() {
        // Prevent double-registration if the server is restarted within the same JVM session
        if (registered) return;
        registered = true;

        // C2S types
        PayloadTypeRegistry.playC2S().register(MarketBuyPayload.TYPE,  MarketBuyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MarketSellPayload.TYPE, MarketSellPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WithdrawPayload.TYPE,   WithdrawPayload.CODEC);

        // S2C types
        PayloadTypeRegistry.playS2C().register(BalanceSyncPayload.TYPE,           BalanceSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TransactionHistoryPayload.TYPE,    TransactionHistoryPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AdvancementLockSyncPayload.TYPE,   AdvancementLockSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PriceConfigSyncPayload.TYPE,       PriceConfigSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MarketListingSyncPayload.TYPE,     MarketListingSyncPayload.CODEC);

        // Handle incoming buy packet on the server
        ServerPlayNetworking.registerGlobalReceiver(MarketBuyPayload.TYPE, (payload, context) -> {
            if (!isValidItemId(payload.itemId()) || !isValidQuantity(payload.quantity())) return;
            context.server().execute(() -> {
                MarketManager.get().buy(context.player(), payload.itemId(), payload.quantity(), context.server());
                broadcastListingUpdate(context.server(), payload.itemId());
            });
        });

        // Handle incoming sell packet on the server
        ServerPlayNetworking.registerGlobalReceiver(MarketSellPayload.TYPE, (payload, context) -> {
            if (!isValidItemId(payload.itemId()) || !isValidSellQuantity(payload.quantity())) return;
            context.server().execute(() -> {
                MarketManager.get().sell(context.player(), payload.itemId(), payload.quantity(), context.server());
                broadcastListingUpdate(context.server(), payload.itemId());
            });
        });

        // Handle withdraw — deduct balance, give physical coins, record history
        ServerPlayNetworking.registerGlobalReceiver(WithdrawPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                EconomyState economy = EconomyState.get(context.server());

                // Clamp each denomination to [0, MAX_COIN_COUNT] before multiplying
                // to prevent integer overflow from a maliciously crafted packet.
                long copper        = Math.max(0, Math.min(payload.copperCoins(),    MAX_COIN_COUNT));
                long copperSacks   = Math.max(0, Math.min(payload.copperSacks(),    MAX_COIN_COUNT));
                long silver        = Math.max(0, Math.min(payload.silverCoins(),    MAX_COIN_COUNT));
                long silverSacks   = Math.max(0, Math.min(payload.silverSacks(),    MAX_COIN_COUNT));
                long gold          = Math.max(0, Math.min(payload.goldCoins(),      MAX_COIN_COUNT));
                long goldSacks     = Math.max(0, Math.min(payload.goldSacks(),      MAX_COIN_COUNT));
                long platinum      = Math.max(0, Math.min(payload.platinumCoins(),  MAX_COIN_COUNT));
                long platinumSacks = Math.max(0, Math.min(payload.platinumSacks(),  MAX_COIN_COUNT));

                long totalCost =
                        copper        * JakesEconomyItems.VALUE_COPPER_COIN +
                        copperSacks   * JakesEconomyItems.VALUE_COPPER_COIN_SACK +
                        silver        * JakesEconomyItems.VALUE_SILVER_COIN +
                        silverSacks   * JakesEconomyItems.VALUE_SILVER_COIN_SACK +
                        gold          * JakesEconomyItems.VALUE_GOLD_COIN +
                        goldSacks     * JakesEconomyItems.VALUE_GOLD_COIN_SACK +
                        platinum      * JakesEconomyItems.VALUE_PLATINUM_COIN +
                        platinumSacks * JakesEconomyItems.VALUE_PLATINUM_COIN_SACK;

                if (totalCost <= 0) return;

                if (economy.getBalance(player.getUUID()) < totalCost) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Insufficient balance to withdraw."));
                    return;
                }

                economy.withdraw(player.getUUID(), totalCost);

                long totalCoins = copper + copperSacks + silver + silverSacks +
                                  gold   + goldSacks   + platinum + platinumSacks;

                giveCoins(player, JakesEconomyItems.COPPER_COIN,        copper);
                giveCoins(player, JakesEconomyItems.COPPER_COIN_SACK,   copperSacks);
                giveCoins(player, JakesEconomyItems.SILVER_COIN,        silver);
                giveCoins(player, JakesEconomyItems.SILVER_COIN_SACK,   silverSacks);
                giveCoins(player, JakesEconomyItems.GOLD_COIN,          gold);
                giveCoins(player, JakesEconomyItems.GOLD_COIN_SACK,     goldSacks);
                giveCoins(player, JakesEconomyItems.PLATINUM_COIN,      platinum);
                giveCoins(player, JakesEconomyItems.PLATINUM_COIN_SACK, platinumSacks);

                // Record in history and sync to client (no chat message — history panel shows it)
                economy.addTransaction(player.getUUID(), "WITHDRAW", "coins", totalCoins, totalCost);
                EconomyState.syncBalance(player, context.server());
                EconomyState.syncHistory(player, context.server());
            });
        });

        // Send initial lock state + price config + full listing when a player joins
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    syncLocks(handler.player, server);
                    ServerPlayNetworking.send(handler.player,
                            new PriceConfigSyncPayload(JakesEconomyConfigManager.serializePrices()));
                    // Full listing sync so prices and trends are correct from the moment the GUI opens
                    ServerPlayNetworking.send(handler.player,
                            new MarketListingSyncPayload(buildFullListingJson(server)));
                })
        );

        // Clean up cached lock state when a player leaves
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                lastSentLocks.remove(handler.player.getUUID())
        );

        // Re-sync lock state every 50 ticks (2.5 s) — only sends a packet when the
        // unlocked set has actually changed, so there is no bandwidth waste at idle.
        // Every 200 ticks (10 s) also broadcast a full listing sync so prices/trends
        // stay accurate even for players who aren't currently trading.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            boolean doListingSync = server.getTickCount() % 200 == 0;
            String fullListingJson = doListingSync ? buildFullListingJson(server) : null;

            if (server.getTickCount() % 50 == 0 || doListingSync) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (server.getTickCount() % 50 == 0) {
                        Set<Integer> current = computeUnlockedLocks(player, server);
                        if (!current.equals(lastSentLocks.get(player.getUUID()))) {
                            lastSentLocks.put(player.getUUID(), current);
                            ServerPlayNetworking.send(player, new AdvancementLockSyncPayload(current));
                        }
                    }
                    if (doListingSync) {
                        ServerPlayNetworking.send(player, new MarketListingSyncPayload(fullListingJson));
                    }
                }
            }
        });
    }

    /**
     * Computes which achievement lock IDs this player has unlocked and sends them
     * to the client immediately. Also updates the lastSentLocks cache.
     */
    public static void syncLocks(ServerPlayer player, MinecraftServer server) {
        Set<Integer> unlocked = computeUnlockedLocks(player, server);
        lastSentLocks.put(player.getUUID(), unlocked);
        ServerPlayNetworking.send(player, new AdvancementLockSyncPayload(unlocked));
    }

    /**
     * Computes the set of achievement lock IDs the player has satisfied,
     * without sending any packet. Used by both syncLocks() and the tick check.
     */
    private static Set<Integer> computeUnlockedLocks(ServerPlayer player, MinecraftServer server) {
        JakesEconomyPriceConfig prices = JakesEconomyConfigManager.getPrices();
        Set<Integer> unlocked = new HashSet<>();
        if (prices == null) return unlocked;

        for (Map.Entry<Integer, JakesEconomyPriceConfig.AchievementLockDef> entry
                : prices.achievementLocks.entrySet()) {
            JakesEconomyPriceConfig.AchievementLockDef lockDef = entry.getValue();
            if (lockDef.advancementId.isEmpty()) {
                unlocked.add(entry.getKey()); // no advancement required = always unlocked
                continue;
            }
            ResourceLocation advRl = ResourceLocation.tryParse(lockDef.advancementId);
            if (advRl == null) continue;
            net.minecraft.advancements.AdvancementHolder holder = server.getAdvancements().get(advRl);
            if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) {
                unlocked.add(entry.getKey());
            }
        }
        return unlocked;
    }

    private static void giveCoins(ServerPlayer player, net.minecraft.world.item.Item item, long quantity) {
        if (quantity <= 0) return;
        long remaining = quantity;
        int maxStack = item.getDefaultMaxStackSize();
        while (remaining > 0) {
            int stackSize = (int) Math.min(remaining, maxStack);
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, stackSize);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            remaining -= stackSize;
        }
    }

    /** Returns true if the item ID is a syntactically valid namespaced ResourceLocation. */
    private static boolean isValidItemId(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_ITEM_ID_LENGTH) return false;
        return id.contains(":") && ResourceLocation.tryParse(id) != null;
    }

    /** Returns true if the quantity is within the acceptable trade range [1, MAX_TRADE_QUANTITY]. */
    private static boolean isValidQuantity(long qty) {
        return qty >= 1 && qty <= MAX_TRADE_QUANTITY;
    }

    /** Returns true if the sell quantity is within the acceptable sell range [1, MAX_SELL_QUANTITY]. */
    private static boolean isValidSellQuantity(long qty) {
        return qty >= 1 && qty <= MAX_SELL_QUANTITY;
    }

    // ─── Listing sync helpers ─────────────────────────────────────────────────

    /**
     * Broadcasts a delta update (single item) of the live price + deficits to every
     * online player. Called immediately after each buy/sell trade commit so all
     * clients see the new price in real time.
     */
    private static void broadcastListingUpdate(MinecraftServer server, String itemId) {
        try {
            MarketListing listing = MarketManager.get().getListings().get(itemId);
            if (listing == null) return;
            var config = JakesEconomyConfigManager.getServer();
            double price = listing.getCurrentPrice(config.marketDepth, config.sensitivity);
            String json = buildSingleListingJson(itemId, listing, price);
            MarketListingSyncPayload payload = new MarketListingSyncPayload(json);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(player, payload);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Serialises the full live listing map to JSON so the joining player (or a
     * periodic refresh) gets accurate prices right away.
     */
    private static String buildFullListingJson(MinecraftServer server) {
        try {
            var config = JakesEconomyConfigManager.getServer();
            Map<String, MarketListing> listings = MarketManager.get().getListings();
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, MarketListing> e : listings.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                double price = e.getValue().getCurrentPrice(config.marketDepth, config.sensitivity);
                appendListingEntry(sb, e.getKey(), e.getValue(), price);
            }
            return sb.append('}').toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Builds a single-entry JSON string: {"itemId":{"p":...,"nd":...,"snd":...}} */
    private static String buildSingleListingJson(String itemId, MarketListing listing, double price) {
        StringBuilder sb = new StringBuilder("{");
        appendListingEntry(sb, itemId, listing, price);
        return sb.append('}').toString();
    }

    private static void appendListingEntry(StringBuilder sb, String itemId, MarketListing listing, double price) {
        sb.append('"').append(itemId.replace("\\", "\\\\").replace("\"", "\\\"")).append("\":{\"p\":")
          .append(price)
          .append(",\"nd\":").append(listing.netDeficit)
          .append(",\"snd\":").append(listing.snapshotDeficit).append('}');
    }

}