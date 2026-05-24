package com.github.devjake123.testmod.network;

import com.github.devjake123.testmod.JakesEconomy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet sent from client → server when a player clicks Sell in the market GUI.
 * Server receives this and calls MarketManager.sell().
 *
 * @param itemId   the ResourceLocation string of the item
 * @param quantity number of items to sell (1, 64, or custom amount)
 */
public record MarketSellPayload(String itemId, long quantity) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MarketSellPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "market_sell"));

    public static final StreamCodec<FriendlyByteBuf, MarketSellPayload> CODEC =
            StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, MarketSellPayload::itemId,
                    net.minecraft.network.codec.ByteBufCodecs.VAR_LONG,    MarketSellPayload::quantity,
                    MarketSellPayload::new
            );

    @Override
    @org.jetbrains.annotations.NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}