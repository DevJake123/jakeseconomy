package com.github.devjake123.testmod.network;

import com.github.devjake123.testmod.JakesEconomy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → Server: player requests to withdraw virtual balance into physical coin items.
 * Contains one count per coin type (order matches JakesEconomyItems coin definitions).
 */
public record WithdrawPayload(
        long copperCoins,
        long copperSacks,
        long silverCoins,
        long silverSacks,
        long goldCoins,
        long goldSacks,
        long platinumCoins,
        long platinumSacks
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WithdrawPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "withdraw"));

    public static final StreamCodec<ByteBuf, WithdrawPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_LONG.encode(buf, payload.copperCoins());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.copperSacks());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.silverCoins());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.silverSacks());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.goldCoins());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.goldSacks());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.platinumCoins());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.platinumSacks());
            },
            buf -> new WithdrawPayload(
                    ByteBufCodecs.VAR_LONG.decode(buf),  // copperCoins
                    ByteBufCodecs.VAR_LONG.decode(buf),  // copperSacks
                    ByteBufCodecs.VAR_LONG.decode(buf),  // silverCoins
                    ByteBufCodecs.VAR_LONG.decode(buf),  // silverSacks
                    ByteBufCodecs.VAR_LONG.decode(buf),  // goldCoins
                    ByteBufCodecs.VAR_LONG.decode(buf),  // goldSacks
                    ByteBufCodecs.VAR_LONG.decode(buf),  // platinumCoins
                    ByteBufCodecs.VAR_LONG.decode(buf)   // platinumSacks
            )
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}