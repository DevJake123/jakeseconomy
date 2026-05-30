package com.github.devjake123.jakeseconomy.network;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: Client requests the auction list, optionally paginated.
 * offset=0 for the first page; server sends back AuctionListSyncPayload with total count
 * so the client can request further pages if needed.
 */
public record AuctionListRequestPayload(int offset) implements CustomPacketPayload {

    public static final Type<AuctionListRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "auction_list_request"));

    public static final StreamCodec<FriendlyByteBuf, AuctionListRequestPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AuctionListRequestPayload::offset,
                    AuctionListRequestPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}


