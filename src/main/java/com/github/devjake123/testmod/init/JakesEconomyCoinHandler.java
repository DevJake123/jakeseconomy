package com.github.devjake123.testmod.init;

import com.github.devjake123.testmod.economy.CurrencyFormatter;
import com.github.devjake123.testmod.economy.EconomyState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public class JakesEconomyCoinHandler {

    // Maps each coin/sack item to its virtual currency value.
    // Used to quickly look up how much balance to credit when a player right-clicks a coin.
    // Map.of() is immutable and supports up to 10 entries (we have exactly 8).
    private static final Map<Item, Long> COIN_VALUES = Map.of(
            JakesEconomyItems.COPPER_COIN,        JakesEconomyItems.VALUE_COPPER_COIN,
            JakesEconomyItems.COPPER_COIN_SACK,   JakesEconomyItems.VALUE_COPPER_COIN_SACK,
            JakesEconomyItems.SILVER_COIN,        JakesEconomyItems.VALUE_SILVER_COIN,
            JakesEconomyItems.SILVER_COIN_SACK,   JakesEconomyItems.VALUE_SILVER_COIN_SACK,
            JakesEconomyItems.GOLD_COIN,          JakesEconomyItems.VALUE_GOLD_COIN,
            JakesEconomyItems.GOLD_COIN_SACK,     JakesEconomyItems.VALUE_GOLD_COIN_SACK,
            JakesEconomyItems.PLATINUM_COIN,      JakesEconomyItems.VALUE_PLATINUM_COIN,
            JakesEconomyItems.PLATINUM_COIN_SACK, JakesEconomyItems.VALUE_PLATINUM_COIN_SACK
    );

    /**
     * Registers the UseItemCallback event listener.
     * Called once during mod initialization in JakesEconomy.onInitialize().
     */
    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            // Skip client-side — this logic only runs on the server
            if (world.isClientSide()) return InteractionResultHolder.pass(player.getItemInHand(hand));

            ItemStack stack = player.getItemInHand(hand);

            // Check if the held item is a coin/sack — null means it's not one of ours
            Long value = COIN_VALUES.get(stack.getItem());

            if (value != null) {
                MinecraftServer server = player.getServer();
                // Server should never be null server-side, but guard defensively
                if (server == null) return InteractionResultHolder.pass(stack);

                EconomyState economy = EconomyState.get(server);

                if (player.isCrouching()) {
                    // Shift+right-click: deposit the entire stack at once
                    long totalValue = value * stack.getCount();
                    economy.deposit(player.getUUID(), totalValue);
                    player.sendSystemMessage(Component.literal("Deposited " +
                            CurrencyFormatter.format(totalValue, true) + " coins"));
                    stack.shrink(stack.getCount()); // consume whole stack
                } else {
                    // Normal right-click: deposit one coin/sack
                    economy.deposit(player.getUUID(), value);
                    player.sendSystemMessage(Component.literal("Deposited " +
                            CurrencyFormatter.format(value, true) + " coins"));
                    stack.shrink(1);
                }

                // Sync updated balance to client so the market GUI reflects it immediately
                if (player instanceof ServerPlayer serverPlayer) {
                    EconomyState.syncBalance(serverPlayer, server);
                }

                // SUCCESS tells Minecraft the interaction was handled — plays hand swing animation
                // and prevents the item from being used for anything else (e.g. placing blocks)
                return InteractionResultHolder.success(stack);
            }

            // Not a coin — let Minecraft handle the interaction normally
            return InteractionResultHolder.pass(stack);
        });
    }

}