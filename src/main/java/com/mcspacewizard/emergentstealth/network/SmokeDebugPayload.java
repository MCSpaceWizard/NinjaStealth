package com.mcspacewizard.emergentstealth.network;

import java.util.List;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server → one player with the AI debug view on (op / singleplayer host only): the active smoke volumes near them
 * (design doc 21). Sent twice a second while there is smoke, and once more when it's gone.
 */
public record SmokeDebugPayload(List<Smoke> volumes) implements CustomPacketPayload {
    public static final Type<SmokeDebugPayload> TYPE = new Type<>(EmergentStealth.id("smoke_debug"));

    /** One sphere: centre, radius and ticks until it stops blocking sight. */
    public record Smoke(Vec3 center, float radius, int ticksLeft) {
        public static final StreamCodec<ByteBuf, Smoke> STREAM_CODEC = StreamCodec.composite(
                Vec3.STREAM_CODEC, Smoke::center,
                ByteBufCodecs.FLOAT, Smoke::radius,
                ByteBufCodecs.VAR_INT, Smoke::ticksLeft,
                Smoke::new);
    }

    public static final StreamCodec<ByteBuf, SmokeDebugPayload> STREAM_CODEC = StreamCodec.composite(
            Smoke.STREAM_CODEC.apply(ByteBufCodecs.list(64)), SmokeDebugPayload::volumes,
            SmokeDebugPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
