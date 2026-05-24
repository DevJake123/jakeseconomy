package com.github.devjake123.testmod.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Price sheet config loaded from jakeseconomy-prices.json.
 *
 * Structure:
 *   categories: map of category name  (item id ItemPrice)
 *
 * Each top-level key in "categories" becomes a tab in the market GUI.
 * Items belong to the tab they are listed under.
 * Invalid item IDs are skipped with a warning logged on server start.
 *
 * Example:
 * {
 *   "categories": {
 *     "Minerals": {
 *       "minecraft:diamond": { "basePrice": 5000.0, "marketDepth": -1 }
 *     },
 *     "AE2": {
 *       "ae2:certus_quartz": { "basePrice": 800.0, "marketDepth": -1 }
 *     }
 *   }
 * }
 */
public class JakesEconomyPriceConfig {

    // Outer key = category/tab name (e.g. "Minerals")
    // Inner key = item ResourceLocation string (e.g. "minecraft:diamond")
    // LinkedHashMap preserves insertion order so tabs appear in config file order
    public Map<String, Map<String, ItemPrice>> categories = new LinkedHashMap<>();

    /**
     * Maps an integer lock ID lock definition.
     * 0 = no lock (default). Built-in:
     *   1 = Nether (advancement: minecraft:story/enter_the_nether)
     *   2 = The End (advancement: minecraft:story/enter_the_end)
     * Admins can add 3, 4, 5 for custom advancement gates.
     */
    public Map<Integer, AchievementLockDef> achievementLocks = new LinkedHashMap<>();

    public static class AchievementLockDef {
        /** Resource location of the advancement that must be completed, e.g. "minecraft:story/enter_the_nether" */
        public String advancementId = "";
        /** Human-readable name shown in the market UI, e.g. "The Nether" */
        public String displayName = "";

        public AchievementLockDef() {}
        public AchievementLockDef(String advancementId, String displayName) {
            this.advancementId = advancementId;
            this.displayName   = displayName;
        }
    }

    public static class ItemPrice {
        // The base price when netDeficit = 0
        public double basePrice = 100.0;

        // Per-item marketDepth override. -1 = use global value from server config.
        public double marketDepth = -1;

        /**
         * Achievement lock gate. 0 = no lock.
         * Matches a key in achievementLocks; the player must have that advancement to trade this item.
         */
        public int achievementLock = 0;

        public ItemPrice() {}

        public ItemPrice(double basePrice, double marketDepth) {
            this.basePrice   = basePrice;
            this.marketDepth = marketDepth;
        }

        public ItemPrice(double basePrice, double marketDepth, int achievementLock) {
            this.basePrice       = basePrice;
            this.marketDepth     = marketDepth;
            this.achievementLock = achievementLock;
        }
    }

    /**
     * Returns a flat map of all itemId ItemPrice across all categories.
     * Used by MarketManager which doesn't need to know which tab an item belongs to.
     */
    public Map<String, ItemPrice> allItems() {
        if (categories == null) return new LinkedHashMap<>();
        Map<String, ItemPrice> flat = new LinkedHashMap<>();
        for (Map<String, ItemPrice> items : categories.values()) {
            if (items != null) flat.putAll(items);
        }
        return flat;
    }

