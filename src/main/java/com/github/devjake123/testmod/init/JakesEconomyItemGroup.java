package com.github.devjake123.testmod.init;

import com.github.devjake123.testmod.JakesEconomy;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Registers the "Jake's Economy" creative mode tab.
 *
 * The tab automatically displays every item registered under the jakeseconomy namespace,
 * so new items added in future layers will appear here without any manual changes.
 * The copper coin is used as the tab icon.
 *
 * Translation key for the tab title: "itemGroup.jakeseconomy.economy" (defined in en_us.json)
 */
public class JakesEconomyItemGroup {

    // The registered creative tab instance.
    // Static initialisation registers it into Minecraft's registry when load() is called.
    public static final CreativeModeTab ECONOMY_ITEM_GROUP =
            Registry.register(
                    BuiltInRegistries.CREATIVE_MODE_TAB,
                    JakesEconomy.id("economy"),
                    FabricItemGroup.builder()
                            .title(Component.translatable("itemGroup.jakeseconomy.economy"))
                            // Icon shown on the tab button in the creative inventory
                            .icon(() -> new ItemStack(JakesEconomyItems.COPPER_COIN))
                            // Automatically include every item registered under the jakeseconomy namespace.
                            // This means new items appear here as soon as they are registered,
                            // without needing to manually add them to this list.
                            .displayItems((parameters, output) -> BuiltInRegistries.ITEM.keySet()
                                    .stream()
                                    .filter(key -> key.getNamespace().equals(JakesEconomy.MOD_ID))
                                    .map(key -> BuiltInRegistries.ITEM.get(key))
                                    .forEach(item -> output.accept(new ItemStack(item)))
                            )
                            .build()
            );

    /**
     * Called in JakesEconomy.onInitialize() to trigger static field initialization,
     * registering the creative tab. The empty body is intentional — see JakesEconomyItems.load()
     * for a full explanation of this pattern.
     */
    public static void load() {}
}