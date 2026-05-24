package com.github.devjake123.testmod.network;

import com.github.devjake123.testmod.JakesEconomy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * Server → Client: syncs which achievement lock IDs the local player has unlocked.
 * Sent on player join and whenever a relevant advancement is granted.
 * The client caches this in ClientAdvancementLockCache for use in market UI rendering.
 */
public record AdvancementLockSyncPayload(Set<Integer> unlockedLockIds) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdvancementLockSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(JakesEconomy.MOD_ID, "advancement_lock_sync"));

    public static final StreamCodec<ByteBuf, AdvancementLockSyncPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(HashSet::new, ByteBufCodecs.VAR_INT),
                    AdvancementLockSyncPayload::unlockedLockIds,
                    AdvancementLockSyncPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}

