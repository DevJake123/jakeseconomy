package com.github.devjake123.jakeseconomy.network;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: Player instantly buys a BIN listing. */
public record AuctionBinPayload(String auctionId) implements CustomPacketPayload {

    public static final Type<AuctionBinPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "auction_bin"));

    public static final StreamCodec<FriendlyByteBuf, AuctionBinPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AuctionBinPayload::auctionId,
                    AuctionBinPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}