    public static JakesEconomyPriceConfig createDefault() {
        JakesEconomyPriceConfig config = new JakesEconomyPriceConfig();

        // Achievement locks
        // Lock IDs 1 and 2 are built-in. Add 3, 4, 5 as needed.
        config.achievementLocks.put(1, new AchievementLockDef("minecraft:story/enter_the_nether", "The Nether"));
        config.achievementLocks.put(2, new AchievementLockDef("minecraft:story/enter_the_end",    "The End"));
        config.achievementLocks.put(3, new AchievementLockDef("minecraft:nether/summon_wither", "Withering Heights"));
        config.achievementLocks.put(4, new AchievementLockDef("minecraft:adventure/hero_of_the_village", "Hero of the Village"));
        config.achievementLocks.put(5, new AchievementLockDef("minecraft:nether/revaulting", "Revaulting"));


        // marketDepth guide (per-item override):
        //   40000   = effectively infinite supply (cobblestone, netherrack, bamboo)
        //   15000    = mass-farmable but not infinite (logs, crops, common mob drops)
        //   5000    = moderate effort (iron, gold, ender pearls, guardian drops)
        //   1000     = meaningful rarity (diamonds, emeralds, rare drops)
        //   500     = hard to get at scale (ancient debris, echo shards, wither skulls)
        //   300       = ultra-rare, a handful of trades spikes the price (nether star, EGA)

        //  Minerals
        Map<String, ItemPrice> minerals = new LinkedHashMap<>();
        minerals.put("minecraft:coal",           new ItemPrice(15.0,    50_000));
        minerals.put("minecraft:charcoal",       new ItemPrice(15.0,    50_000));
        minerals.put("minecraft:copper_ingot",   new ItemPrice(10.0,    20_000));
        minerals.put("minecraft:iron_ingot",     new ItemPrice(20.0,     8_000));
        minerals.put("minecraft:gold_ingot",     new ItemPrice(60.0,     6_000));
        minerals.put("minecraft:redstone",       new ItemPrice(20.0,    20_000));
        minerals.put("minecraft:lapis_lazuli",   new ItemPrice(25.0,    15_000));
        minerals.put("minecraft:quartz",         new ItemPrice(25.0,    15_000, 1));  // nether
        minerals.put("minecraft:glowstone_dust", new ItemPrice(50.0,    10_000, 1));  // nether
        minerals.put("minecraft:amethyst_shard", new ItemPrice(80.0,     5_000));
        minerals.put("minecraft:echo_shard",     new ItemPrice(2_000.0,    500));
        minerals.put("minecraft:diamond",        new ItemPrice(3_000.0,  3_000));
        minerals.put("minecraft:emerald",        new ItemPrice(100.0,    3_000));
        minerals.put("minecraft:netherite_scrap",new ItemPrice(20_000.0,   800, 1));  // nether
        minerals.put("minecraft:netherite_ingot",new ItemPrice(70_000.0,   500, 1));  // nether
        config.categories.put("Minerals", minerals);

        // Food
        Map<String, ItemPrice> food = new LinkedHashMap<>();
        food.put("minecraft:wheat",              new ItemPrice(5.0,  5_000));
        food.put("minecraft:carrot",             new ItemPrice(5.0,  5_000));
        food.put("minecraft:potato",             new ItemPrice(5.0,  5_000));
        food.put("minecraft:beetroot",           new ItemPrice(5.0,  5_000));
        food.put("minecraft:sugar_cane",         new ItemPrice(4.0,  8_000));
        food.put("minecraft:pumpkin",            new ItemPrice(10.0, 3_000));
        food.put("minecraft:melon_slice",        new ItemPrice(4.0,  5_000));
        food.put("minecraft:nether_wart",        new ItemPrice(15.0, 2_000, 1));  // nether
        food.put("minecraft:sweet_berries",      new ItemPrice(6.0,  4_000));
        food.put("minecraft:glow_berries",       new ItemPrice(20.0, 1_000));
        food.put("minecraft:cocoa_beans",        new ItemPrice(10.0, 3_000));
        food.put("minecraft:honey_bottle",       new ItemPrice(60.0,  800));
        food.put("minecraft:brown_mushroom",     new ItemPrice(8.0,  3_000));
        food.put("minecraft:red_mushroom",       new ItemPrice(8.0,  3_000));
        food.put("minecraft:chorus_fruit",       new ItemPrice(30.0, 1_000, 2));  // end
        food.put("minecraft:bread",              new ItemPrice(18.0, 3_000));
        food.put("minecraft:pumpkin_pie",        new ItemPrice(30.0, 1_000));
        food.put("minecraft:cake",               new ItemPrice(90.0,  500));
        food.put("minecraft:cookie",             new ItemPrice(8.0,  3_000));
        food.put("minecraft:mushroom_stew",      new ItemPrice(45.0, 1_000));
        food.put("minecraft:rabbit_stew",        new ItemPrice(60.0,  600));
        food.put("minecraft:beef",               new ItemPrice(15.0, 2_000));
        food.put("minecraft:cooked_beef",        new ItemPrice(18.0, 1_000));
        food.put("minecraft:porkchop",           new ItemPrice(12.0, 2_000));
        food.put("minecraft:cooked_porkchop",    new ItemPrice(15.0, 1_000));
        food.put("minecraft:chicken",            new ItemPrice(10.0, 2_000));
        food.put("minecraft:cooked_chicken",     new ItemPrice(13.0, 1_000));
        food.put("minecraft:mutton",             new ItemPrice(10.0, 2_000));
        food.put("minecraft:cooked_mutton",      new ItemPrice(13.0, 1_000));
        food.put("minecraft:rabbit",             new ItemPrice(15.0, 1_000));
        food.put("minecraft:cooked_rabbit",      new ItemPrice(18.0, 1_000));
        food.put("minecraft:cod",                new ItemPrice(10.0, 3_000));
        food.put("minecraft:salmon",             new ItemPrice(12.0, 2_000));
        food.put("minecraft:tropical_fish",      new ItemPrice(40.0,  500));
        food.put("minecraft:pufferfish",         new ItemPrice(50.0,  500));
        food.put("minecraft:cooked_cod",         new ItemPrice(13.0, 2_000));
        food.put("minecraft:cooked_salmon",      new ItemPrice(15.0, 1_000));
        food.put("minecraft:golden_carrot",      new ItemPrice(400.0,  2_000));
        food.put("minecraft:golden_apple",       new ItemPrice(5_000.0,  800));
        food.put("minecraft:enchanted_golden_apple", new ItemPrice(100_000.0, 100));  // = 1 gold coin
        config.categories.put("Food", food);

        // Mob Drops
        Map<String, ItemPrice> mobDrops = new LinkedHashMap<>();
        mobDrops.put("minecraft:string",               new ItemPrice(10.0,    3_000));
        mobDrops.put("minecraft:feather",              new ItemPrice(8.0,     3_000));
        mobDrops.put("minecraft:leather",              new ItemPrice(30.0,    1_000));
        mobDrops.put("minecraft:bone",                 new ItemPrice(8.0,     4_000));
        mobDrops.put("minecraft:rotten_flesh",         new ItemPrice(3.0,     8_000));
        mobDrops.put("minecraft:spider_eye",           new ItemPrice(15.0,    2_000));
        mobDrops.put("minecraft:gunpowder",            new ItemPrice(25.0,    1_000));
        mobDrops.put("minecraft:slime_ball",           new ItemPrice(20.0,    1_000));
        mobDrops.put("minecraft:ink_sac",              new ItemPrice(15.0,    2_000));
        mobDrops.put("minecraft:glow_ink_sac",         new ItemPrice(60.0,     1_000));
        mobDrops.put("minecraft:rabbit_foot",          new ItemPrice(150.0,    1_000));
        mobDrops.put("minecraft:rabbit_hide",          new ItemPrice(10.0,    2_000));
        mobDrops.put("minecraft:prismarine_shard",     new ItemPrice(30.0,     1_000));
        mobDrops.put("minecraft:prismarine_crystals",  new ItemPrice(50.0,     1_000));
        mobDrops.put("minecraft:phantom_membrane",     new ItemPrice(200.0,    3_000));
        mobDrops.put("minecraft:turtle_scute",         new ItemPrice(300.0,    2_000));
        mobDrops.put("minecraft:ender_pearl",          new ItemPrice(150.0,    3_000));
        mobDrops.put("minecraft:ender_eye",            new ItemPrice(300.0,    3_000));
        mobDrops.put("minecraft:blaze_rod",            new ItemPrice(100.0,    4_000, 1));  // nether
        mobDrops.put("minecraft:magma_cream",          new ItemPrice(45.0,    1_000, 1));  // nether
        mobDrops.put("minecraft:ghast_tear",           new ItemPrice(400.0,    2_000, 1));  // nether
        mobDrops.put("minecraft:wither_skeleton_skull",new ItemPrice(4_000.0,  1_000, 1));  // nether
        mobDrops.put("minecraft:nether_star",          new ItemPrice(150_000.0,  300, 3));  // nether
        mobDrops.put("minecraft:shulker_shell",        new ItemPrice(600.0,    2_000, 2));  // end
        mobDrops.put("minecraft:dragon_breath",        new ItemPrice(1_500.0,  1_000, 2));  // end
        mobDrops.put("minecraft:elytra",               new ItemPrice(50_000.0,   500, 2));  // end
        mobDrops.put("minecraft:totem_of_undying",     new ItemPrice(2_000.0,    800, 4));
        mobDrops.put("minecraft:wind_charge",          new ItemPrice(60.0,    1_000, 5));
        mobDrops.put("minecraft:breeze_rod",           new ItemPrice(500.0,     3_000, 5));
        mobDrops.put("minecraft:heavy_core",           new ItemPrice(100_000.0,   50, 5));
        config.categories.put("Mob Drops", mobDrops);

        // Blocks
        Map<String, ItemPrice> blocks = new LinkedHashMap<>();
        blocks.put("minecraft:cobblestone",     new ItemPrice(1.0,   10_000));
        blocks.put("minecraft:gravel",          new ItemPrice(3.0,   10_000));
        blocks.put("minecraft:sand",            new ItemPrice(3.0,   10_000));
        blocks.put("minecraft:red_sand",        new ItemPrice(5.0,    5_000));
        blocks.put("minecraft:netherrack",      new ItemPrice(1.0,   10_000, 1));  // nether
        blocks.put("minecraft:end_stone",       new ItemPrice(3.0,   10_000, 2));  // end
        blocks.put("minecraft:deepslate",       new ItemPrice(5.0,    5_000));
        blocks.put("minecraft:tuff",            new ItemPrice(4.0,    5_000));
        blocks.put("minecraft:calcite",         new ItemPrice(6.0,    3_000));
        blocks.put("minecraft:dripstone_block", new ItemPrice(10.0,   2_000));
        blocks.put("minecraft:basalt",          new ItemPrice(5.0,    5_000, 1));  // nether
        blocks.put("minecraft:blackstone",      new ItemPrice(8.0,    3_000, 1));  // nether
        blocks.put("minecraft:soul_sand",       new ItemPrice(15.0,   2_000, 1));  // nether
        blocks.put("minecraft:soul_soil",       new ItemPrice(12.0,   2_000, 1));  // nether
        blocks.put("minecraft:magma_block",     new ItemPrice(6.0,    3_000, 1));  // nether
        blocks.put("minecraft:obsidian",        new ItemPrice(50.0,   1_000));
        blocks.put("minecraft:crying_obsidian", new ItemPrice(500.0,   400, 1));  // nether
        blocks.put("minecraft:prismarine",      new ItemPrice(80.0,    1_000));
        blocks.put("minecraft:prismarine_bricks",new ItemPrice(120.0,  1_000));
        blocks.put("minecraft:dark_prismarine", new ItemPrice(160.0,   1_000));
        blocks.put("minecraft:sea_lantern",     new ItemPrice(140.0,   1_000));
        config.categories.put("Blocks", blocks);

        // Wood
        Map<String, ItemPrice> wood = new LinkedHashMap<>();
        wood.put("minecraft:oak_log",       new ItemPrice(8.0,  8_000));
        wood.put("minecraft:spruce_log",    new ItemPrice(8.0,  8_000));
        wood.put("minecraft:birch_log",     new ItemPrice(8.0,  8_000));
        wood.put("minecraft:jungle_log",    new ItemPrice(10.0, 6_000));
        wood.put("minecraft:acacia_log",    new ItemPrice(8.0,  8_000));
        wood.put("minecraft:dark_oak_log",  new ItemPrice(9.0,  6_000));
        wood.put("minecraft:cherry_log",    new ItemPrice(12.0, 4_000));
        wood.put("minecraft:mangrove_log",  new ItemPrice(10.0, 6_000));
        wood.put("minecraft:bamboo",        new ItemPrice(2.0,  50_000));
        wood.put("minecraft:crimson_stem",  new ItemPrice(15.0,  3_000, 1));  // nether
        wood.put("minecraft:warped_stem",   new ItemPrice(15.0,  3_000, 1));  // nether
        config.categories.put("Wood", wood);

        // Misc
        Map<String, ItemPrice> misc = new LinkedHashMap<>();
        misc.put("minecraft:stick",            new ItemPrice(2.0,   15_000));
        misc.put("minecraft:paper",            new ItemPrice(4.0,    8_000));
        misc.put("minecraft:book",             new ItemPrice(15.0,   2_000));
        misc.put("minecraft:glass",            new ItemPrice(4.0,    8_000));
        misc.put("minecraft:glass_bottle",     new ItemPrice(6.0,    6_000));
        misc.put("minecraft:flint",            new ItemPrice(6.0,    5_000));
        misc.put("minecraft:clay_ball",        new ItemPrice(5.0,    4_000));
        misc.put("minecraft:snowball",         new ItemPrice(1.0,   10_000));
        misc.put("minecraft:white_wool",       new ItemPrice(10.0,   3_000));
        misc.put("minecraft:popped_chorus_fruit", new ItemPrice(60.0, 5_000, 2));  // end
        misc.put("minecraft:tnt",              new ItemPrice(150.0,   1_000));
        misc.put("minecraft:fire_charge",      new ItemPrice(25.0,   1_000, 1));  // needs blaze rod
        misc.put("minecraft:name_tag",         new ItemPrice(600.0,   2_000));
        misc.put("minecraft:saddle",           new ItemPrice(300.0,   3_000));
        misc.put("minecraft:experience_bottle",new ItemPrice(200.0,   3_000));
        misc.put("minecraft:nautilus_shell",   new ItemPrice(300.0,   2_000));
        misc.put("minecraft:heart_of_the_sea", new ItemPrice(2_000.0,   500));
        misc.put("minecraft:sponge",           new ItemPrice(150.0,   3_000));
        misc.put("minecraft:wet_sponge",       new ItemPrice(120.0,   4_000));
        misc.put("minecraft:sea_pickle",       new ItemPrice(20.0,   2_000));
        misc.put("minecraft:kelp",             new ItemPrice(3.0,   12_000));
        misc.put("minecraft:dandelion",        new ItemPrice(3.0,    8_000));
        misc.put("minecraft:poppy",            new ItemPrice(3.0,    8_000));
        misc.put("minecraft:blue_orchid",      new ItemPrice(3.0,    6_000));
        misc.put("minecraft:allium",           new ItemPrice(3.0,    6_000));
        misc.put("minecraft:azure_bluet",      new ItemPrice(3.0,    6_000));
        misc.put("minecraft:red_tulip",        new ItemPrice(3.0,    6_000));
        misc.put("minecraft:orange_tulip",     new ItemPrice(3.0,    6_000));
        misc.put("minecraft:white_tulip",      new ItemPrice(3.0,    6_000));
        misc.put("minecraft:pink_tulip",       new ItemPrice(3.0,    6_000));
        misc.put("minecraft:oxeye_daisy",      new ItemPrice(3.0,    6_000));
        misc.put("minecraft:cornflower",       new ItemPrice(3.0,    6_000));
        misc.put("minecraft:lily_of_the_valley",new ItemPrice(3.0,   6_000));
        misc.put("minecraft:sunflower",        new ItemPrice(5.0,    4_000));
        misc.put("minecraft:lilac",            new ItemPrice(5.0,    4_000));
        misc.put("minecraft:rose_bush",        new ItemPrice(5.0,    4_000));
        misc.put("minecraft:peony",            new ItemPrice(5.0,    4_000));
        misc.put("minecraft:wither_rose",      new ItemPrice(200.0,    5_000, 3));
        misc.put("minecraft:torchflower",      new ItemPrice(250.0,   2_000));
        misc.put("minecraft:pitcher_plant",    new ItemPrice(300.0,   2_000));
        misc.put("jakeseconomy:experience_point", new ItemPrice(5.0,  5_000));
        config.categories.put("Misc", misc);

        return config;
    }
}
