package com.mcspacewizard.emergentstealth.action;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * A timed action an entity is playing (design doc 17 §3), e.g. one side of a paired takedown. The server sets
 * it (synced attachment {@code ESAttachments.ACTION}); clients animate it from {@code startTick}, relative to the
 * shared {@code anchor}, so both participants stay aligned and late joiners can seek in.
 *
 * @param action    the action id, e.g. {@code emergentstealth:takedown/rear_nonlethal}
 * @param role      which side this entity plays
 * @param startTick game time the action started
 * @param length    duration in ticks
 * @param anchor    shared reference point (the victim's position at the start)
 * @param yaw       shared facing (the attacker's yaw at the start)
 */
public record ActionPlayback(Identifier action, Role role, long startTick, int length, Vec3 anchor, float yaw) {
    public static final ActionPlayback NONE = new ActionPlayback(EmergentStealth.id("none"), Role.SOLO, Long.MIN_VALUE, 0, Vec3.ZERO, 0.0F);

    public enum Role {
        SOLO,
        ATTACKER,
        VICTIM;

        public static final StreamCodec<ByteBuf, Role> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Role::ordinal);
    }

    public static final StreamCodec<ByteBuf, ActionPlayback> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, ActionPlayback::action,
            Role.STREAM_CODEC, ActionPlayback::role,
            ByteBufCodecs.VAR_LONG, ActionPlayback::startTick,
            ByteBufCodecs.VAR_INT, ActionPlayback::length,
            Vec3.STREAM_CODEC, ActionPlayback::anchor,
            ByteBufCodecs.FLOAT, ActionPlayback::yaw,
            ActionPlayback::new);

    public boolean isNone() {
        return startTick == Long.MIN_VALUE;
    }

    /** Whether the action is still playing at {@code gameTime}. */
    public boolean activeAt(long gameTime) {
        return !isNone() && gameTime >= startTick && gameTime < startTick + length;
    }

    /** Seconds since the start, for animation (partialTick in 0..1). */
    public float seconds(long gameTime, float partialTick) {
        return (gameTime - startTick + partialTick) / 20.0F;
    }
}
