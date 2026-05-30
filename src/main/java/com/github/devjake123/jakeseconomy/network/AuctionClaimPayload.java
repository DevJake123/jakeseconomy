package com.github.devjake123.jakeseconomy.network;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: Player claims all pending auction items and currency from escrow. */
public record AuctionClaimPayload() implements CustomPacketPayload {

    public static final Type<AuctionClaimPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "auction_claim"));

    public static final StreamCodec<ByteBuf, AuctionClaimPayload> CODEC =
            StreamCodec.unit(new AuctionClaimPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}


