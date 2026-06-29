package com.github.devjake123.jakeseconomy.network;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenScreenPayload(String screen) implements CustomPacketPayload {

    public static final Type<OpenScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "open_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenScreenPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, OpenScreenPayload::screen,
                    OpenScreenPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

