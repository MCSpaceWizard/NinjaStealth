package com.mcspacewizard.emergentstealth.network;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: open the Sumi dialogue preview (design doc 31 §3.3, {@code /es dev dialogue}). */
public record OpenDialoguePreviewPayload() implements CustomPacketPayload {
    public static final OpenDialoguePreviewPayload INSTANCE = new OpenDialoguePreviewPayload();
    public static final Type<OpenDialoguePreviewPayload> TYPE = new Type<>(EmergentStealth.id("open_dialogue_preview"));
    public static final StreamCodec<ByteBuf, OpenDialoguePreviewPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
