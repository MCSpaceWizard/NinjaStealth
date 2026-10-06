package com.mcspacewizard.emergentstealth.network;

import java.util.List;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server → one player with the light debug view on (op / singleplayer host only): an exposure heatmap
 * around them, their own body-point exposure and what lights it.
 *
 * @param gridOrigin   world position of grid cell (0, 0); cells step +x then +z
 * @param gridSize     cells per side
 * @param gridHeights  per cell: Y of the sampled floor (only meaningful where exposure != NO_DATA)
 * @param gridExposure per cell: exposure 0-250, or {@link #NO_DATA}
 * @param samples      the player's body points and their exposure
 * @param sources      lights reaching the player's chest point (strongest first)
 * @param celestialDir direction to the sun (day) or moon (night)
 * @param stats        [blockExposure, skyExposure, skyAccess, celestialClear, total]
 */
public record LightDebugPayload(BlockPos gridOrigin, int gridSize, List<Integer> gridHeights, byte[] gridExposure,
                                List<Sample> samples, List<Source> sources, Vec3 celestialDir, List<Float> stats)
        implements CustomPacketPayload {

    public static final Type<LightDebugPayload> TYPE = new Type<>(EmergentStealth.id("light_debug"));
    public static final int NO_DATA = 255;

    public record Sample(Vec3 pos, float exposure) {
        public static final StreamCodec<ByteBuf, Sample> STREAM_CODEC = StreamCodec.composite(
                Vec3.STREAM_CODEC, Sample::pos, ByteBufCodecs.FLOAT, Sample::exposure, Sample::new);
    }

    public record Source(Vec3 pos, float potential, float transmittance, boolean dynamic) {
        public static final StreamCodec<ByteBuf, Source> STREAM_CODEC = StreamCodec.composite(
                Vec3.STREAM_CODEC, Source::pos,
                ByteBufCodecs.FLOAT, Source::potential,
                ByteBufCodecs.FLOAT, Source::transmittance,
                ByteBufCodecs.BOOL, Source::dynamic,
                Source::new);
    }

    public static final StreamCodec<ByteBuf, LightDebugPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LightDebugPayload::gridOrigin,
            ByteBufCodecs.VAR_INT, LightDebugPayload::gridSize,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(4096)), LightDebugPayload::gridHeights,
            ByteBufCodecs.BYTE_ARRAY, LightDebugPayload::gridExposure,
            Sample.STREAM_CODEC.apply(ByteBufCodecs.list(16)), LightDebugPayload::samples,
            Source.STREAM_CODEC.apply(ByteBufCodecs.list(16)), LightDebugPayload::sources,
            Vec3.STREAM_CODEC, LightDebugPayload::celestialDir,
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list(8)), LightDebugPayload::stats,
            LightDebugPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
