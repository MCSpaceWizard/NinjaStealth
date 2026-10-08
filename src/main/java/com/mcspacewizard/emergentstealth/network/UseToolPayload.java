package com.mcspacewizard.emergentstealth.network;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server intent: quick-use the active tool (design doc 21 §1, the V key) without changing the held item.
 *
 * @param charge 0 = a tap (throwables are lobbed), 1 = fully charged. Clamped by the server.
 */
public record UseToolPayload(float charge) implements CustomPacketPayload {
    public static final Type<UseToolPayload> TYPE = new Type<>(EmergentStealth.id("use_tool"));

    public static final StreamCodec<ByteBuf, UseToolPayload> STREAM_CODEC =
            ByteBufCodecs.FLOAT.map(UseToolPayload::new, UseToolPayload::charge);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
