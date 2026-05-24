package com.github.devjake123.testmod.command;

import com.github.devjake123.testmod.economy.CurrencyFormatter;
import com.github.devjake123.testmod.economy.EconomyState;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.github.devjake123.testmod.economy.MarketManager;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.github.devjake123.testmod.config.JakesEconomyConfigManager;
import com.github.devjake123.testmod.config.JakesEconomyPriceConfig;

/**
 * Registers all /jakeseconomy commands using Brigadier (Minecraft's command framework).
 *
 * Available commands:
 *   /jakeseconomy balance               — Check your own virtual balance
 *   /jakeseconomy balance <player>      — (Op) Check another player's balance
 *   /jakeseconomy give <player> <amt>   — (Op) Add currency to a player's balance
 *   /jakeseconomy set <player> <amt>    — (Op) Set a player's balance to an exact amount
 *   /jakeseconomy take <player> <amt>   — (Op) Remove currency from a player's balance
 *
 * Op commands require permission level 2 (operator).
 * All amounts and balances are displayed using CurrencyFormatter (abbreviated by default).
 *
 */
public class JakesEconomyCommands {

    /**
     * Registers all commands with Brigadier via Fabric's CommandRegistrationCallback.
     * Called once during mod initialization in JakesEconomy.onInitialize().
     */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("jakeseconomy")

                // /jakeseconomy balance — any player can check their own balance
                .then(Commands.literal("balance")
                        .executes(ctx -> {
                            ServerPlayer player = (ServerPlayer) ctx.getSource().getEntityOrException();
                            long balance = EconomyState.get(ctx.getSource().getServer()).getBalance(player.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Your balance: " + CurrencyFormatter.format(balance, true)), false);
                            return 1;
                        })
                        // /jakeseconomy balance <player> — op only, check someone else's balance
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    long balance = EconomyState.get(ctx.getSource().getServer()).getBalance(target.getUUID());
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            target.getName().getString() + "'s balance: " +
                                            CurrencyFormatter.format(balance, true)), false);
                                    return 1;
                                })))

                // /jakeseconomy give <player> <amount> — op: add currency to a player
                .then(Commands.literal("give")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1)) // minimum 1
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            long amount = LongArgumentType.getLong(ctx, "amount");
                                            EconomyState.get(ctx.getSource().getServer()).deposit(target.getUUID(), amount);
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Gave " + CurrencyFormatter.format(amount, true) +
                                                    " to " + target.getName().getString()), false);
                                            return 1;
                                        }))))

                // /jakeseconomy set <player> <amount> — op: set a player's balance exactly
                .then(Commands.literal("set")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(0)) // minimum 0 (allow zeroing)
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            long amount = LongArgumentType.getLong(ctx, "amount");
                                            EconomyState.get(ctx.getSource().getServer()).setBalance(target.getUUID(), amount);
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Set " + target.getName().getString() + "'s balance to " +
                                                    CurrencyFormatter.format(amount, true)), false);
                                            return 1;
                                        }))))

                // /jakeseconomy take <player> <amount> — op: remove currency from a player
                // Note: withdraw() returns false if insufficient funds — the balance floors at 0,
                // it won't go negative. Consider adding feedback for that case in a future pass.
                .then(Commands.literal("take")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1)) // minimum 1
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            long amount = LongArgumentType.getLong(ctx, "amount");
                                            EconomyState.get(ctx.getSource().getServer()).withdraw(target.getUUID(), amount);
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Took " + CurrencyFormatter.format(amount, true) +
                                                    " from " + target.getName().getString()), false);
                                            return 1;
                                        }))))

                .then(Commands.literal("market")
                        .requires(src -> src.hasPermission(2))

                        // /jakeseconomy market setprice <item> <price> <category>
                        // Adds or updates an item in the market under the given category tab.
                        // Category is created automatically if it doesn't exist.
                        // Skips if the item ID is not a valid registered item.
                        .then(Commands.literal("setprice")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                                                .then(Commands.argument("category", StringArgumentType.string())
                                                        .executes(ctx -> {
                                                            ResourceLocation item = ResourceLocationArgument.getId(ctx, "item");
                                                            double price = DoubleArgumentType.getDouble(ctx, "price");
                                                            String category = StringArgumentType.getString(ctx, "category");

                                                            // Sanity check: item must exist in the registry
                                                            if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(item)) {
                                                                ctx.getSource().sendFailure(Component.literal(
                                                                        "Unknown item: " + item + " — not found in item registry."));
                                                                return 0;
                                                            }

                                                             MarketManager.get().setPrice(item.toString(), price, -1, category);
                                                             ctx.getSource().sendSuccess(() -> Component.literal(
                                                                     "Set market price for " + MarketManager.friendlyName(item.toString()) + " to " +
                                                                             CurrencyFormatter.format((long) price, true) +
                                                                             " in category [" + category + "]"), true);
                                                            return 1;
                                                        })))))

                        // /jakeseconomy market addcategory <name>
                        // Creates an empty category tab in the price config.
                        // Useful to pre-create a tab before adding items to it.
                        .then(Commands.literal("addcategory")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            JakesEconomyPriceConfig prices = JakesEconomyConfigManager.getPrices();
                                            if (prices.categories.containsKey(name)) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "Category '" + name + "' already exists."));
                                                return 0;
                                            }
                                            prices.categories.put(name, new java.util.LinkedHashMap<>());
                                            JakesEconomyConfigManager.savePrices();
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Created market category: " + name), true);
                                            return 1;
                                        })))

                        // /jakeseconomy market removeprice <item>
                        // Removes an item from whichever category it belongs to.
                        .then(Commands.literal("removeprice")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .executes(ctx -> {
                                            ResourceLocation item = ResourceLocationArgument.getId(ctx, "item");
                                             MarketManager.get().removePrice(item.toString());
                                             ctx.getSource().sendSuccess(() -> Component.literal(
                                                     "Removed " + MarketManager.friendlyName(item.toString()) + " from the market."), true);
                                            return 1;
                                        })))

                        // /jakeseconomy market price <item>
                        // Shows the current market price for an item (accounts for netDeficit).
                        .then(Commands.literal("price")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .executes(ctx -> {
                                            ResourceLocation item = ResourceLocationArgument.getId(ctx, "item");
                                            double price = MarketManager.get().getCurrentPrice(item.toString());
                                             if (price < 0) {
                                                 ctx.getSource().sendSuccess(() -> Component.literal(
                                                         MarketManager.friendlyName(item.toString()) + " is not listed in the market."), false);
                                             } else {
                                                 ctx.getSource().sendSuccess(() -> Component.literal(
                                                         MarketManager.friendlyName(item.toString()) + " current price: " + CurrencyFormatter.format((long) price, true)), false);
                                             }
                                            return 1;
                                        }))))
        ));
    }
}
