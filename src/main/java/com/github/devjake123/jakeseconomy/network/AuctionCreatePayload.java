package com.github.devjake123.jakeseconomy.network;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: Player requests to list an item from their inventory in the Auction House. */
public record AuctionCreatePayload(int inventorySlot, long price, long durationMs, boolean isBin)
        implements CustomPacketPayload {

    public static final Type<AuctionCreatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "auction_create"));

    public static final StreamCodec<FriendlyByteBuf, AuctionCreatePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,  AuctionCreatePayload::inventorySlot,
                    ByteBufCodecs.VAR_LONG, AuctionCreatePayload::price,
                    ByteBufCodecs.VAR_LONG, AuctionCreatePayload::durationMs,
                    ByteBufCodecs.BOOL,     AuctionCreatePayload::isBin,
                    AuctionCreatePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}


