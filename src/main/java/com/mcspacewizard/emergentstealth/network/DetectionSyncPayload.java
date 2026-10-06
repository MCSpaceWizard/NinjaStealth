package com.mcspacewizard.emergentstealth.network;

import java.util.List;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → one player: how aware nearby NPCs are of <em>that player</em>, for the detection indicators.
 * Only the receiving player's own awareness is included (M-01: no free information about other players).
 */
public record DetectionSyncPayload(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<DetectionSyncPayload> TYPE = new Type<>(EmergentStealth.id("detection_sync"));

    /**
     * @param entityId  the NPC
     * @param awareness its awareness of the receiving player (0-1)
     * @param state     its alert state ordinal
     * @param focusYou  whether its attention (alert target or strongest awareness) is the receiving player
     */
    public record Entry(int entityId, float awareness, int state, boolean focusYou) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::entityId,
                ByteBufCodecs.FLOAT, Entry::awareness,
                ByteBufCodecs.VAR_INT, Entry::state,
                ByteBufCodecs.BOOL, Entry::focusYou,
                Entry::new);
    }

    public static final StreamCodec<ByteBuf, DetectionSyncPayload> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(512)).map(DetectionSyncPayload::new, DetectionSyncPayload::entries);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
