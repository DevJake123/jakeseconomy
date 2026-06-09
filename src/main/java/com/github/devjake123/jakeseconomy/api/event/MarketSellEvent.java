package com.github.devjake123.jakeseconomy.api.event;

import java.util.UUID;

/**
 * Fired after a player successfully sells items to the market.
 *
 * @param playerId    the seller
 * @param itemId      namespaced item id (e.g. {@code minecraft:diamond})
 * @param quantity    number of items sold
 * @param totalPayout total currency credited to the seller
 */
public record MarketSellEvent(UUID playerId, String itemId, long quantity, long totalPayout) {}

