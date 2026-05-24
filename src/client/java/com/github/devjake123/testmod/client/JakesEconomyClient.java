package com.github.devjake123.testmod.client;

import com.github.devjake123.testmod.client.screen.MarketScreen;
import com.github.devjake123.testmod.config.JakesEconomyConfigManager;
import com.github.devjake123.testmod.config.JakesEconomyPriceConfig;
import com.github.devjake123.testmod.network.AdvancementLockSyncPayload;
import com.github.devjake123.testmod.network.BalanceSyncPayload;
import com.github.devjake123.testmod.network.MarketListingSyncPayload;
import com.github.devjake123.testmod.network.PriceConfigSyncPayload;
import com.github.devjake123.testmod.network.TransactionHistoryPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint for Jake's Economy.
 * Registers:
 *   - C2S packet payload types (so ClientPlayNetworking.send() works)
 *   - Keybind (default ;) to open the market screen
 */
public class JakesEconomyClient implements ClientModInitializer {

	// The keybind that opens the market GUI — default semicolon
	public static KeyMapping OPEN_MARKET_KEY;

	@Override
	public void onInitializeClient() {
		// Receive balance updates from the server and cache them for GUI display
		ClientPlayNetworking.registerGlobalReceiver(BalanceSyncPayload.TYPE, (payload, context) ->
				ClientBalanceCache.set(payload.balance())
		);

		// Receive transaction history updates and cache them for the History panel
		ClientPlayNetworking.registerGlobalReceiver(TransactionHistoryPayload.TYPE, (payload, context) ->
				ClientTransactionHistoryCache.update(payload.entries())
		);

		// Receive achievement lock state from the server and cache it for GUI rendering
		ClientPlayNetworking.registerGlobalReceiver(AdvancementLockSyncPayload.TYPE, (payload, context) ->
				ClientAdvancementLockCache.set(payload.unlockedLockIds())
		);

		// Clear lock cache on disconnect so stale state does not persist between sessions
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientAdvancementLockCache.clear();
			ClientMarketListingCache.clear();
		});

		// Receive the server's price config and apply it so the market GUI shows the correct items
		ClientPlayNetworking.registerGlobalReceiver(PriceConfigSyncPayload.TYPE, (payload, context) -> {
			JakesEconomyPriceConfig config = JakesEconomyConfigManager.deserializePrices(payload.configJson());
			if (config != null) {
				JakesEconomyConfigManager.setPrices(config);
			}
		});

		// Receive live price + trend data from the server and cache it for the GUI
		ClientPlayNetworking.registerGlobalReceiver(MarketListingSyncPayload.TYPE, (payload, context) ->
				ClientMarketListingCache.update(payload.json())
		);

		// Register the keybind in Minecraft's controls menu
		OPEN_MARKET_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.jakeseconomy.open_market",   // translation key
				GLFW.GLFW_KEY_SEMICOLON,           // default: ;
				"key.categories.jakeseconomy"      // category shown in controls menu
		));

		// Each client tick, check if the key was just pressed
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (OPEN_MARKET_KEY.consumeClick() && client.screen == null) {
				client.setScreen(new MarketScreen());
			}
		});
	}
}