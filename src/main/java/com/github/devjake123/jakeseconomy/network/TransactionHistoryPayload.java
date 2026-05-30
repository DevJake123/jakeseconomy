package com.github.devjake123.jakeseconomy.network;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import com.github.devjake123.jakeseconomy.economy.TransactionEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → Client: sends the player's recent transaction history so the GUI can display it.
 * Sent after every buy/sell/withdraw/deposit operation.
 */
public record TransactionHistoryPayload(List<TransactionEntry> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TransactionHistoryPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "tx_history"));

    private static final StreamCodec<ByteBuf, TransactionEntry> ENTRY_CODEC = StreamCodec.of(
            (buf, e) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, e.type());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.itemId());
                ByteBufCodecs.VAR_LONG.encode(buf, e.quantity());
                ByteBufCodecs.VAR_LONG.encode(buf, e.amount());
                ByteBufCodecs.VAR_LONG.encode(buf, e.timestamp());
            },
            buf -> new TransactionEntry(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf)
            )
    );

    public static final StreamCodec<ByteBuf, TransactionHistoryPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.entries().size());
                for (TransactionEntry e : payload.entries()) ENTRY_CODEC.encode(buf, e);
            },
            buf -> {
                int count = ByteBufCodecs.VAR_INT.decode(buf);
                List<TransactionEntry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) entries.add(ENTRY_CODEC.decode(buf));
                return new TransactionHistoryPayload(entries);
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}


