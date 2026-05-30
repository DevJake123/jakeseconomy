package com.github.devjake123.jakeseconomy.network;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet sent from client → server when a player clicks Buy in the market GUI.
 * Server receives this and calls MarketManager.buy().
 *
 * @param itemId   the ResourceLocation string of the item (e.g. "minecraft:diamond")
 * @param quantity number of items to buy (1, 64, or custom amount)
 */
public record MarketBuyPayload(String itemId, long quantity) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MarketBuyPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "market_buy"));

    // Codec: tells Fabric how to serialize/deserialize this packet over the network
    public static final StreamCodec<FriendlyByteBuf, MarketBuyPayload> CODEC =
            StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, MarketBuyPayload::itemId,
                    net.minecraft.network.codec.ByteBufCodecs.VAR_LONG,    MarketBuyPayload::quantity,
                    MarketBuyPayload::new
            );

    @Override
    @org.jetbrains.annotations.NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
