package com.mcspacewizard.emergentstealth.stealth.light;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Per-cell cache of what each block cell sees of the sky ({@link LightTransport#sample}): sky openness (depends
 * only on blocks) and sun/moon disk visibility (blocks plus a quantised sky state). Used by the server's
 * {@link ExposureModel} and the client's baked sky light alike (one store per level instance), so both pay the
 * rays once per cell.
 * <p>
 * Also caches each chunk's highest surface block (the {@code WORLD_SURFACE} heightmap) as the
 * {@link LightTransport.SkyCeiling} that stops sky rays as soon as only air is left above them.
 * <p>
 * <b>Invalidation:</b> a block change drops the cells within {@link LightTransport#OPENNESS_RAY_LENGTH} of it
 * (everything openness can depend on). Sun rays are longer (64 blocks), so disk visibility also expires after
 * {@link #DISK_TTL} ticks, and openness after {@link #OPENNESS_TTL} as a safety net. Thread-safe: meshing
 * threads read and fill it concurrently (values are deterministic, so racing writes agree).
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class SkyCells {
    private SkyCells() {}

    private static final long OPENNESS_TTL = 600;
    private static final long DISK_TTL = 40;
    private static final int CELLS = 16 * 16 * 16;

    private static final class Section {
        final long builtTick;
        final float[] openness = filled();
        /** Disk visibility for one sky state; replaced as a whole so slow threads computing an old state can't leak in. */
        volatile Disks disks;

        Section(long tick) {
            this.builtTick = tick;
            this.disks = new Disks(0L, tick);
        }
    }

    private static final class Disks {
        final long key;
        final long tick;
        final float[] sun = filled();
        final float[] moon = filled();

        Disks(long key, long tick) {
            this.key = key;
            this.tick = tick;
        }
    }

    private static float[] filled() {
        float[] values = new float[CELLS];
        Arrays.fill(values, Float.NaN);
        return values;
    }

    private static final class Store {
        final ConcurrentHashMap<Long, Section> sections = new ConcurrentHashMap<>();
        final ConcurrentHashMap<Long, Integer> chunkTops = new ConcurrentHashMap<>();
    }

    private static final Map<Level, Store> STORES = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile @Nullable Level lastLevel;
    private static volatile @Nullable Store lastStore;

    private static Store store(Level level) {
        if (lastLevel == level) {
            Store store = lastStore;
            if (store != null) {
                return store;
            }
        }
        Store store = STORES.computeIfAbsent(level, l -> new Store());
        lastStore = store;
        lastLevel = level;
        return store;
    }

    /**
     * What {@code cell} sees of the sky in {@code sky} (whose sun/moon directions should be quantised so the cache
     * can hit). Callers handle cells with no sky access at all (vanilla sky light 0) themselves.
     */
    public static LightTransport.SkySample sample(Level level, BlockPos cell, LightTransport.SkyState sky) {
        Store store = store(level);
        long now = level.getGameTime();
        long key = SectionPos.asLong(cell);
        Section section = store.sections.get(key);
        if (section == null || now - section.builtTick > OPENNESS_TTL || now < section.builtTick) {
            section = new Section(now);
            store.sections.put(key, section);
        }
        long diskKey = diskKey(sky);
        Disks disks = section.disks;
        if (disks.key != diskKey || now - disks.tick > DISK_TTL || now < disks.tick) {
            disks = new Disks(diskKey, now);
            section.disks = disks;
        }
        int index = ((cell.getY() & 15) << 8) | ((cell.getZ() & 15) << 4) | (cell.getX() & 15);
        LightTransport.SkyCeiling ceiling = ceiling(level);

        float openness = section.openness[index];
        if (Float.isNaN(openness)) {
            openness = LightTransport.openness(level, cell, ceiling);
            section.openness[index] = openness;
        }
        float sun = 0.0F;
        if (sky.sunUp()) {
            sun = disks.sun[index];
            if (Float.isNaN(sun)) {
                sun = LightTransport.diskVisible(level, cell, sky.sun(), ceiling);
                disks.sun[index] = sun;
            }
        }
        float moon = 0.0F;
        if (sky.moonUp()) {
            moon = disks.moon[index];
            if (Float.isNaN(moon)) {
                moon = LightTransport.diskVisible(level, cell, sky.moon(), ceiling);
                disks.moon[index] = moon;
            }
        }
        return new LightTransport.SkySample(openness, sun, moon);
    }

    private static long diskKey(LightTransport.SkyState sky) {
        long key = Double.doubleToLongBits(sky.sun().x) * 31L + Double.doubleToLongBits(sky.sun().y);
        key = key * 31L + Double.doubleToLongBits(sky.moon().x);
        return key * 31L + Double.doubleToLongBits(sky.moon().y);
    }

    /**
     * Highest surface block any part of a sky ray can pass over: the max of the cached chunk tops under the
     * ray's horizontal span; the build limit where a chunk isn't loaded.
     */
    public static LightTransport.SkyCeiling ceiling(Level level) {
        Store store = store(level);
        return (from, direction, length) -> {
            Vec3 end = from.add(direction.scale(length));
            int minX = SectionPos.blockToSectionCoord(Math.floor(Math.min(from.x, end.x)));
            int maxX = SectionPos.blockToSectionCoord(Math.floor(Math.max(from.x, end.x)));
            int minZ = SectionPos.blockToSectionCoord(Math.floor(Math.min(from.z, end.z)));
            int maxZ = SectionPos.blockToSectionCoord(Math.floor(Math.max(from.z, end.z)));
            int top = Integer.MIN_VALUE;
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cz = minZ; cz <= maxZ; cz++) {
                    Integer chunkTop = chunkTop(level, store, cx, cz);
                    if (chunkTop == null) {
                        return level.getMaxY();
                    }
                    top = Math.max(top, chunkTop);
                }
            }
            return top;
        };
    }

    /** Lowest and highest surface block of a loaded chunk, or null. Used to find sections near the surface. */
    public static int @Nullable [] surfaceRange(Level level, int cx, int cz) {
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
        return new int[] {min, max};
    }

    private static @Nullable Integer chunkTop(Level level, Store store, int cx, int cz) {
        long key = ChunkPos.pack(cx, cz);
        Integer top = store.chunkTops.get(key);
        if (top != null) {
            return top;
        }
        int[] range = surfaceRange(level, cx, cz);
        if (range == null) {
            return null;
        }
        store.chunkTops.put(key, range[1]);
        return range[1];
    }

    /** A block changed: drop every cached cell whose openness rays could pass through it, and its chunk top. */
    public static void invalidate(Level level, BlockPos pos) {
        Store store = STORES.get(level);
        if (store == null) {
            return;
        }
        store.chunkTops.remove(ChunkPos.pack(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())));
        int reach = (int) Math.ceil(LightTransport.OPENNESS_RAY_LENGTH);
        int minX = SectionPos.blockToSectionCoord(pos.getX() - reach);
        int maxX = SectionPos.blockToSectionCoord(pos.getX() + reach);
        int minY = SectionPos.blockToSectionCoord(pos.getY() - reach);
        int maxY = SectionPos.blockToSectionCoord(pos.getY());
        int minZ = SectionPos.blockToSectionCoord(pos.getZ() - reach);
        int maxZ = SectionPos.blockToSectionCoord(pos.getZ() + reach);
        for (int sx = minX; sx <= maxX; sx++) {
            for (int sy = minY; sy <= maxY; sy++) {
                for (int sz = minZ; sz <= maxZ; sz++) {
                    store.sections.remove(SectionPos.asLong(sx, sy, sz));
                }
            }
        }
    }

    /** A chunk (un)loaded: drop its cells and its top. */
    public static void invalidateChunk(Level level, int cx, int cz) {
        Store store = STORES.get(level);
        if (store == null) {
            return;
        }
        store.chunkTops.remove(ChunkPos.pack(cx, cz));
        for (int sy = level.getMinSectionY(); sy <= level.getMaxSectionY(); sy++) {
            store.sections.remove(SectionPos.asLong(cx, sy, cz));
        }
    }

    public static void clear(Level level) {
        STORES.remove(level);
        if (lastLevel == level) {
            lastLevel = null;
            lastStore = null;
        }
    }

    // Server-side invalidation (the client calls invalidate/invalidateChunk from its own block-change hook).

    @SubscribeEvent
    static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            invalidate(level, event.getPos());
        }
    }

    @SubscribeEvent
    static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            invalidateChunk(level, event.getChunk().getPos().x(), event.getChunk().getPos().z());
        }
    }

    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            invalidateChunk(level, event.getChunk().getPos().x(), event.getChunk().getPos().z());
        }
    }
}
