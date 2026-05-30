package com.github.devjake123.jakeseconomy.economy;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import com.github.devjake123.jakeseconomy.config.JakesEconomyConfigManager;
import com.github.devjake123.jakeseconomy.config.JakesEconomyPriceConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * * Manages all market transactions and price state.
 * * * This is the core of the economy system. It:
 * *   - Maintains a map of MarketListings (one per tradeable item)
 * *   - Processes instant buy/sell transactions
 * *   - Applies the logarithmic price curve
 * *   - Enforces per-player deficit rate limits (via EconomyState)
 * *   - Applies price decay on a scheduled tick
 * * All state that must survive restarts (netDeficit per item) is stored in
 * * EconomyState's NBT save data. MarketManager itself holds the in-memory listings
 * * map which is rebuilt from config + EconomyState on server start.
 * * Thread safety: All methods must be called from the server tick thread.
 * */
public class MarketManager {

    // Pseudo-item ID for experience point trading (not a real registry item)
    public static final String XP_ITEM_ID = "jakeseconomy:experience_point";

    // Internal technical safeguards against floating point overflow/underflow at extreme economies.
    // These are NOT gameplay limits — they exist purely to prevent NaN/Infinity values.
    static final double PRICE_FLOOR = 0.000001;
    static final double PRICE_CEILING = 1_000_000_000.0;


    // In-memory listing map: ResourceLocation string → MarketListing
    // Populated from config on server start, netDeficit values loaded from EconomyState NBT.
    private final Map<String, MarketListing> listings = new HashMap<>();

    // Singleton instance — one MarketManager per server lifecycle
    private static MarketManager instance;

    private MarketManager() {}

    /**     * Returns the active MarketManager instance.     * Call initialize() first on server start, then use get() everywhere else.     */
    public static MarketManager get() {
        if (instance == null) throw new IllegalStateException("MarketManager not initialized — call initialize() first");
        return instance;
    }

    /**     * Initializes the MarketManager on server start.     * Loads all item prices from config into memory, then restores netDeficit     * values from the persisted EconomyState NBT data.     *     * Called from JakesEconomy.onInitialize() via ServerLifecycleEvents.SERVER_STARTED.     */
    public static void initialize(MinecraftServer server) {
        instance = new MarketManager();
        JakesEconomyPriceConfig prices = JakesEconomyConfigManager.getPrices();

        // Build a listing for each configured item, skipping any unknown item IDs
        for (Map.Entry<String, JakesEconomyPriceConfig.ItemPrice> entry : prices.allItems().entrySet()) {
            net.minecraft.resources.ResourceLocation itemRl =
                    net.minecraft.resources.ResourceLocation.tryParse(entry.getKey());
            if (itemRl == null) {
                JakesEconomy.LOGGER.warn("MarketManager: skipping unparseable item id '{}' in price config.", entry.getKey());
                continue;
            }
            // Allow jakeseconomy: pseudo-items (e.g. experience_point) without registry validation
            boolean isModPseudo = itemRl.getNamespace().equals("jakeseconomy");
            if (!isModPseudo && !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(itemRl)) {
                JakesEconomy.LOGGER.warn("MarketManager: skipping unknown item '{}' in price config.", entry.getKey());
                continue;
            }
            JakesEconomyPriceConfig.ItemPrice price = entry.getValue();
            instance.listings.put(entry.getKey(), new MarketListing(price.basePrice, price.marketDepth));
        }

        // Restore netDeficit values from persisted save data
        EconomyState economy = EconomyState.get(server);
        economy.loadDeficitsInto(instance.listings);
        economy.loadSnapshotsInto(instance.listings);

        JakesEconomy.LOGGER.info("MarketManager initialized with {} tradeable items.", instance.listings.size());

    }


    // ----------------------------
    // Transaction methods
    // ----------------------------

