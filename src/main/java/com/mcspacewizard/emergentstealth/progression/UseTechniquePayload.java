package com.mcspacewizard.emergentstealth.progression;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client intent: use the selected technique, or (cycle = true) select the next unlocked one. */
public record UseTechniquePayload(boolean cycle) implements CustomPacketPayload {
    public static final Type<UseTechniquePayload> TYPE = new Type<>(EmergentStealth.id("use_technique"));
    public static final StreamCodec<ByteBuf, UseTechniquePayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(UseTechniquePayload::new, UseTechniquePayload::cycle);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
