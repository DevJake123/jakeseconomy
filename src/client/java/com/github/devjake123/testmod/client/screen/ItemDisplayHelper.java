package com.github.devjake123.testmod.client.screen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side utility for getting a human-readable display name from an item registry ID.
 * Uses the item's localized hover name (e.g. "minecraft:iron_ingot" → "Iron Ingot").
 */
public final class ItemDisplayHelper {

    private ItemDisplayHelper() {}

    /**
     * Returns the localized display name of the item with the given registry ID.
     * Falls back to a formatted version of the path if the item is unknown.
     */
    public static String getDisplayName(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "Unknown";
        // Pseudo-items that have no registry entry
        if ("jakeseconomy:experience_point".equals(itemId)) return "Experience (XP)";
        try {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                return new ItemStack(BuiltInRegistries.ITEM.get(rl)).getHoverName().getString();
            }
        } catch (Exception ignored) {}
        // Fallback: capitalise the path and replace underscores
        String path = itemId.contains(":") ? itemId.split(":")[1] : itemId;
        path = path.replace("_", " ");
        return path.isEmpty() ? itemId : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }
}

