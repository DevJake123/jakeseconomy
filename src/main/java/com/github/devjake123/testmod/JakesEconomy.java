package com.github.devjake123.testmod;

import com.github.devjake123.testmod.command.JakesEconomyCommands;
import com.github.devjake123.testmod.config.JakesEconomyConfigManager;
import com.github.devjake123.testmod.economy.EconomyState;
import com.github.devjake123.testmod.economy.MarketManager;
import com.github.devjake123.testmod.economy.PriceDecayScheduler;
import com.github.devjake123.testmod.economy.TrendSnapshotScheduler;
import com.github.devjake123.testmod.init.JakesEconomyCoinHandler;
import com.github.devjake123.testmod.init.JakesEconomyItemGroup;
import com.github.devjake123.testmod.init.JakesEconomyItems;
import com.github.devjake123.testmod.init.JakesEconomyLootTables;
import com.github.devjake123.testmod.network.MarketPacketHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint for Jake's Economy.
 *
 * This class is loaded by Fabric when the mod initializes (both client and server).
 * It is responsible for triggering registration of all mod content and wiring
 * Mod ID: "jakeseconomy"
 * All ResourceLocations for this mod should be created via JakesEconomy.id(path).
 */
public class JakesEconomy implements ModInitializer {

	// The mod's unique identifier — used in all registry keys, ResourceLocations,
	// config file names, and NBT save keys throughout the codebase.
	public static final String MOD_ID = "jakeseconomy";

	// Shared logger — use JakesEconomy.LOGGER throughout the mod for consistent log prefixing.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Register all coin/sack items into the game's item registry.
		JakesEconomyItems.load();

		// Register the "Jake's Economy" creative mode tab.
		JakesEconomyItemGroup.load();

		// Inject coin drops into vanilla chest loot tables.
		JakesEconomyLootTables.register();

		// Register the right-click handler for physical coins.
		JakesEconomyCoinHandler.register();

		// Register price decay tick listener
		PriceDecayScheduler.register();

		// Register trend snapshot tick listener (fires every 3 minutes)
		TrendSnapshotScheduler.register();

		// Initialize MarketManager once server is fully started and config is loaded
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			JakesEconomyConfigManager.loadServer(server);
			MarketManager.initialize(server);
		});

		// Safety net: mark economy data dirty just before shutdown so Minecraft always
		// flushes it to disk, even if the process was force-killed before auto-save ran.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			try {
				EconomyState.get(server).setDirty();
				LOGGER.info("Economy state marked for final save on shutdown.");
			} catch (Exception e) {
				LOGGER.error("Failed to mark economy state dirty on shutdown: {}", e.getMessage());
			}
		});

		// Register all /jakeseconomy commands (balance, give, set, take).
		JakesEconomyCommands.register();

		// Register network packet handlers for client→server market transactions
		MarketPacketHandler.register();

		LOGGER.info("Jakes Economy Initialized.");
	}

	/**
	 * Creates a ResourceLocation in the jakeseconomy namespace.
	 * Use this everywhere instead of ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
	 * to keep code concise and avoid typos in the namespace string.
	 */
	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}