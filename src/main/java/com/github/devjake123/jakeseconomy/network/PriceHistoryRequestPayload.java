package com.github.devjake123.jakeseconomy.network;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → Server: request the price history for a specific market item.
 * The server responds with a {@link PriceHistoryResponsePayload}.
 * Sent when the player opens the trend graph screen for an item.
 */
public record PriceHistoryRequestPayload(String itemId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PriceHistoryRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "price_history_request"));

    public static final StreamCodec<ByteBuf, PriceHistoryRequestPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    PriceHistoryRequestPayload::itemId,
                    PriceHistoryRequestPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}

