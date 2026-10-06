package com.mcspacewizard.emergentstealth.network;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → one player: their own light exposure (0 dark - 1 lit), for the light gem. */
public record LightGemPayload(float exposure) implements CustomPacketPayload {
    public static final Type<LightGemPayload> TYPE = new Type<>(EmergentStealth.id("light_gem"));

    public static final StreamCodec<ByteBuf, LightGemPayload> STREAM_CODEC =
            ByteBufCodecs.FLOAT.map(LightGemPayload::new, LightGemPayload::exposure);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