    /** Processes an instant buy transaction.
     * * Deducts the current price × quantity from the player's virtual balance,
     * * gives the items directly to their inventory, and increases netDeficit.
     * * Fails if:
     * *   - The item has no listing (not tradeable)
     * *   - The player has insufficient balance
     * *   - The player has hit their deficit contribution limit for this window
     * */
    public void buy(ServerPlayer player, String itemId, long quantity, MinecraftServer server) {
        JakesEconomy.LOGGER.debug("[Market] {} attempting to buy {}x '{}'.",
                player.getName().getString(), quantity, itemId);
        MarketListing listing = listings.get(itemId);
        if (listing == null) {
            JakesEconomy.LOGGER.warn("[Market] Buy rejected — item '{}' has no listing (requested by {}).",
                    itemId, player.getName().getString());
            player.sendSystemMessage(Component.literal("That item is not available in the market."));
            return;
        }

        // Achievement lock check — block trade if the player hasn't unlocked this item's gate
        {
            JakesEconomyPriceConfig prices = JakesEconomyConfigManager.getPrices();
            JakesEconomyPriceConfig.ItemPrice itemPrice = prices.allItems().get(itemId);
            if (itemPrice != null && itemPrice.achievementLock > 0) {
                JakesEconomyPriceConfig.AchievementLockDef lockDef = prices.achievementLocks.get(itemPrice.achievementLock);
                if (lockDef != null && !lockDef.advancementId.isEmpty()) {
                    ResourceLocation advRl = ResourceLocation.tryParse(lockDef.advancementId);
                    if (advRl != null) {
                        net.minecraft.advancements.AdvancementHolder holder = server.getAdvancements().get(advRl);
                        if (holder != null && !player.getAdvancements().getOrStartProgress(holder).isDone()) {
                            player.sendSystemMessage(Component.literal(
                                    "Complete \"" + lockDef.displayName + "\" to trade this item."));
                            return;
                        }
                    }
                }
            }
        }

        var config = JakesEconomyConfigManager.getServer();
        EconomyState economy = EconomyState.get(server);

        // How many can this player buy given rate limit (MAX_VALUE if in surplus)
        long allowance = economy.getRemainingBuyAllowance(player.getUUID(), itemId, listing.netDeficit, config);
        if (allowance == 0) {
            long hours = (long) Math.ceil(config.deficitWindowHours);
            JakesEconomy.LOGGER.debug("[Market] {} hit rate limit buying '{}' — window resets in {} hour(s).",
                    player.getName().getString(), itemId, hours);
            player.sendSystemMessage(Component.literal(
                    "You've reached your market impact limit for " + friendlyName(itemId) +
                            ". Resets in " + hours + " hour(s)."));
            return;
        }

        long allowed = Math.min(quantity, allowance);
        boolean capped = allowed < quantity;

        // Per-unit pricing loop — price updates each unit as deficit increases
        long totalCost = 0;
        long actualBought = 0;
        long balance = economy.getBalance(player.getUUID());

        for (long i = 0; i < allowed; i++) {
            double price = listing.getCurrentPrice(config.marketDepth, config.sensitivity);
            long unitCost = (long) Math.ceil(price);
            if (balance - totalCost < unitCost) break; // can't afford next unit
            totalCost += unitCost;
            listing.netDeficit += 1;
            actualBought++;
        }

        // Undo the deficit changes we speculatively applied (will re-apply correctly)
        listing.netDeficit -= actualBought;

        if (actualBought == 0) {
            JakesEconomy.LOGGER.debug("[Market] {} cannot afford any '{}' (balance: {}, min unit cost: {}).",
                    player.getName().getString(), itemId, balance,
                    (long) Math.ceil(listing.getCurrentPrice(config.marketDepth, config.sensitivity)));
            player.sendSystemMessage(Component.literal(
                    "Insufficient funds. Need at least " +
                            CurrencyFormatter.format((long) Math.ceil(listing.getCurrentPrice(config.marketDepth, config.sensitivity)), true) +
                            " | Balance: " + CurrencyFormatter.format(balance, true)));
            return;
        }

        // Commit
        economy.withdraw(player.getUUID(), totalCost);
        giveItems(player, itemId, actualBought);
        listing.netDeficit += actualBought;
        economy.recordBuyContribution(player.getUUID(), itemId, actualBought);
        economy.saveDeficitsFrom(listings);

        // Warn in chat if the order was partially filled (user should know), but no success spam
        if (capped && actualBought == allowed) {
            player.sendSystemMessage(Component.literal(
                    "Rate limit: bought " + actualBought + "/" + quantity + "x " + friendlyName(itemId) + " this window."));
        } else if (actualBought < quantity) {
            player.sendSystemMessage(Component.literal(
                    "Bought " + actualBought + "/" + quantity + "x " + friendlyName(itemId) + " — insufficient funds for remainder."));
        }

        JakesEconomy.LOGGER.info("[Market] {} bought {}x '{}' for {} (deficit: {} → {}).",
                player.getName().getString(), actualBought, itemId, totalCost,
                listing.netDeficit - actualBought, listing.netDeficit);

        // Record in history and sync to client
        economy.addTransaction(player.getUUID(), "BUY", itemId, actualBought, totalCost);
        EconomyState.syncBalance(player, server);
        EconomyState.syncHistory(player, server);
    }

