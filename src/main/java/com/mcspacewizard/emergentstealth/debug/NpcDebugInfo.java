package com.mcspacewizard.emergentstealth.debug;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Debug snapshot of a stealth NPC, synced to clients that requested it (ops / singleplayer host only;
 * vanilla's debug subscription system enforces that). Must have value equality: vanilla only re-sends
 * when the snapshot changes.
 *
 * @param archetype   archetype id
 * @param role        role name
 * @param faction     faction id
 * @param state       current AI state label (placeholder until S4)
 * @param viewRange   central vision range in blocks (placeholder until S2)
 * @param fovDegrees  central vision cone full angle in degrees (placeholder until S2)
 */
public record NpcDebugInfo(String archetype, String role, String faction, String state, float viewRange, float fovDegrees) {
    public static final StreamCodec<ByteBuf, NpcDebugInfo> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcDebugInfo::archetype,
            ByteBufCodecs.STRING_UTF8, NpcDebugInfo::role,
            ByteBufCodecs.STRING_UTF8, NpcDebugInfo::faction,
            ByteBufCodecs.STRING_UTF8, NpcDebugInfo::state,
            ByteBufCodecs.FLOAT, NpcDebugInfo::viewRange,
            ByteBufCodecs.FLOAT, NpcDebugInfo::fovDegrees,
            NpcDebugInfo::new);
}
