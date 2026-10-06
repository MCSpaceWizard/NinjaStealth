package com.mcspacewizard.emergentstealth.stealth.sound;

import java.util.Map;
import java.util.WeakHashMap;

import com.mcspacewizard.emergentstealth.entity.StealthNpc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Background noise that covers other sounds, measured at the listener (design doc 16 §3, S-04, L-08).
 * Several sources combine as {@code 1 − Π(1 − m)}. Cached per NPC and refreshed about once a second.
 */
public final class Masking {
    private Masking() {}

    public static final float RAIN = 0.25F;
    public static final float THUNDER = 0.5F;
    public static final float FLOWING_WATER = 0.3F;
    public static final float JUKEBOX = 0.4F;
    public static final int WATER_RADIUS = 6;
    public static final int JUKEBOX_RADIUS = 16;
    /** How long a listener's masking value is reused. */
    public static final int REFRESH_TICKS = 20;

    private static final Map<StealthNpc, Cached> CACHE = new WeakHashMap<>();

    private static final class Cached {
        long time = Long.MIN_VALUE;
        float value;
    }

    /** Which masking sources are present at a spot. */
    public record Sources(boolean rain, boolean thunder, boolean flowingWater, boolean jukebox) {
        public static final Sources NONE = new Sources(false, false, false, false);

        /** Total masking 0..1. A thunderstorm replaces (rather than adds to) plain rain. */
        public float masking() {
            float keep = 1.0F;
            if (thunder) {
                keep *= 1.0F - THUNDER;
            } else if (rain) {
                keep *= 1.0F - RAIN;
            }
            if (flowingWater) {
                keep *= 1.0F - FLOWING_WATER;
            }
            if (jukebox) {
                keep *= 1.0F - JUKEBOX;
            }
            return 1.0F - keep;
        }
    }

    /** Masking at an NPC's ears, from the cache when it's less than a second old. */
    public static float cached(StealthNpc npc) {
        if (!(npc.level() instanceof ServerLevel level)) {
            return 0.0F;
        }
        long now = level.getGameTime();
        Cached cached = CACHE.computeIfAbsent(npc, n -> new Cached());
        if (now - cached.time >= REFRESH_TICKS || now < cached.time) {
            cached.time = now;
            cached.value = at(level, npc.getEyePosition());
        }
        return cached.value;
    }

    /** Masking at a point, uncached. */
    public static float at(ServerLevel level, Vec3 pos) {
        return sources(level, pos).masking();
    }

    public static Sources sources(ServerLevel level, Vec3 pos) {
        BlockPos block = BlockPos.containing(pos);
        boolean rain = false;
        boolean thunder = false;
        // Rain drums on the roof too, so this deliberately doesn't need open sky: it only needs rain to fall here.
        if (level.isRaining()) {
            Biome biome = level.getBiome(block).value();
            if (biome.getPrecipitationAt(block, level.getSeaLevel()) == Biome.Precipitation.RAIN) {
                rain = true;
                thunder = level.isThundering();
            }
        }
        return new Sources(rain, thunder, flowingWaterNear(level, block), jukeboxNear(level, pos));
    }

    /** Any flowing (not source) water within {@link #WATER_RADIUS}. Skips sections without fluids. */
    static boolean flowingWaterNear(ServerLevel level, BlockPos center) {
        int r = WATER_RADIUS;
        int r2 = r * r;
        int minSecX = SectionPos.blockToSectionCoord(center.getX() - r);
        int maxSecX = SectionPos.blockToSectionCoord(center.getX() + r);
        int minSecZ = SectionPos.blockToSectionCoord(center.getZ() - r);
        int maxSecZ = SectionPos.blockToSectionCoord(center.getZ() + r);
        int minY = Math.max(level.getMinY(), center.getY() - r);
        int maxY = Math.min(level.getMaxY(), center.getY() + r);
        if (minY > maxY) {
            return false;
        }
        for (int scx = minSecX; scx <= maxSecX; scx++) {
            for (int scz = minSecZ; scz <= maxSecZ; scz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(scx, scz);
                if (chunk == null) {
                    continue;
                }
                for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
                    LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.maybeHas(state -> !state.getFluidState().isEmpty())) {
                        continue;
                    }
                    int x0 = Math.max(center.getX() - r, scx << 4);
                    int x1 = Math.min(center.getX() + r, (scx << 4) + 15);
                    int z0 = Math.max(center.getZ() - r, scz << 4);
                    int z1 = Math.min(center.getZ() + r, (scz << 4) + 15);
                    int y0 = Math.max(minY, sy << 4);
                    int y1 = Math.min(maxY, (sy << 4) + 15);
                    for (int y = y0; y <= y1; y++) {
                        int dy = y - center.getY();
                        for (int z = z0; z <= z1; z++) {
                            int dz = z - center.getZ();
                            for (int x = x0; x <= x1; x++) {
                                int dx = x - center.getX();
                                if (dx * dx + dy * dy + dz * dz > r2) {
                                    continue;
                                }
                                FluidState fluid = section.getFluidState(x & 15, y & 15, z & 15);
                                if (!fluid.isEmpty() && !fluid.isSource() && fluid.is(FluidTags.WATER)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /** A jukebox playing a song within {@link #JUKEBOX_RADIUS}. Looks only at block entities of loaded chunks. */
    static boolean jukeboxNear(ServerLevel level, Vec3 pos) {
        int r = JUKEBOX_RADIUS;
        double r2 = (double) r * r;
        int minCx = SectionPos.blockToSectionCoord(pos.x - r);
        int maxCx = SectionPos.blockToSectionCoord(pos.x + r);
        int minCz = SectionPos.blockToSectionCoord(pos.z - r);
        int maxCz = SectionPos.blockToSectionCoord(pos.z + r);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof JukeboxBlockEntity jukebox && jukebox.getSongPlayer().isPlaying()
                            && blockEntity.getBlockPos().distToCenterSqr(pos) <= r2) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