    /* * Processes an instant sell transaction.
     * * Takes the items from the player's inventory, credits the current price × quantity
     * * to their virtual balance, and decreases netDeficit (increases surplus).
     * * * Fails if:
     * *   - The item has no listing
     * *   - The player doesn't have enough of the item in their inventory
     * *   - The player has hit their deficit contribution limit for this window
     * */
    public void sell(ServerPlayer player, String itemId, long quantity, MinecraftServer server) {
        JakesEconomy.LOGGER.debug("[Market] {} attempting to sell {}x '{}'.",
                player.getName().getString(), quantity, itemId);
        MarketListing listing = listings.get(itemId);
        if (listing == null) {
            JakesEconomy.LOGGER.warn("[Market] Sell rejected — item '{}' has no listing (requested by {}).",
                    itemId, player.getName().getString());
            player.sendSystemMessage(Component.literal("That item is not sold in the market."));
            return;
        }

        // Achievement lock check — block trade if the player hasn't unlocked this item's gate
        {
            JakesEconomyPriceConfig prices = JakesEconomyConfigManager.getPrices();
            JakesEconomyPriceConfig.ItemPrice itemPrice = prices.allItems().get(itemId);
            if (itemPrice != null && itemPrice.achievementLock > 0) {
                JakesEconomyPriceConfig.AchievementLockDef lockDef = prices.achievementLocks.get(itemPrice.achievementLock);
                if (lockDef != null && !lockDef.advancementId.isEmpty()) {
                    ResourceLocation advRl = ResourceLocation.tryParse(lockDef.advancementId);
                    if (advRl != null) {
                        net.minecraft.advancements.AdvancementHolder holder = server.getAdvancements().get(advRl);
                        if (holder != null && !player.getAdvancements().getOrStartProgress(holder).isDone()) {
                            player.sendSystemMessage(Component.literal(
                                    "Complete \"" + lockDef.displayName + "\" to trade this item."));
                            return;
                        }
                    }
                }
            }
        }

        var config = JakesEconomyConfigManager.getServer();
        EconomyState economy = EconomyState.get(server);

        int available = countItems(player, itemId);
        if (available <= 0) {
            player.sendSystemMessage(Component.literal(
                    "You don't have any " + friendlyName(itemId) + " to sell."));
            return;
        }
        // Cap to what the player actually holds — allows partial fills on shift-click
        // (mirrors buy()'s behaviour of filling as much as the player can afford)
        if (available < quantity) {
            quantity = available;
        }

        // Per-unit pricing loop — mirrors buy() so buying and selling the same item is balanced.
        // Each unit is sold at the current price, then deficit decreases (price drops) for the next unit.
        long totalEarned = 0;
        for (long i = 0; i < quantity; i++) {
            double price = listing.getCurrentPrice(config.marketDepth, config.sensitivity);
            totalEarned += (long) Math.floor(price);
            listing.netDeficit -= 1;
        }
        // Undo speculative deficit changes before the commit below re-applies them correctly
        listing.netDeficit += quantity;

        takeItems(player, itemId, quantity);
        economy.deposit(player.getUUID(), totalEarned);

        listing.netDeficit -= quantity;
        economy.recordSellContribution(player.getUUID(), itemId, quantity);
        economy.saveDeficitsFrom(listings);

        // Record in history and sync to client (no success chat spam)
        economy.addTransaction(player.getUUID(), "SELL", itemId, quantity, totalEarned);
        JakesEconomy.LOGGER.info("[Market] {} sold {}x '{}' for {} (deficit: {} → {}).",
                player.getName().getString(), quantity, itemId, totalEarned,
                listing.netDeficit + quantity, listing.netDeficit);
        EconomyState.syncBalance(player, server);
        EconomyState.syncHistory(player, server);
    }

