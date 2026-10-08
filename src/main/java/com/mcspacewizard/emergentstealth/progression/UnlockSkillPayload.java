package com.mcspacewizard.emergentstealth.progression;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client intent: unlock a skill (from the skill tree screen). The server validates. */
public record UnlockSkillPayload(Identifier skill) implements CustomPacketPayload {
    public static final Type<UnlockSkillPayload> TYPE = new Type<>(EmergentStealth.id("unlock_skill"));
    public static final StreamCodec<ByteBuf, UnlockSkillPayload> STREAM_CODEC =
            Identifier.STREAM_CODEC.map(UnlockSkillPayload::new, UnlockSkillPayload::skill);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
