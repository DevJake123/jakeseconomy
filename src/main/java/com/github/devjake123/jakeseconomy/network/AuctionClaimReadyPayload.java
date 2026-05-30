package com.github.devjake123.jakeseconomy.network;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: Tells the client whether the player has pending claims (items or currency) in escrow.
 * Drives the amber "Claim" glow badge on the Auction House sidebar button.
 */
public record AuctionClaimReadyPayload(boolean hasClaims) implements CustomPacketPayload {

    public static final Type<AuctionClaimReadyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "auction_claim_ready"));

    public static final StreamCodec<ByteBuf, AuctionClaimReadyPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, AuctionClaimReadyPayload::hasClaims,
                    AuctionClaimReadyPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}