    // ----------------------------
    // Price management
    // ----------------------------

    /**
     * * Sets or updates the base price for an item.
     * * Called by /jakeseconomy market setprice.
     * * Also persists the change to jakeseconomy-prices.json via ConfigManager.
     * */
    public void setPrice(String itemId, double basePrice, double marketDepthOverride, String category, int achievementLock) {
        listings.put(itemId, new MarketListing(basePrice, marketDepthOverride));
        JakesEconomyPriceConfig prices = JakesEconomyConfigManager.getPrices();
        prices.categories.computeIfAbsent(category, k -> new java.util.LinkedHashMap<>())
                .put(itemId, new JakesEconomyPriceConfig.ItemPrice(basePrice, marketDepthOverride, achievementLock));
        JakesEconomyConfigManager.savePrices();
        JakesEconomy.LOGGER.info("[Market] Price set: '{}' → {} in category '{}' (lock {}).", itemId, basePrice, category, achievementLock);
    }

    /**
     * * Removes an item from the market entirely.
     * * Called by /jakeseconomy market removeprice.
     * */
    public void removePrice(String itemId) {
        listings.remove(itemId);
        JakesEconomyPriceConfig prices = JakesEconomyConfigManager.getPrices();
        for (Map<String, JakesEconomyPriceConfig.ItemPrice> cat : prices.categories.values()) {
            cat.remove(itemId);
        }
        JakesEconomyConfigManager.savePrices();
        JakesEconomy.LOGGER.info("[Market] '{}' removed from the market.", itemId);
    }

    /**
     * * Returns an unmodifiable view of all current listings.
     * * Used by the GUI to render the item list and prices.
     * */
    public Map<String, MarketListing> getListings() {
        return Collections.unmodifiableMap(listings);
    }

    /**
     * * Returns the current price for a single item, or -1 if not listed.
     * */
    public double getCurrentPrice(String itemId) {
        MarketListing listing = listings.get(itemId);
        if (listing == null) return -1;
        var config = JakesEconomyConfigManager.getServer();
        return listing.getCurrentPrice(config.marketDepth, config.sensitivity);
    }

    // ----------------------------
    // Trend Snapshots (called by TrendSnapshotScheduler)
    // ----------------------------

    /**
     * Captures the current netDeficit of every listing as the new trend baseline.
     * The trend arrow in the GUI will compare future netDeficit values against this snapshot,
     * so it shows whether prices have moved up or down over the last ~3 minutes.
     */
    public void updateTrendSnapshots(MinecraftServer server) {
        for (MarketListing listing : listings.values()) {
            listing.snapshotDeficit = listing.netDeficit;
        }
        EconomyState.get(server).saveSnapshotsFrom(listings);
        JakesEconomy.LOGGER.debug("Trend snapshots updated for {} listings.", listings.size());
    }

    // ----------------------------
    // Decay (called by PriceDecayScheduler)
    // ----------------------------

