package com.github.devjake123.jakeseconomy.api.event;

import java.util.UUID;

/**
 * Fired after a player successfully buys items from the market.
 *
 * @param playerId  the buyer
 * @param itemId    namespaced item id (e.g. {@code minecraft:diamond})
 * @param quantity  number of items bought
 * @param totalCost total currency deducted from the buyer
 */
public record MarketBuyEvent(UUID playerId, String itemId, long quantity, long totalCost) {}

