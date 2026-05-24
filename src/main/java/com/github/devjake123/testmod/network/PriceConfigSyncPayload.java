package com.github.devjake123.testmod.network;

import com.github.devjake123.testmod.JakesEconomy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client: sends the full price config (categories, items, achievement locks)
 * as a JSON string so the market GUI can render correctly without a local config file.
 * Sent once on player join.
 */
public record PriceConfigSyncPayload(String configJson) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PriceConfigSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "price_config_sync"));

    public static final StreamCodec<ByteBuf, PriceConfigSyncPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    PriceConfigSyncPayload::configJson,
                    PriceConfigSyncPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}