package com.github.devjake123.jakeseconomy.config;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public class JakesEconomyConfigManager {

    // Pretty-printed Gson instance for human-readable config files
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // In-memory config instances — access via getServer(), getClient(), getPrices()
    private static JakesEconomyServerConfig serverConfig = new JakesEconomyServerConfig();
    private static JakesEconomyClientConfig clientConfig = new JakesEconomyClientConfig();
    private static JakesEconomyPriceConfig priceConfig = new JakesEconomyPriceConfig();

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
            // Singleplayer: decay ON by default so prices recover between play sessions.
            // Dedicated server: decay OFF so the player community permanently shapes the economy.
            defaults.priceDecayEnabled = server.isSingleplayer();
            return defaults;
        });
        // Backfill any fields that Gson left at 0 because they were added after the
        // config file was first created (Gson's Unsafe instantiation skips initializers).
        serverConfig.mergeDefaults();
        // Re-save so the new fields appear in the file for the admin to see/adjust.
        save("jakeseconomy-server.json", serverConfig);

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

    // Saves server config to disk.
    // Call this after any op command that modifies prices or server settings.
    public static void saveServer() {
        save("jakeseconomy-server.json", serverConfig);
    }

    // Saves the price sheet— called by MarketManager.setPrice/removePrice
    public static void savePrices() {
        save("jakeseconomy-prices.json", priceConfig);
    }

    // Saves client config to disk.
    // Call this when the player changes a client setting.
    public static void saveClient() {
        save("jakeseconomy-client.json", clientConfig);
    }

    // --- Accessors ---

    // Returns the server economy/market settings config
    public static JakesEconomyServerConfig getServer() {
        return serverConfig;
    }

    // Returns the client GUI/display settings config
    public static JakesEconomyClientConfig getClient() {
        return clientConfig;
    }

    // Returns the item price sheet
    public static JakesEconomyPriceConfig getPrices() {
        return priceConfig;
    }

    // --- Internal helpers ---

    // Loads a config file from disk if it exists, otherwise generates defaults via the supplier,
    // saves them to disk, and returns them.
    private static <T> T loadOrCreate(String filename, Class<T> type, Supplier<T> defaults) {
        Path path = configDir.resolve(filename);
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                T loaded = GSON.fromJson(reader, type);
                // Gson uses Unsafe to create instances, skipping field initializers,
                // so a missing or empty JSON file produces null or an object with null fields.
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

    // Writes to a .tmp file first, then atomically renames it to the real file.
// This prevents a truncated/corrupt config if the server crashes mid-write.
    private static void save(String filename, Object data) {
        if (configDir == null) return;
        Path path = configDir.resolve(filename);
        Path tmp = configDir.resolve(filename + ".tmp");
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(data, writer);
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            JakesEconomy.LOGGER.error("Failed to save {}", filename, e);
            // Best-effort cleanup of the temp file if rename failed
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
    }

    /** Replaces the in-memory price config — called on the client when the server syncs its config. */
    public static void setPrices(JakesEconomyPriceConfig config) {
        priceConfig = config;
    }

    /** Serializes the price config to a JSON string for network transport. */
    public static String serializePrices() {
        return GSON.toJson(priceConfig);
    }

    /** Deserializes a price config from a JSON string received over the network. */
    public static JakesEconomyPriceConfig deserializePrices(String json) {
        JakesEconomyPriceConfig config = GSON.fromJson(json, JakesEconomyPriceConfig.class);
        // Ensure fields that Gson skips (due to Unsafe instantiation) are not null
        if (config != null && config.categories == null)      config.categories       = new java.util.LinkedHashMap<>();
        if (config != null && config.achievementLocks == null) config.achievementLocks = new java.util.LinkedHashMap<>();
        return config;
    }

}
