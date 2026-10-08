package com.mcspacewizard.emergentstealth.action;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client intent: put down the body being dragged or carried (any right-click while moving one). */
public record DropBodyPayload() implements CustomPacketPayload {
    public static final DropBodyPayload INSTANCE = new DropBodyPayload();
    public static final Type<DropBodyPayload> TYPE = new Type<>(EmergentStealth.id("drop_body"));
    public static final StreamCodec<ByteBuf, DropBodyPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
