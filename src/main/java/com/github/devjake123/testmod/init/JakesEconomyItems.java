package com.github.devjake123.testmod.init;

import com.github.devjake123.testmod.JakesEconomy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.world.item.Item;

/**
 * Registers all physical currency items for Jake's Economy.
 *
 * The currency ladder (each tier is 9x the previous):
 *   Copper Coin (10) → Copper Sack (90) → Silver Coin (1,000) → Silver Sack (9,000)
 *   → Gold Coin (100,000) → Gold Sack (900,000) → Platinum Coin (10,000,000) → Platinum Sack (90,000,000)
 *
 * Physical coins are obtained via loot chests or withdrawing
 * Right-clicking any coin/sack converts it to virtual balance in the player's account.
 * Coins can be withdrawn from the virtual balance via the market GUI.
 *
 * Copper/Silver coins and sacks can be found in chests. Gold+ are craft-only.
 */
public class JakesEconomyItems {

    // --- Physical coin items ---
    // Each registered with a unique ResourceLocation under the jakeseconomy namespace.
    // Item.Properties() is left default — coins have no special durability, food, or tool behaviour.

    public static final Item COPPER_COIN      = register("copper_coin",      new Item(new Item.Properties()));
    public static final Item SILVER_COIN      = register("silver_coin",      new Item(new Item.Properties()));
    public static final Item GOLD_COIN        = register("gold_coin",        new Item(new Item.Properties()));
    public static final Item PLATINUM_COIN    = register("platinum_coin",    new Item(new Item.Properties()));

    // --- Compressed sack items ---
    // Each sack holds 9 of the previous denomination's coins (9 copper coins = 1 copper sack, etc.)
    // Crafted via shapeless 9-into-1 recipe. Used for convenient large-value deposits.

    public static final Item COPPER_COIN_SACK   = register("copper_coin_sack",   new Item(new Item.Properties()));
    public static final Item SILVER_COIN_SACK   = register("silver_coin_sack",   new Item(new Item.Properties()));
    public static final Item GOLD_COIN_SACK     = register("gold_coin_sack",     new Item(new Item.Properties()));
    public static final Item PLATINUM_COIN_SACK = register("platinum_coin_sack", new Item(new Item.Properties()));

    // --- Virtual currency values (in currency units) ---
    // These are the exact amounts credited to a player's virtual balance when depositing each item.
    // All values are configurable in jakeseconomy-prices.json (these are the hardcoded defaults
    // used by the coin handler — coin items themselves are not in the price sheet).

    public static final long VALUE_COPPER_COIN      = 10L;
    public static final long VALUE_COPPER_COIN_SACK = 90L;         // 9 × copper coin
    public static final long VALUE_SILVER_COIN      = 1_000L;
    public static final long VALUE_SILVER_COIN_SACK = 9_000L;      // 9 × silver coin
    public static final long VALUE_GOLD_COIN        = 100_000L;
    public static final long VALUE_GOLD_COIN_SACK   = 900_000L;    // 9 × gold coin
    public static final long VALUE_PLATINUM_COIN    = 10_000_000L;
    public static final long VALUE_PLATINUM_COIN_SACK = 90_000_000L; // 9 × platinum coin

    /**
     * Registers an item into Minecraft's item registry under the jakeseconomy namespace.
     * Generic so it returns the exact subtype passed in, allowing subclasses to be stored
     * in typed fields without casting.
     */
    public static <T extends Item> T register(String name, T item) {
        return Registry.register(BuiltInRegistries.ITEM, JakesEconomy.id(name), item);
    }

    /**
     * Called in JakesEconomy.onInitialize() to trigger static field initialization,
     * which registers all items. The empty body is intentional — Java initialises
     * static fields when the class is first loaded, so calling this method is enough
     * to ensure all register() calls above have run before anything else references them.
     */
    public static void load() {}
}