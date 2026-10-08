package com.mcspacewizard.emergentstealth.action;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client intent: toggle crawling (design doc 17 §1). The server decides. */
public record ToggleCrawlPayload() implements CustomPacketPayload {
    public static final ToggleCrawlPayload INSTANCE = new ToggleCrawlPayload();
    public static final Type<ToggleCrawlPayload> TYPE = new Type<>(EmergentStealth.id("toggle_crawl"));
    public static final StreamCodec<ByteBuf, ToggleCrawlPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
