package com.mcspacewizard.emergentstealth.client.light;

import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.stealth.light.LightTransport;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Lowest and highest surface block of each chunk (from the client-synced {@code WORLD_SURFACE} heightmap).
 * A sun or moon ray that has climbed above every surface it still crosses can only meet air, so it stops
 * there ({@link LightTransport#skyDirect}'s ceiling). On open ground that turns a 64-block ray into one or
 * two steps. Also bounds which sections need re-baking when the sun moves. Read from meshing threads.
 */
final class ColumnCeilings {
    private ColumnCeilings() {}

    /** Surface height range of one chunk: the lowest and highest top block. */
    record Range(int min, int max) {}

    private static final ConcurrentHashMap<Long, Range> CHUNKS = new ConcurrentHashMap<>();

    /** Surface range of a chunk, or null if it isn't loaded (callers then assume the build limit). */
    static @Nullable Range chunk(ClientLevel level, int cx, int cz) {
        long key = ChunkPos.pack(cx, cz);
        Range range = CHUNKS.get(key);
        if (range != null) {
            return range;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null || !chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE)) {
            return null;
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                min = Math.min(min, top);
                max = Math.max(max, top);
            }
        }
        range = new Range(min, max);
        CHUNKS.put(key, range);
        return range;
    }

    /**
     * Highest surface any sun/moon ray from {@code point} can cross: the max over the chunks under both rays'
     * horizontal spans. Falls back to the build limit where a chunk isn't known.
     */
    static int ceiling(ClientLevel level, Vec3 point, LightTransport.SkyState sky) {
        int ceiling = Integer.MIN_VALUE;
        if (sky.sunUp()) {
            ceiling = Math.max(ceiling, along(level, point, sky.sun()));
        }
        if (sky.moonUp()) {
            ceiling = Math.max(ceiling, along(level, point, sky.moon()));
        }
        return ceiling == Integer.MIN_VALUE ? level.getMaxY() : ceiling;
    }

    private static int along(ClientLevel level, Vec3 point, Vec3 direction) {
        Vec3 end = point.add(direction.scale(LightTransport.SKY_RAY_LENGTH));
        int minX = SectionPos.blockToSectionCoord(Math.floor(Math.min(point.x, end.x)));
        int maxX = SectionPos.blockToSectionCoord(Math.floor(Math.max(point.x, end.x)));
        int minZ = SectionPos.blockToSectionCoord(Math.floor(Math.min(point.z, end.z)));
        int maxZ = SectionPos.blockToSectionCoord(Math.floor(Math.max(point.z, end.z)));
        int ceiling = Integer.MIN_VALUE;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                Range range = chunk(level, cx, cz);
                if (range == null) {
                    return level.getMaxY();
                }
                ceiling = Math.max(ceiling, range.max());
            }
        }
        return ceiling;
    }

    static void invalidate(BlockPos pos) {
        CHUNKS.remove(ChunkPos.pack(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())));
    }

    static void invalidateChunk(int cx, int cz) {
        CHUNKS.remove(ChunkPos.pack(cx, cz));
    }

    static void clear() {
        CHUNKS.clear();
    }
}
