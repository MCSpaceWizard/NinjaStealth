package com.mcspacewizard.emergentstealth.client.light;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.stealth.light.LightSourceIndex;
import com.mcspacewizard.emergentstealth.stealth.light.LightSourceIndex.Source;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Client-side twin of the server's {@link LightSourceIndex}: the light-emitting blocks of each chunk section
 * of the client level, using the same shared scan. Read from chunk-meshing worker threads, so entries live
 * in a concurrent map and are only ever replaced, never mutated.
 * <p>
 * A global {@link #generation()} counter changes whenever anything that baked light depends on changes (a
 * relevant block, a chunk, the settings); per-thread light caches compare against it instead of being
 * cleared one by one.
 */
final class ClientLightSources {
    private ClientLightSources() {}

    /** Same safety-net expiry as the server index; normal changes invalidate explicitly. */
    private static final long MAX_AGE_TICKS = 100;
    /** Above this many emitters around one section, that section keeps vanilla light (lava lakes etc.). */
    static final int MAX_SOURCES_PER_NEIGHBOURHOOD = 256;

    private record Entry(List<Source> sources, long builtTick) {}

    private static final ConcurrentHashMap<Long, Entry> SECTIONS = new ConcurrentHashMap<>();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static volatile @Nullable ClientLevel indexedLevel;

    static long generation() {
        return GENERATION.get();
    }

    static void bumpGeneration() {
        GENERATION.incrementAndGet();
    }

    /** Sources in one section. Safe from any thread; reads the live level like vanilla meshing reads light. */
    static List<Source> section(ClientLevel level, int sx, int sy, int sz) {
        if (level != indexedLevel) {
            reset(level);
        }
        long key = SectionPos.asLong(sx, sy, sz);
        long now = level.getGameTime();
        Entry entry = SECTIONS.get(key);
        if (entry != null && now - entry.builtTick < MAX_AGE_TICKS && now >= entry.builtTick) {
            return entry.sources;
        }
        if (sy < level.getMinSectionY() || sy > level.getMaxSectionY()) {
            return List.of();
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(sx, sz);
        if (chunk == null) {
            // Not cached: the chunk is scanned once it arrives (chunk load invalidates).
            return List.of();
        }
        long generationAtScan = GENERATION.get();
        List<Source> sources = LightSourceIndex.scanSection(chunk.getSection(chunk.getSectionIndexFromSectionY(sy)), sx, sy, sz);
        // A block change during the scan may have made this result stale: use it once, don't cache it.
        if (GENERATION.get() == generationAtScan) {
            SECTIONS.put(key, new Entry(sources, now));
            if (entry != null && !entry.sources.equals(sources)) {
                // Expiry caught a change that wasn't reported: baked values may depend on it.
                bumpGeneration();
            }
        }
        return sources;
    }

    /**
     * All sources in the 3×3×3 sections around a section: every light that can reach any block in it, since
     * light travels at most 15 blocks. Returns null when there are too many to trace (caller keeps vanilla).
     */
    static @Nullable List<Source> neighbourhood(ClientLevel level, int sx, int sy, int sz) {
        List<Source> result = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    result.addAll(section(level, sx + dx, sy + dy, sz + dz));
                    if (result.size() > MAX_SOURCES_PER_NEIGHBOURHOOD) {
                        return null;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Sources whose light could pass through {@code pos} (main thread: block-change handling). One block of
     * margin: a ray can clip a block whose centre is slightly further away than the ray's end.
     */
    static List<Source> reaching(ClientLevel level, BlockPos pos) {
        List<Source> result = new ArrayList<>();
        int sx = SectionPos.blockToSectionCoord(pos.getX());
        int sy = SectionPos.blockToSectionCoord(pos.getY());
        int sz = SectionPos.blockToSectionCoord(pos.getZ());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (Source source : section(level, sx + dx, sy + dy, sz + dz)) {
                        double reach = source.emission() + 1.0;
                        if (source.pos().distSqr(pos) < reach * reach) {
                            result.add(source);
                        }
                    }
                }
            }
        }
        return result;
    }

    /** Drops the section containing {@code pos}. */
    static void invalidate(BlockPos pos) {
        SECTIONS.remove(SectionPos.asLong(pos));
    }

    /** Drops every section of a chunk column (chunk load/unload). */
    static void invalidateChunk(ClientLevel level, int cx, int cz) {
        for (int sy = level.getMinSectionY(); sy <= level.getMaxSectionY(); sy++) {
            SECTIONS.remove(SectionPos.asLong(cx, sy, cz));
        }
        bumpGeneration();
    }

    static synchronized void reset(@Nullable ClientLevel level) {
        if (indexedLevel != level) {
            SECTIONS.clear();
            indexedLevel = level;
            bumpGeneration();
        }
    }
}
