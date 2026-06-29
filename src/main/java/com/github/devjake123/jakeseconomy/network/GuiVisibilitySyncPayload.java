package com.github.devjake123.jakeseconomy.network;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client: sends GUI tab visibility settings and hotkey control from the server config
 * so the client knows which tabs to show/hide and whether the keybind is allowed.
 * Sent once on player join.
 */
public record GuiVisibilitySyncPayload(
        boolean showMarketTab,
        boolean showWithdrawTab,
        boolean showHistoryTab,
        boolean showAuctionTab,
        boolean allowHotkeyOpen
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GuiVisibilitySyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "gui_visibility_sync"));

    public static final StreamCodec<ByteBuf, GuiVisibilitySyncPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, GuiVisibilitySyncPayload::showMarketTab,
                    ByteBufCodecs.BOOL, GuiVisibilitySyncPayload::showWithdrawTab,
                    ByteBufCodecs.BOOL, GuiVisibilitySyncPayload::showHistoryTab,
                    ByteBufCodecs.BOOL, GuiVisibilitySyncPayload::showAuctionTab,
                    ByteBufCodecs.BOOL, GuiVisibilitySyncPayload::allowHotkeyOpen,
                    GuiVisibilitySyncPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
