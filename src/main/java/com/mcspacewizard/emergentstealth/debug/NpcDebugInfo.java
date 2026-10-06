package com.mcspacewizard.emergentstealth.debug;

import java.util.List;
import java.util.Optional;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * Debug snapshot of a stealth NPC, synced to clients that requested it (ops / singleplayer host only;
 * vanilla's debug subscription system enforces that). Must have value equality: vanilla only re-sends
 * when the snapshot changes.
 *
 * @param archetype  archetype id
 * @param role       role name
 * @param faction    faction id
 * @param state      alert state name
 * @param tier       perception LOD tier (1 full, 2 reduced, 3 not looking)
 * @param awareness  awareness of the focus target (0-1)
 * @param cones      vision cone parameters from the perception profile
 * @param lastKnown  focus target's last known position
 * @param rays       last sight rays toward the focus target
 */
public record NpcDebugInfo(String archetype, String role, String faction, String state, int tier, float awareness,
                           Cones cones, Optional<Vec3> lastKnown, List<Ray> rays) {

    public record Cones(float centralHalfAngle, float centralRange, float peripheralHalfAngle, float peripheralRange,
                        float verticalHalfAngle) {
        public static final StreamCodec<ByteBuf, Cones> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Cones::centralHalfAngle,
                ByteBufCodecs.FLOAT, Cones::centralRange,
                ByteBufCodecs.FLOAT, Cones::peripheralHalfAngle,
                ByteBufCodecs.FLOAT, Cones::peripheralRange,
                ByteBufCodecs.FLOAT, Cones::verticalHalfAngle,
                Cones::new);
    }

    /** A sight ray to a body sample point and how much sight got through (0 blocked - 1 clear). */
    public record Ray(Vec3 point, float transmittance) {
        public static final StreamCodec<ByteBuf, Ray> STREAM_CODEC = StreamCodec.composite(
                Vec3.STREAM_CODEC, Ray::point,
                ByteBufCodecs.FLOAT, Ray::transmittance,
                Ray::new);
    }

    public static final StreamCodec<ByteBuf, NpcDebugInfo> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcDebugInfo::archetype,
            ByteBufCodecs.STRING_UTF8, NpcDebugInfo::role,
            ByteBufCodecs.STRING_UTF8, NpcDebugInfo::faction,
            ByteBufCodecs.STRING_UTF8, NpcDebugInfo::state,
            ByteBufCodecs.VAR_INT, NpcDebugInfo::tier,
            ByteBufCodecs.FLOAT, NpcDebugInfo::awareness,
            Cones.STREAM_CODEC, NpcDebugInfo::cones,
            Vec3.STREAM_CODEC.apply(ByteBufCodecs::optional), NpcDebugInfo::lastKnown,
            Ray.STREAM_CODEC.apply(ByteBufCodecs.list(8)), NpcDebugInfo::rays,
            NpcDebugInfo::new);
}
