package com.github.devjake123.testmod.network;

import com.github.devjake123.testmod.JakesEconomy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client: syncs the player's current virtual balance so the GUI can display it.
 * Sent after every buy/sell/withdraw/setbalance operation.
 */
public record BalanceSyncPayload(long balance) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BalanceSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "balance_sync"));

    public static final StreamCodec<ByteBuf, BalanceSyncPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, BalanceSyncPayload::balance,
                    BalanceSyncPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}

