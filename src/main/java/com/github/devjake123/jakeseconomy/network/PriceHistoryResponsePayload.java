package com.github.devjake123.jakeseconomy.network;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client: delivers price history for a specific market item as a tiered JSON object.
 *
 * JSON format: {@code {"recent":[{"t":<epochMs>,"p":<price>},...], "archive":[...]}}
 * Points are ordered oldest → newest.
 * Recent tier: up to 72 entries (20-min snapshots, 24h). Archive tier: up to 720 entries (hourly, 30 days).
 */
public record PriceHistoryResponsePayload(String itemId, String json) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PriceHistoryResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "price_history_response"));

    public static final StreamCodec<ByteBuf, PriceHistoryResponsePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    PriceHistoryResponsePayload::itemId,
                    ByteBufCodecs.STRING_UTF8,
                    PriceHistoryResponsePayload::json,
                    PriceHistoryResponsePayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}

