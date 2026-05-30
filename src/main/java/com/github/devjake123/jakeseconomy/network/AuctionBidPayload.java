package com.github.devjake123.jakeseconomy.network;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: Player places a bid on an open auction. */
public record AuctionBidPayload(String auctionId, long amount)
        implements CustomPacketPayload {

    public static final Type<AuctionBidPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "auction_bid"));

    public static final StreamCodec<FriendlyByteBuf, AuctionBidPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AuctionBidPayload::auctionId,
                    ByteBufCodecs.VAR_LONG,    AuctionBidPayload::amount,
                    AuctionBidPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}


