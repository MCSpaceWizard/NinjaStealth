package com.mcspacewizard.emergentstealth.network;

import java.util.List;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server → one player with the AI debug view on (op / singleplayer host only): noises resolved this tick
 * near them, and which NPCs heard each (design doc 16 §6).
 */
public record NoiseDebugPayload(List<Noise> noises) implements CustomPacketPayload {
    public static final Type<NoiseDebugPayload> TYPE = new Type<>(EmergentStealth.id("noise_debug"));

    /** One NPC that heard the noise: where its ears are, how strongly and at what propagation cost. */
    public record Heard(Vec3 ear, float intensity, float cost) {
        public static final StreamCodec<ByteBuf, Heard> STREAM_CODEC = StreamCodec.composite(
                Vec3.STREAM_CODEC, Heard::ear,
                ByteBufCodecs.FLOAT, Heard::intensity,
                ByteBufCodecs.FLOAT, Heard::cost,
                Heard::new);
    }

    /** A noise: position, loudness (blocks), kind name, whether it gives a player away, and its hearers. */
    public record Noise(Vec3 pos, float loudness, String kind, boolean attributed, List<Heard> heard) {
        public static final StreamCodec<ByteBuf, Noise> STREAM_CODEC = StreamCodec.composite(
                Vec3.STREAM_CODEC, Noise::pos,
                ByteBufCodecs.FLOAT, Noise::loudness,
                ByteBufCodecs.stringUtf8(32), Noise::kind,
                ByteBufCodecs.BOOL, Noise::attributed,
                Heard.STREAM_CODEC.apply(ByteBufCodecs.list(64)), Noise::heard,
                Noise::new);
    }

    public static final StreamCodec<ByteBuf, NoiseDebugPayload> STREAM_CODEC = StreamCodec.composite(
            Noise.STREAM_CODEC.apply(ByteBufCodecs.list(256)), NoiseDebugPayload::noises,
            NoiseDebugPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
