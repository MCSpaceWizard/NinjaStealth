package com.mcspacewizard.emergentstealth.action;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A body being moved (design doc 17 §4). On the player it points at the body; on the body it points at the
 * player. Synced attachment {@code ESAttachments.CARRY}.
 *
 * @param otherId the entity id at the other end, or -1 for none
 * @param mode    dragged along the floor, or carried on the shoulder
 */
public record CarryLink(int otherId, Mode mode) {
    public static final CarryLink NONE = new CarryLink(-1, Mode.NONE);

    public enum Mode {
        NONE,
        DRAG,
        CARRY;

        public static final StreamCodec<ByteBuf, Mode> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Mode::ordinal);
    }

    public static final StreamCodec<ByteBuf, CarryLink> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CarryLink::otherId,
            Mode.STREAM_CODEC, CarryLink::mode,
            CarryLink::new);

    public boolean isNone() {
        return mode == Mode.NONE || otherId < 0;
    }
}
