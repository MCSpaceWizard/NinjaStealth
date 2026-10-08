package com.mcspacewizard.emergentstealth.network;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server intent: throw one of the held item (design doc 16 §5). The server validates everything;
 * the client only says how long the key was held.
 *
 * @param charge 0 = a quick tap (a lob), 1 = fully charged (a long throw). Clamped by the server.
 */
public record ThrowItemPayload(float charge) implements CustomPacketPayload {
    public static final Type<ThrowItemPayload> TYPE = new Type<>(EmergentStealth.id("throw_item"));

    public static final StreamCodec<ByteBuf, ThrowItemPayload> STREAM_CODEC =
            ByteBufCodecs.FLOAT.map(ThrowItemPayload::new, ThrowItemPayload::charge);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
