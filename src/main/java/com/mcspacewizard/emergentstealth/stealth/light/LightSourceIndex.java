package com.mcspacewizard.emergentstealth.stealth.light;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Caches the light-emitting blocks of each chunk section so exposure can trace light from each source.
 * Sections with no emitters are skipped cheaply via a palette check. Entries are invalidated on block
 * changes (neighbour notifications) and expire after {@link #MAX_AGE_TICKS} as a safety net for changes
 * that don't notify neighbours.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class LightSourceIndex {
    private LightSourceIndex() {}

    /** A light-emitting block: centre position and emission level (1-15). */
    public record Source(BlockPos pos, int emission) {}

    private static final long MAX_AGE_TICKS = 100;
    private static final Map<ServerLevel, Long2ObjectMap<Entry>> CACHE = new WeakHashMap<>();

    private record Entry(List<Source> sources, long builtTick) {}

    /** All sources in the sections overlapping a cube of the given radius around {@code center}. */
    public static List<Source> sourcesNear(ServerLevel level, BlockPos center, int radius) {
        List<Source> result = new ArrayList<>();
        int minX = SectionPos.blockToSectionCoord(center.getX() - radius);
        int maxX = SectionPos.blockToSectionCoord(center.getX() + radius);
        int minY = SectionPos.blockToSectionCoord(center.getY() - radius);
        int maxY = SectionPos.blockToSectionCoord(center.getY() + radius);
        int minZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maxZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
        for (int sx = minX; sx <= maxX; sx++) {
            for (int sz = minZ; sz <= maxZ; sz++) {
                for (int sy = minY; sy <= maxY; sy++) {
                    result.addAll(section(level, sx, sy, sz));
                }
            }
        }
        return result;
    }

    private static List<Source> section(ServerLevel level, int sx, int sy, int sz) {
        Long2ObjectMap<Entry> cache = CACHE.computeIfAbsent(level, l -> new Long2ObjectOpenHashMap<>());
        long key = SectionPos.asLong(sx, sy, sz);
        long now = level.getGameTime();
        Entry entry = cache.get(key);
        if (entry != null && now - entry.builtTick < MAX_AGE_TICKS) {
            return entry.sources;
        }
        List<Source> sources = scan(level, sx, sy, sz);
        cache.put(key, new Entry(sources, now));
        return sources;
    }

    private static List<Source> scan(ServerLevel level, int sx, int sy, int sz) {
        if (sy < level.getMinSectionY() || sy > level.getMaxSectionY()) {
            return List.of();
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(sx, sz);
        if (chunk == null) {
            return List.of();
        }
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
        if (section.hasOnlyAir() || !section.maybeHas(state -> state.getLightEmission() > 0)) {
            return List.of();
        }
        List<Source> sources = new ArrayList<>();
        int baseX = SectionPos.sectionToBlockCoord(sx);
        int baseY = SectionPos.sectionToBlockCoord(sy);
        int baseZ = SectionPos.sectionToBlockCoord(sz);
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    int emission = state.getLightEmission();
                    if (emission > 0) {
                        sources.add(new Source(new BlockPos(baseX + x, baseY + y, baseZ + z), emission));
                    }
                }
            }
        }
        return sources;
    }

    @SubscribeEvent
    static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Long2ObjectMap<Entry> cache = CACHE.get(level);
            if (cache != null) {
                cache.remove(SectionPos.asLong(event.getPos()));
            }
        }
    }

    @SubscribeEvent
    static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Long2ObjectMap<Entry> cache = CACHE.get(level);
            if (cache != null) {
                int cx = event.getChunk().getPos().x();
                int cz = event.getChunk().getPos().z();
                for (int sy = level.getMinSectionY(); sy <= level.getMaxSectionY(); sy++) {
                    cache.remove(SectionPos.asLong(cx, sy, cz));
                }
            }
        }
    }

    /** Drops cached sources around a position (call after changing a light block without neighbour updates). */
    public static void invalidate(ServerLevel level, BlockPos pos) {
        Long2ObjectMap<Entry> cache = CACHE.get(level);
        if (cache != null) {
            cache.remove(SectionPos.asLong(pos));
        }
    }
}
