package com.github.devjake123.testmod.init;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

public class JakesEconomyLootTables {

    // --- Loot table target lists ---
    // Each array lists the vanilla chest ResourceLocations to inject into.
    // These are matched against the loot table key during the MODIFY event.

    // Copper coins: widespread, many early/mid-game exploration chests
    private static final ResourceLocation[] COPPER_COIN_CHESTS = {
            ResourceLocation.withDefaultNamespace("chests/simple_dungeon"),
            ResourceLocation.withDefaultNamespace("chests/desert_pyramid"),
            ResourceLocation.withDefaultNamespace("chests/igloo_chest_content"),
            ResourceLocation.withDefaultNamespace("chests/jungle_temple"),
            ResourceLocation.withDefaultNamespace("chests/shipwreck_treasure"),
            ResourceLocation.withDefaultNamespace("chests/pillager_outpost"),
            ResourceLocation.withDefaultNamespace("chests/stronghold_corridor"),
            ResourceLocation.withDefaultNamespace("chests/woodland_mansion"),
            ResourceLocation.withDefaultNamespace("chests/trial_chambers/corridor_barrel"),
            ResourceLocation.withDefaultNamespace("chests/abandoned_mineshaft")
    };

    // Copper sacks: rarer, more dangerous/late-game chests
    private static final ResourceLocation[] COPPER_COIN_SACK_CHESTS = {
            ResourceLocation.withDefaultNamespace("chests/ancient_city"),
            ResourceLocation.withDefaultNamespace("chests/desert_pyramid"),
            ResourceLocation.withDefaultNamespace("chests/jungle_temple"),
            ResourceLocation.withDefaultNamespace("chests/pillager_outpost"),
            ResourceLocation.withDefaultNamespace("chests/shipwreck_treasure"),
            ResourceLocation.withDefaultNamespace("chests/stronghold_corridor"),
            ResourceLocation.withDefaultNamespace("chests/stronghold_crossing"),
            ResourceLocation.withDefaultNamespace("chests/end_city_treasure"),
            ResourceLocation.withDefaultNamespace("chests/trial_chambers/corridor"),
            ResourceLocation.withDefaultNamespace("chests/trial_chambers/reward_common")
    };

    // Silver coins: very rare, only end-game / hidden locations
    private static final ResourceLocation[] SILVER_COIN_CHESTS = {
            ResourceLocation.withDefaultNamespace("chests/ancient_city_ice_box"),
            ResourceLocation.withDefaultNamespace("chests/buried_treasure"),
            ResourceLocation.withDefaultNamespace("chests/trial_chambers/reward_ominous")
    };

    // Silver sacks: ominous trial vaults only
    private static final ResourceLocation[] SILVER_COIN_SACK_CHESTS = {
            ResourceLocation.withDefaultNamespace("chests/trial_chambers/reward_ominous")
    };

    // Gold coins: unrealistic to find naturally
    private static final ResourceLocation[] GOLD_COIN_CHESTS = {
            ResourceLocation.withDefaultNamespace("chests/trial_chambers/reward_ominous"),
            ResourceLocation.withDefaultNamespace("chests/bastion_treasure")
    };

    /**
     * Registers the loot table modification event.
     * Called once during mod initialization in JakesEconomy.onInitialize().
     */
    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            ResourceLocation id = key.location();

            // Village chests: matched by namespace prefix so ALL village chest variants
            // (weaponsmith, toolsmith, butcher, etc.) are covered without listing each one.
            // Also catches any mod that adds new chests under minecraft:chests/village/*.
            if (id.getNamespace().equals("minecraft") && id.getPath().startsWith("chests/village/")) {
                tableBuilder.withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(JakesEconomyItems.COPPER_COIN)
                                .setWeight(1)
                                .apply(net.minecraft.world.level.storage.loot.functions.SetItemCountFunction
                                        .setCount(net.minecraft.world.level.storage.loot.providers.number.UniformGenerator.between(1, 3))))
                        .when(LootItemRandomChanceCondition.randomChance(0.20f)));
            }

            // Copper coins: ~20% chance of 1-3
            for (ResourceLocation chest : COPPER_COIN_CHESTS) {
                if (chest.equals(id)) {
                    tableBuilder.withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(JakesEconomyItems.COPPER_COIN)
                                    .setWeight(1)
                                    .apply(net.minecraft.world.level.storage.loot.functions.SetItemCountFunction
                                            .setCount(net.minecraft.world.level.storage.loot.providers.number.UniformGenerator.between(1, 3))))
                            .when(LootItemRandomChanceCondition.randomChance(0.20f)));
                    break;
                }
            }

            // Copper sacks: ~8% chance
            for (ResourceLocation chest : COPPER_COIN_SACK_CHESTS) {
                if (chest.equals(id)) {
                    tableBuilder.withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(JakesEconomyItems.COPPER_COIN_SACK).setWeight(1))
                            .when(LootItemRandomChanceCondition.randomChance(0.08f)));
                    break;
                }
            }

            // Silver coins: ~5% chance
            for (ResourceLocation chest : SILVER_COIN_CHESTS) {
                if (chest.equals(id)) {
                    tableBuilder.withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(JakesEconomyItems.SILVER_COIN).setWeight(1))
                            .when(LootItemRandomChanceCondition.randomChance(0.05f)));
                    break;
                }
            }

            // Silver sacks: ~8% chance of 1 — ominous trial only
            for (ResourceLocation chest : SILVER_COIN_SACK_CHESTS) {
                if (chest.equals(id)) {
                    tableBuilder.withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(JakesEconomyItems.SILVER_COIN_SACK).setWeight(1))
                            .when(LootItemRandomChanceCondition.randomChance(0.08f)));
                    break;
                }
            }

            // Gold coins: ~1% chance
            for (ResourceLocation chest : GOLD_COIN_CHESTS) {
                if (chest.equals(id)) {
                    tableBuilder.withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(JakesEconomyItems.GOLD_COIN).setWeight(1))
                            .when(LootItemRandomChanceCondition.randomChance(0.01f)));
                    break;
                }
            }
        });
    }
}