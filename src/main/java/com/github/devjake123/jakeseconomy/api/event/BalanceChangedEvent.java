package com.github.devjake123.jakeseconomy.api.event;

import java.util.UUID;

/**
 * Fired after a player's virtual balance is changed (deposit, withdraw, or set).
 *
 * @param playerId    the player whose balance changed
 * @param oldBalance  balance before the change
 * @param newBalance  balance after the change
 */
public record BalanceChangedEvent(UUID playerId, long oldBalance, long newBalance) {}

