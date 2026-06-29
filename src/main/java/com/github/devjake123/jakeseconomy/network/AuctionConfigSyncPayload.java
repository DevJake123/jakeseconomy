package com.github.devjake123.jakeseconomy.network;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client: sends auction item-filter configuration so the client
 * can correctly filter the "New Listing" item picker.
 *
 * JSON format:
 *   {"mode":"whitelist","whitelist":["minecraft:bread"],"blacklist":[],"allowMarketItems":false}
 *
 * Sent once on player join (same timing as GuiVisibilitySyncPayload).
 */
public record AuctionConfigSyncPayload(String json) implements CustomPacketPayload {

    public static final Type<AuctionConfigSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "auction_config_sync"));

    public static final StreamCodec<ByteBuf, AuctionConfigSyncPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AuctionConfigSyncPayload::json,
                    AuctionConfigSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

