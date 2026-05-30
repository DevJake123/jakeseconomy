package com.github.devjake123.jakeseconomy.network;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: Broadcasts a single updated auction entry to all online players after any mutation
 * (new bid, new listing, cancel, or finalization). Same JSON format as one entry in
 * AuctionListSyncPayload so the client cache can merge it with the same parser.
 */
public record AuctionDeltaSyncPayload(String json) implements CustomPacketPayload {

    public static final Type<AuctionDeltaSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "auction_delta_sync"));

    public static final StreamCodec<ByteBuf, AuctionDeltaSyncPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AuctionDeltaSyncPayload::json,
                    AuctionDeltaSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}