    /**
     * * Applies price decay to all listings.
     * * Each item's netDeficit moves closer to 0 by the configured decay rate,
     * * simulating natural market recovery when trading slows down.
     * * Only called if priceDecayEnabled = true in server config.
     * */
    public void applyDecay(MinecraftServer server) {
        double decayRate = JakesEconomyConfigManager.getServer().priceDecayRatePercent / 100.0;
        for (MarketListing listing : listings.values()) {
            // Decay toward 0: netDeficit shrinks by decayRate% each interval
            listing.netDeficit = (long)(listing.netDeficit * (1.0 - decayRate));
        }
        EconomyState.get(server).saveDeficitsFrom(listings);
        EconomyState.get(server).setDirty();
        JakesEconomy.LOGGER.info("Applied market price decay ({} listings affected).", listings.size());
    }

    // ----------------------------
    // Inventory helpers
    // ----------------------------

    /**
     * Returns a human-readable display name for any item ID, mirroring the
     * client-side ItemDisplayHelper so server chat messages show "Iron Ingot"
     * instead of "minecraft:iron_ingot".
     */
    public static String friendlyName(String itemId) {
        if (XP_ITEM_ID.equals(itemId)) return "Experience (XP)";
        try {
            net.minecraft.resources.ResourceLocation rl =
                    net.minecraft.resources.ResourceLocation.tryParse(itemId);
            if (rl != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl)) {
                return new net.minecraft.world.item.ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl))
                        .getHoverName().getString();
            }
        } catch (Exception ignored) {}
        // Fallback: capitalise the path and replace underscores
        String path = itemId.contains(":") ? itemId.split(":")[1] : itemId;
        path = path.replace("_", " ");
        return path.isEmpty() ? itemId : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    /**
     * Gives `quantity` of the specified item to the player's inventory.
     * Drops any overflow on the ground at the player's feet if inventory is full.
     */
    private void giveItems(ServerPlayer player, String itemId, long quantity) {
        // XP pseudo-item
        if (XP_ITEM_ID.equals(itemId)) {
            player.giveExperiencePoints((int) Math.min(quantity, Integer.MAX_VALUE));
            return;
        }
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (rl == null) return;
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        if (item == net.minecraft.world.item.Items.AIR) return;

        long remaining = quantity;
        int maxStack = item.getDefaultMaxStackSize();

        while (remaining > 0) {
            int stackSize = (int) Math.min(remaining, maxStack);
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, stackSize);
            // addItem returns the leftover — if inventory is full, drop it at player's feet
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= stackSize;
        }
    }

    /**
     * Removes `quantity` of the specified item from the player's inventory.
     * Iterates all slots and removes from each until the total is fulfilled.
     * Assumes countItems() was called first to confirm availability.
     */
    private void takeItems(ServerPlayer player, String itemId, long quantity) {
        // XP pseudo-item
        if (XP_ITEM_ID.equals(itemId)) {
            int toTake = (int) Math.min(quantity, Math.max(0, player.totalExperience));
            if (toTake > 0) player.giveExperiencePoints(-toTake);
            return;
        }
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (rl == null) return;
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        if (item == net.minecraft.world.item.Items.AIR) return;

        long remaining = quantity;
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();

        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            net.minecraft.world.item.ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                int take = (int) Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    /**
     * Counts how many of the specified item the player currently has across all inventory slots.
     * Used by sell() to verify the player has enough before proceeding.
     */
    /** Returns true if the given item ID is configured as a tradeable market item. */
    public boolean isMarketItem(String itemId) {
        return listings.containsKey(itemId);
    }

    private int countItems(ServerPlayer player, String itemId) {
        // XP pseudo-item
        if (XP_ITEM_ID.equals(itemId)) {
            return Math.max(0, player.totalExperience);
        }
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (rl == null) return 0;
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        if (item == net.minecraft.world.item.Items.AIR) return 0;

        int count = 0;
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

}
