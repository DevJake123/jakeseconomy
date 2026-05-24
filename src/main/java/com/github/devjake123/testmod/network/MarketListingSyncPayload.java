package com.github.devjake123.testmod.network;

import com.github.devjake123.testmod.JakesEconomy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client: pushes live market listing data so the client GUI shows
 * accurate, up-to-date prices and trend arrows in multiplayer.
 *
 * The payload carries a compact JSON string:
 *   {"itemId":{"p":<price>,"nd":<netDeficit>,"snd":<snapshotDeficit>}, ...}
 *
 * This is used for two purposes:
 *   1. Full sync  — all items sent on player JOIN and every ~10 s
 *   2. Delta sync — only the traded item sent after each buy/sell transaction
 *      (broadcast to all online players so everyone's GUI stays in sync)
 */
public record MarketListingSyncPayload(String json) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MarketListingSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "market_listing_sync"));

    public static final StreamCodec<ByteBuf, MarketListingSyncPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    MarketListingSyncPayload::json,
                    MarketListingSyncPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}

