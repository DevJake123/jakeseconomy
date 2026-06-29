package com.github.devjake123.jakeseconomy.command;

import com.github.devjake123.jakeseconomy.economy.CurrencyFormatter;
import com.github.devjake123.jakeseconomy.economy.EconomyState;
import com.github.devjake123.jakeseconomy.JakesEconomy;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.github.devjake123.jakeseconomy.economy.MarketManager;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.github.devjake123.jakeseconomy.config.JakesEconomyConfigManager;
import com.github.devjake123.jakeseconomy.config.JakesEconomyPriceConfig;
import com.github.devjake123.jakeseconomy.config.JakesEconomyServerConfig;
import com.github.devjake123.jakeseconomy.network.PriceConfigSyncPayload;
import com.github.devjake123.jakeseconomy.network.OpenScreenPayload;

import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Registers all economy commands using Brigadier (Minecraft's command framework).
 *
 * Permission levels for every command group are read from the server config
 * (jakeseconomy-server.json → "permissions") so server/world creators can
 * tighten or relax access without touching the mod. Defaults match the old
 * hard-coded behaviour (ops = level 2, players = level 0).
 *
 * Available commands:
 *   /jecon balance               — Check your own virtual balance
 *   /jecon balance <player>      — Check another player's balance (perm: balanceOther)
 *   /jecon give <player> <amt>   — Add currency to a player (perm: give)
 *   /jecon set <player> <amt>    — Set a player's balance (perm: set)
 *   /jecon take <player> <amt>   — Remove currency from a player (perm: take)
 *   /jecon market open           — Open the market GUI (perm: marketOpen)
 *   /jecon market setprice …     — Manage market listings (perm: marketAdmin)
 *   /jecon auction open          — Open the auction GUI (perm: auctionOpen)
 *   /balance                     — Shortcut for /jecon balance
 */
public class JakesEconomyCommands {

    // ─── Permission helper ────────────────────────────────────────────────────

    /**
     * Returns a Brigadier predicate that reads the required permission level
     * for a command group from the live server config each time it is evaluated.
     * This means a server reload automatically picks up config changes.
     */
    private static Predicate<net.minecraft.commands.CommandSourceStack> perm(
            ToIntFunction<JakesEconomyServerConfig.Permissions> getter) {
        return src -> {
            JakesEconomyServerConfig cfg = JakesEconomyConfigManager.getServer();
            int level = (cfg == null || cfg.permissions == null) ? 2 : getter.applyAsInt(cfg.permissions);
            return src.hasPermission(level);
        };
    }

    // ─── Registration ─────────────────────────────────────────────────────────

    public static void register() {

        // ── Main /jecon tree ──────────────────────────────────────────────────
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("jecon")

                // /jecon balance — any player checks own balance
                .then(Commands.literal("balance")
                    .executes(ctx -> {
                        ServerPlayer player = (ServerPlayer) ctx.getSource().getEntityOrException();
                        long balance = EconomyState.get(ctx.getSource().getServer()).getBalance(player.getUUID());
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "Your balance: " + CurrencyFormatter.format(balance, true)), false);
                        return 1;
                    })
                    // /jecon balance <player>
                    .then(Commands.argument("player", EntityArgument.player())
                        .requires(perm(p -> p.balanceOther))
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            long balance = EconomyState.get(ctx.getSource().getServer()).getBalance(target.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    target.getName().getString() + "'s balance: " +
                                    CurrencyFormatter.format(balance, true)), false);
                            return 1;
                        })))

                // /jecon give <player> <amount>
                .then(Commands.literal("give")
                    .requires(perm(p -> p.give))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                long amount = LongArgumentType.getLong(ctx, "amount");
                                EconomyState state = EconomyState.get(ctx.getSource().getServer());
                                state.deposit(target.getUUID(), amount);
                                EconomyState.syncBalance(target, ctx.getSource().getServer());
                                JakesEconomy.LOGGER.info("[Admin] {} gave {} to {}.",
                                        ctx.getSource().getTextName(),
                                        CurrencyFormatter.format(amount, false),
                                        target.getName().getString());
                                long newBal = state.getBalance(target.getUUID());
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Gave " + CurrencyFormatter.format(amount, true) +
                                        " to " + target.getName().getString() +
                                        ". New balance: " + CurrencyFormatter.format(newBal, true)), false);
                                return 1;
                            }))))

                // /jecon set <player> <amount>
                .then(Commands.literal("set")
                    .requires(perm(p -> p.set))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                long amount = LongArgumentType.getLong(ctx, "amount");
                                EconomyState state = EconomyState.get(ctx.getSource().getServer());
                                state.setBalance(target.getUUID(), amount);
                                EconomyState.syncBalance(target, ctx.getSource().getServer());
                                JakesEconomy.LOGGER.info("[Admin] {} set {}'s balance to {}.",
                                        ctx.getSource().getTextName(),
                                        target.getName().getString(),
                                        CurrencyFormatter.format(amount, false));
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Set " + target.getName().getString() + "'s balance to " +
                                        CurrencyFormatter.format(amount, true)), false);
                                return 1;
                            }))))

                // /jecon take <player> <amount>
                .then(Commands.literal("take")
                    .requires(perm(p -> p.take))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                long amount = LongArgumentType.getLong(ctx, "amount");
                                EconomyState state = EconomyState.get(ctx.getSource().getServer());
                                boolean success = state.withdraw(target.getUUID(), amount);
                                if (success) {
                                    EconomyState.syncBalance(target, ctx.getSource().getServer());
                                    JakesEconomy.LOGGER.info("[Admin] {} took {} from {}.",
                                            ctx.getSource().getTextName(),
                                            CurrencyFormatter.format(amount, false),
                                            target.getName().getString());
                                    long newBal = state.getBalance(target.getUUID());
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Took " + CurrencyFormatter.format(amount, true) +
                                            " from " + target.getName().getString() +
                                            ". New balance: " + CurrencyFormatter.format(newBal, true)), false);
                                } else {
                                    long current = state.getBalance(target.getUUID());
                                    ctx.getSource().sendFailure(Component.literal(
                                            target.getName().getString() + " only has " +
                                            CurrencyFormatter.format(current, true) +
                                            " — could not take " + CurrencyFormatter.format(amount, true) + "."));
                                }
                                return success ? 1 : 0;
                            }))))

                // /jecon market …
                .then(Commands.literal("market")

                    // /jecon market open
                    .then(Commands.literal("open")
                        .requires(perm(p -> p.marketOpen))
                        .executes(ctx -> execOpenMarket(ctx.getSource().getPlayerOrException())))

                    // /jecon market setprice <item> <price> <category> [achievementLock]
                    .then(Commands.literal("setprice")
                        .requires(perm(p -> p.marketAdmin))
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                            .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                                .then(Commands.argument("category", StringArgumentType.string())
                                    .executes(ctx -> {
                                        ResourceLocation item = ResourceLocationArgument.getId(ctx, "item");
                                        double price = DoubleArgumentType.getDouble(ctx, "price");
                                        String category = StringArgumentType.getString(ctx, "category");
                                        if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(item)) {
                                            ctx.getSource().sendFailure(Component.literal(
                                                    "Unknown item: " + item + " — not found in item registry."));
                                            return 0;
                                        }
                                        MarketManager.get().setPrice(item.toString(), price, -1, category, 0);
                                        JakesEconomy.LOGGER.info("[Admin] {} set price for '{}' to {} in category '{}'.",
                                                ctx.getSource().getTextName(), item, price, category);
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "Set market price for " + MarketManager.friendlyName(item.toString()) + " to " +
                                                CurrencyFormatter.format((long) price, true) +
                                                " in category [" + category + "]"), true);
                                        return 1;
                                    })
                                    .then(Commands.argument("achievementLock", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            ResourceLocation item = ResourceLocationArgument.getId(ctx, "item");
                                            double price = DoubleArgumentType.getDouble(ctx, "price");
                                            String category = StringArgumentType.getString(ctx, "category");
                                            int lock = IntegerArgumentType.getInteger(ctx, "achievementLock");
                                            if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(item)) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "Unknown item: " + item + " — not found in item registry."));
                                                return 0;
                                            }
                                            MarketManager.get().setPrice(item.toString(), price, -1, category, lock);
                                            JakesEconomy.LOGGER.info("[Admin] {} set price for '{}' to {} in category '{}' with lock {}.",
                                                    ctx.getSource().getTextName(), item, price, category, lock);
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Set market price for " + MarketManager.friendlyName(item.toString()) + " to " +
                                                    CurrencyFormatter.format((long) price, true) +
                                                    " in category [" + category + "]" +
                                                    (lock > 0 ? " (lock " + lock + ")" : "")), true);
                                            return 1;
                                        }))))))

                    // /jecon market addcategory <name>
                    .then(Commands.literal("addcategory")
                        .requires(perm(p -> p.marketAdmin))
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
                                JakesEconomy.LOGGER.info("[Admin] {} created market category '{}'.",
                                        ctx.getSource().getTextName(), name);
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Created market category: " + name), true);
                                return 1;
                            })))

                    // /jecon market removeprice <item>
                    .then(Commands.literal("removeprice")
                        .requires(perm(p -> p.marketAdmin))
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                            .executes(ctx -> {
                                ResourceLocation item = ResourceLocationArgument.getId(ctx, "item");
                                MarketManager.get().removePrice(item.toString());
                                JakesEconomy.LOGGER.info("[Admin] {} removed '{}' from the market.",
                                        ctx.getSource().getTextName(), item);
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Removed " + MarketManager.friendlyName(item.toString()) + " from the market."), true);
                                return 1;
                            })))

                    // /jecon market setlock <item> <lockId>
                    .then(Commands.literal("setlock")
                        .requires(perm(p -> p.marketAdmin))
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                            .then(Commands.argument("lockId", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    ResourceLocation item = ResourceLocationArgument.getId(ctx, "item");
                                    int lockId = IntegerArgumentType.getInteger(ctx, "lockId");
                                    JakesEconomyPriceConfig prices = JakesEconomyConfigManager.getPrices();
                                    JakesEconomyPriceConfig.ItemPrice found = prices.categories.values().stream()
                                            .map(cat -> cat.get(item.toString()))
                                            .filter(ip -> ip != null)
                                            .findFirst().orElse(null);
                                    if (found == null) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                item + " is not listed in the market. Use /jecon market setprice first."));
                                        return 0;
                                    }
                                    found.achievementLock = lockId;
                                    JakesEconomyConfigManager.savePrices();
                                    String configJson = JakesEconomyConfigManager.serializePrices();
                                    for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                        ServerPlayNetworking.send(p, new PriceConfigSyncPayload(configJson));
                                    }
                                    JakesEconomy.LOGGER.info("[Admin] {} set lock {} on '{}'.",
                                            ctx.getSource().getTextName(), lockId, item);
                                    final int finalLockId = lockId;
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Set achievement lock " + finalLockId + " on " +
                                            MarketManager.friendlyName(item.toString()) +
                                            (finalLockId == 0 ? " (lock removed)" : "")), true);
                                    return 1;
                                }))))

                    // /jecon market price <item>
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
                                            MarketManager.friendlyName(item.toString()) + " current price: " +
                                            CurrencyFormatter.format((long) price, true)), false);
                                }
                                return 1;
                            }))))

                // /jecon auction …
                .then(Commands.literal("auction")
                    .then(Commands.literal("open")
                        .requires(perm(p -> p.auctionOpen))
                        .executes(ctx -> execOpenAuction(ctx.getSource().getPlayerOrException()))))

            ) // close dispatcher.register(jecon)
        ); // close EVENT.register

        // ── /balance shortcut ─────────────────────────────────────────────────
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            if (dispatcher.getRoot().getChild("balance") != null) {
                JakesEconomy.LOGGER.info("[Commands] /balance is already registered by another mod — skipping shortcut.");
                return;
            }
            dispatcher.register(Commands.literal("balance")
                    .executes(ctx -> {
                        ServerPlayer player = (ServerPlayer) ctx.getSource().getEntityOrException();
                        long balance = EconomyState.get(ctx.getSource().getServer()).getBalance(player.getUUID());
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "Balance: " + CurrencyFormatter.format(balance, true) +
                                "  (" + CurrencyFormatter.format(balance, false) + ")"), false);
                        return 1;
                    }));
            JakesEconomy.LOGGER.info("[Commands] Registered /balance shortcut.");
        });

        // ── /jecon debug fillhistory ──────────────────────────────────────────
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("jecon")
                .then(Commands.literal("debug")
                    .requires(perm(p -> p.debug))
                    .then(Commands.literal("fillhistory")
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                            .executes(ctx -> execFillHistory(
                                    ctx.getSource().getServer(),
                                    ResourceLocationArgument.getId(ctx, "item"),
                                    720,
                                    ctx.getSource()))
                            .then(Commands.argument("hours", IntegerArgumentType.integer(1, 720))
                                .executes(ctx -> execFillHistory(
                                        ctx.getSource().getServer(),
                                        ResourceLocationArgument.getId(ctx, "item"),
                                        IntegerArgumentType.getInteger(ctx, "hours"),
                                        ctx.getSource()))))))));
    }

    // ─── Command implementations ──────────────────────────────────────────────

    private static int execOpenMarket(ServerPlayer player) {
        ServerPlayNetworking.send(player, new OpenScreenPayload("market"));
        return 1;
    }

    private static int execOpenAuction(ServerPlayer player) {
        ServerPlayNetworking.send(player, new OpenScreenPayload("auction"));
        return 1;
    }

    private static int execFillHistory(net.minecraft.server.MinecraftServer server,
                                       ResourceLocation item,
                                       int hours,
                                       net.minecraft.commands.CommandSourceStack source) {
        double basePrice = MarketManager.get().getCurrentPrice(item.toString());
        if (basePrice <= 0) {
            source.sendFailure(Component.literal(item + " is not a listed market item."));
            return 0;
        }
        long now = System.currentTimeMillis();
        java.util.Random rng = new java.util.Random(0xC0FFEEL);

        java.util.List<com.github.devjake123.jakeseconomy.economy.PricePoint> archivePoints =
                new java.util.ArrayList<>(hours);
        for (int i = 0; i < hours; i++) {
            long   ts     = now - (long)(hours - i) * 3_600_000L;
            double weekly = Math.sin(i * 2 * Math.PI / 168.0) * 0.20;
            double daily  = Math.sin(i * 2 * Math.PI /  24.0) * 0.10;
            double noise  = (rng.nextDouble() - 0.5)           * 0.08;
            double price  = Math.max(1.0, basePrice * (1.0 + weekly + daily + noise));
            archivePoints.add(new com.github.devjake123.jakeseconomy.economy.PricePoint(ts, price));
        }
        EconomyState.get(server).injectPriceHistory(item.toString(), archivePoints);

        int recentCount = 72;
        java.util.List<com.github.devjake123.jakeseconomy.economy.PricePoint> recentPoints =
                new java.util.ArrayList<>(recentCount);
        java.util.Random rng2 = new java.util.Random(0xBEEFL);
        for (int i = 0; i < recentCount; i++) {
            long   ts    = now - (long)(recentCount - i) * 1_200_000L;
            double cycle = Math.sin(i * 2 * Math.PI / 72.0) * 0.12;
            double noise = (rng2.nextDouble() - 0.5)         * 0.05;
            double price = Math.max(1.0, basePrice * (1.0 + cycle + noise));
            recentPoints.add(new com.github.devjake123.jakeseconomy.economy.PricePoint(ts, price));
        }
        EconomyState.get(server).injectRecentPriceHistory(item.toString(), recentPoints);

        final int finalHours = hours;
        source.sendSuccess(() -> Component.literal(
                "[Debug] Injected " + finalHours + "h archive + 24h recent (20-min) test data for "
                + MarketManager.friendlyName(item.toString()) + ". Open the graph to verify."), true);
        return 1;
    }
}

