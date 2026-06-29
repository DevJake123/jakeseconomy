package com.github.devjake123.jakeseconomy.config;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class JakesEconomyConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static JakesEconomyServerConfig serverConfig = new JakesEconomyServerConfig();
    private static JakesEconomyClientConfig clientConfig = new JakesEconomyClientConfig();
    private static JakesEconomyPriceConfig  priceConfig  = new JakesEconomyPriceConfig();

    // Resolved path to the game's config/ directory — set on first load
    private static Path configDir;

    // Called in JakesEconomy.onInitialize() via ServerLifecycleEvents.SERVER_STARTED.
    // Loads server-side configs (server settings + price sheet).
    // On first launch, generates defaults and sets priceDecayEnabled based on
    // whether this is a singleplayer or dedicated server world.
    public static void loadServer(MinecraftServer server) {
        configDir = server.getServerDirectory().resolve("config");

        serverConfig = loadOrCreate("jakeseconomy-server.json", JakesEconomyServerConfig.class, () -> {
            JakesEconomyServerConfig defaults = new JakesEconomyServerConfig();
            // Singleplayer: decay ON. Dedicated server: decay OFF.
            defaults.priceDecayEnabled = server.isSingleplayer();
            return defaults;
        });
        // Backfill any fields that Gson left at 0 because they were added after the
        // config file was first created (Gson's Unsafe instantiation skips initializers).
        serverConfig.mergeDefaults();
        // Always re-save with comments so the file stays up-to-date with new fields.
        saveServerCommented("jakeseconomy-server.json", serverConfig);

        priceConfig = loadOrCreate("jakeseconomy-prices.json", JakesEconomyPriceConfig.class,
                JakesEconomyPriceConfig::createDefault);
    }

    // Called in JakesEconomyClient.onInitializeClient().
    // Loads client-side config (keybind, display preferences).
    // Uses the game directory since this runs before a server is available.
    public static void loadClient(Path gameDir) {
        configDir = gameDir.resolve("config");
        clientConfig = loadOrCreate("jakeseconomy-client.json", JakesEconomyClientConfig.class,
                JakesEconomyClientConfig::new);
    }

    public static void saveServer() { saveServerCommented("jakeseconomy-server.json", serverConfig); }
    public static void savePrices() { save("jakeseconomy-prices.json", priceConfig); }
    public static void saveClient() { save("jakeseconomy-client.json", clientConfig); }

    public static JakesEconomyServerConfig getServer() { return serverConfig; }
    public static JakesEconomyClientConfig getClient() { return clientConfig; }
    public static JakesEconomyPriceConfig  getPrices()  { return priceConfig; }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private static <T> T loadOrCreate(String filename, Class<T> type, Supplier<T> defaults) {
        Path path = configDir.resolve(filename);
        if (Files.exists(path)) {
            try {
                // Strip // comments before handing the text to Gson
                String raw      = Files.readString(path);
                String stripped = stripComments(raw);
                T loaded = GSON.fromJson(stripped, type);
                if (loaded != null) {
                    JakesEconomy.LOGGER.info("Loaded {}", filename);
                    return loaded;
                }
                JakesEconomy.LOGGER.warn("{} parsed as null (empty file?), regenerating defaults.", filename);
            } catch (IOException e) {
                JakesEconomy.LOGGER.error("Failed to read {}, using defaults.", filename, e);
            }
        }
        // File doesn't exist, failed to read, or parsed as null — generate and save defaults
        T instance = defaults.get();
        save(filename, instance);
        JakesEconomy.LOGGER.info("Generated default {}", filename);
        return instance;
    }

    /**
     * Strips // comments from a JSON string so Gson can parse it.
     * Handles whole-line comments (line starts with //) and inline trailing
     * comments (// text after a value), while ignoring // inside string literals.
     */
    private static String stripComments(String content) {
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n", -1)) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("//")) continue;   // skip whole-line comment
            int commentIdx = findInlineComment(line);
            sb.append(commentIdx >= 0 ? line.substring(0, commentIdx) : line).append('\n');
        }
        return sb.toString();
    }

    /** Returns the index of the first // that is NOT inside a quoted string, or -1. */
    private static int findInlineComment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length() - 1; i++) {
            char c = line.charAt(i);
            if (c == '\\' && inString) { i++; continue; } // skip escaped char
            if (c == '"') inString = !inString;
            if (!inString && c == '/' && line.charAt(i + 1) == '/') return i;
        }
        return -1;
    }

    /** Generic atomic save using Gson (no comments). */
    private static void save(String filename, Object data) {
        if (configDir == null) return;
        Path path = configDir.resolve(filename);
        Path tmp  = configDir.resolve(filename + ".tmp");
        try {
            Files.createDirectories(configDir);
            try (Writer w = Files.newBufferedWriter(tmp)) { GSON.toJson(data, w); }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            JakesEconomy.LOGGER.error("Failed to save {}", filename, e);
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    /**
     * Saves the server config as commented JSON.
     * Comments use // syntax; the reader pre-strips them before Gson parsing.
     */
    private static void saveServerCommented(String filename, JakesEconomyServerConfig c) {
        if (configDir == null) return;
        Path path = configDir.resolve(filename);
        Path tmp  = configDir.resolve(filename + ".tmp");
        try {
            Files.createDirectories(configDir);
            Files.writeString(tmp, buildCommentedServerJson(c));
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            JakesEconomy.LOGGER.error("Failed to save {}", filename, e);
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    // ─── Commented JSON builder ───────────────────────────────────────────────

    private static String buildCommentedServerJson(JakesEconomyServerConfig c) {
        StringBuilder s = new StringBuilder("{\n");
        JakesEconomyServerConfig.Permissions p =
                c.permissions != null ? c.permissions : new JakesEconomyServerConfig.Permissions();

        // ── Market Settings ───────────────────────────────────────────────────
        cmt(s, "── Market Settings ─────────────────────────────────────────────────────");
        cmt(s, "Allow players to propose unlisted items for the market (not yet implemented).");
        kv (s, "allowModdedItems",        c.allowModdedItems);
        cmt(s, "If allowModdedItems is true, require op approval before items become tradeable.");
        kv (s, "requireOpApproval",        c.requireOpApproval);
        s.append('\n');

        // ── Price Decay ───────────────────────────────────────────────────────
        cmt(s, "── Price Decay ──────────────────────────────────────────────────────────");
        cmt(s, "Gradually recover prices back toward base value over time.");
        cmt(s, "Recommended ON for singleplayer, OFF for dedicated servers.");
        kv (s, "priceDecayEnabled",        c.priceDecayEnabled);
        cmt(s, "How much the deficit shrinks per tick as a percent (5.0 = 5% per interval).");
        kv (s, "priceDecayRatePercent",     c.priceDecayRatePercent);
        cmt(s, "Hours of real server uptime between each decay tick.");
        kv (s, "priceDecayIntervalHours",   c.priceDecayIntervalHours);
        s.append('\n');

        // ── Price Formula ─────────────────────────────────────────────────────
        cmt(s, "── Price Formula ────────────────────────────────────────────────────────");
        cmt(s, "Trade volume needed before prices shift meaningfully. Higher = more stable.");
        cmt(s, "  Vanilla/light: 500-2000 | Default: 5000 | Tech/automation: 100000+");
        kv (s, "marketDepth",              c.marketDepth);
        cmt(s, "Steepness of the price curve. Higher = more dramatic swings per trade.");
        kv (s, "sensitivity",              c.sensitivity);
        cmt(s, "Max items one player can contribute to price shift per time window.");
        kv (s, "deficitLimitPerWindow",    c.deficitLimitPerWindow);
        cmt(s, "Hours before a player's deficit contribution resets.");
        kv (s, "deficitWindowHours",       c.deficitWindowHours);
        s.append('\n');

        // ── Auction ───────────────────────────────────────────────────────────
        cmt(s, "── Auction ──────────────────────────────────────────────────────────────");
        cmt(s, "If a bid arrives within this many ms of the end time, the auction extends");
        cmt(s, "by the same duration to prevent last-second sniping. Default 120000 = 2 min.");
        kv (s, "antiSnipeExtensionMs",     c.antiSnipeExtensionMs);
        cmt(s, "New bids must exceed the current top bid by at least this percent (1.0 = 1%).");
        kv (s, "minBidIncrementPercent",   c.minBidIncrementPercent);
        cmt(s, "Non-refundable listing fee as a percent of the listing price. Set to 0 to disable.");
        kv (s, "listingFeePercent",         c.listingFeePercent);
        cmt(s, "Maximum price a player can set for a single auction listing.");
        kv (s, "maxListingPrice",           c.maxListingPrice);
        cmt(s, "Controls which items may be auctioned: \"all\", \"whitelist\", or \"blacklist\".");
        kvStr(s, "auctionItemMode",         c.auctionItemMode);
        cmt(s, "If true, items listed in the market shop can also appear on the auction house.");
        kv (s, "allowMarketItemsInAuction", c.allowMarketItemsInAuction);
        cmt(s, "Item IDs allowed on the auction house (active when auctionItemMode is \"whitelist\").");
        cmt(s, "Example: [\"minecraft:bread\", \"minecraft:diamond\"]");
        kvList(s, "auctionWhitelist",       c.auctionWhitelist);
        cmt(s, "Item IDs blocked from the auction house (active when auctionItemMode is \"blacklist\").");
        kvList(s, "auctionBlacklist",       c.auctionBlacklist);
        s.append('\n');

        // ── GUI Tab Visibility ────────────────────────────────────────────────
        cmt(s, "── GUI Tab Visibility ───────────────────────────────────────────────────");
        cmt(s, "Show or hide each tab in the market GUI. Set to false to remove it entirely.");
        kv (s, "showMarketTab",             c.showMarketTab);
        cmt(s, "Set to false to keep all currency digital (disables coin withdrawal).");
        kv (s, "showWithdrawTab",           c.showWithdrawTab);
        kv (s, "showHistoryTab",            c.showHistoryTab);
        kv (s, "showAuctionTab",            c.showAuctionTab);
        cmt(s, "If false, the keybind (default: ;) cannot open the GUI.");
        cmt(s, "Players must use /jecon market open or an NPC trigger instead.");
        kv (s, "allowHotkeyOpen",           c.allowHotkeyOpen);
        s.append('\n');

        // ── Command Permissions ───────────────────────────────────────────────
        cmt(s, "── Command Permissions ──────────────────────────────────────────────────");
        cmt(s, "Required permission level for each command group.");
        cmt(s, "  0 = all players  |  1 = moderator  |  2 = operator  |  4 = owner");
        s.append("  \"permissions\": {\n");
        permKv(s, "balanceOther", p.balanceOther, "/jecon balance <player>",             false);
        permKv(s, "give",         p.give,         "/jecon give",                          false);
        permKv(s, "set",          p.set,          "/jecon set",                           false);
        permKv(s, "take",         p.take,         "/jecon take",                          false);
        permKv(s, "marketOpen",   p.marketOpen,   "/jecon market open (0 = all players)", false);
        permKv(s, "marketAdmin",  p.marketAdmin,  "/jecon market setprice, addcategory, removeprice, setlock, price", false);
        permKv(s, "auctionOpen",  p.auctionOpen,  "/jecon auction open (0 = all players)", false);
        permKv(s, "debug",        p.debug,        "/jecon debug",                         true);
        s.append("  }\n");
        s.append("}\n");
        return s.toString();
    }

    /** Appends a // comment line. */
    private static void cmt(StringBuilder s, String text) {
        s.append("  // ").append(text).append('\n');
    }

    /** Appends a key: value, pair using Gson for the value. */
    private static void kv(StringBuilder s, String key, Object value) {
        s.append("  \"").append(key).append("\": ").append(GSON.toJson(value)).append(",\n");
    }

    /** Appends a key: "string", pair with JSON escaping. */
    private static void kvStr(StringBuilder s, String key, String value) {
        s.append("  \"").append(key).append("\": \"")
         .append(value.replace("\\", "\\\\").replace("\"", "\\\""))
         .append("\",\n");
    }

    /** Appends a key: [list], pair. */
    private static void kvList(StringBuilder s, String key, List<String> list) {
        s.append("  \"").append(key).append("\": ").append(GSON.toJson(list)).append(",\n");
    }

    /** Appends a permissions integer field with inline comment (comma before comment so it survives stripping). */
    private static void permKv(StringBuilder s, String key, int value, String comment, boolean last) {
        s.append("    \"").append(key).append("\": ").append(value);
        if (!last) s.append(',');
        s.append("  // ").append(comment).append('\n');
    }

    // ─── Price config network helpers ─────────────────────────────────────────

    public static void setPrices(JakesEconomyPriceConfig config) { priceConfig = config; }

    public static String serializePrices() { return GSON.toJson(priceConfig); }

    public static JakesEconomyPriceConfig deserializePrices(String json) {
        JakesEconomyPriceConfig config = GSON.fromJson(json, JakesEconomyPriceConfig.class);
        if (config != null && config.categories      == null) config.categories      = new java.util.LinkedHashMap<>();
        if (config != null && config.achievementLocks == null) config.achievementLocks = new java.util.LinkedHashMap<>();
        return config;
    }
}
