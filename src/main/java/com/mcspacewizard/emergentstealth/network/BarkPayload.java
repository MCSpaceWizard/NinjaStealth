package com.mcspacewizard.emergentstealth.network;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * An NPC says something (design doc 14 §6). The client picks the line: {@code seed} chooses among the lang
 * keys {@code bark.emergentstealth.<situation>.<n>} it has, so writers can add lines without code.
 */
public record BarkPayload(int entityId, String situation, int seed) implements CustomPacketPayload {
    public static final Type<BarkPayload> TYPE = new Type<>(EmergentStealth.id("bark"));

    public static final StreamCodec<ByteBuf, BarkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BarkPayload::entityId,
            ByteBufCodecs.stringUtf8(64), BarkPayload::situation,
            ByteBufCodecs.VAR_INT, BarkPayload::seed,
            BarkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
