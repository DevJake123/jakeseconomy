package com.github.devjake123.jakeseconomy.network;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: Delivers a chunk of the auction list to the client.
 *
 * JSON format:
 *   {"total":<int>,"offset":<int>,"entries":[{auction dto...}, ...]}
 *
 * The client appends each chunk to the cache. If total > offset+entries.length,
 * it requests the next page via AuctionListRequestPayload(offset + chunk_size).
 */
public record AuctionListSyncPayload(String json) implements CustomPacketPayload {

    public static final Type<AuctionListSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "auction_list_sync"));

    public static final StreamCodec<ByteBuf, AuctionListSyncPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AuctionListSyncPayload::json,
                    AuctionListSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}


