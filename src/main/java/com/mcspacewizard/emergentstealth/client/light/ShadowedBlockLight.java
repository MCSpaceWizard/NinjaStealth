package com.mcspacewizard.emergentstealth.client.light;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.stealth.light.LightSourceIndex.Source;
import com.mcspacewizard.emergentstealth.stealth.light.LightTransport;
import com.mcspacewizard.emergentstealth.stealth.light.SkyCells;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

/**
 * The shadow-casting light level of one block cell, from static emitters and dynamic lights, computed with
 * the server's shared {@link LightTransport} maths (doc 30 §3). Called from chunk-meshing worker threads and
 * the main thread; every thread keeps its own cache, stamped with the source generation and the dynamic-light
 * version so it stays valid across section compiles and empties itself when anything relevant changes.
 */
final class ShadowedBlockLight {
    private ShadowedBlockLight() {}

    /** Flag bit in a cached value: too many static emitters here, so static light comes from vanilla. */
    static final int DENSE = 0x10;
    private static final int MAX_CACHED_CELLS = 1 << 16;
    private static final int HOOD_SLOTS = 4;

    private static final class ThreadCache {
        @Nullable ClientLevel level;
        long generation = -1;
        long dynamicVersion = -1;
        boolean statics;
        boolean dynamics;
        final Long2ByteOpenHashMap cells = new Long2ByteOpenHashMap();

        /** A few recent sections' source neighbourhoods: smooth-lighting samples straddle section borders. */
        final long[] hoodKeys = new long[HOOD_SLOTS];
        final @Nullable List<?>[] hoods = new List<?>[HOOD_SLOTS];
        final boolean[] hoodValid = new boolean[HOOD_SLOTS];
        int hoodNext;
        final List<LightTransport.Candidate> scratch = new ArrayList<>();
    }

    private static final ThreadLocal<ThreadCache> CACHE = ThreadLocal.withInitial(ThreadCache::new);

    /**
     * Model light at {@code pos}: bits 0-3 are the level (0-15), {@link #DENSE} marks a cell whose static
     * light must come from vanilla. Rays read {@code level} directly (see class comment in {@link VisualLighting}).
     */
    static int cell(ClientLevel level, BlockPos pos, boolean statics, boolean dynamics) {
        ThreadCache cache = CACHE.get();
        long generation = ClientLightSources.generation();
        ClientDynamicLights.Snapshot snapshot = ClientDynamicLights.snapshot();
        if (cache.level != level || cache.generation != generation || cache.dynamicVersion != snapshot.version()
                || cache.statics != statics || cache.dynamics != dynamics || cache.cells.size() > MAX_CACHED_CELLS) {
            cache.cells.clear();
            Arrays.fill(cache.hoodValid, false);
            cache.level = level;
            cache.generation = generation;
            cache.dynamicVersion = snapshot.version();
            cache.statics = statics;
            cache.dynamics = dynamics;
        }
        long key = pos.asLong();
        if (cache.cells.containsKey(key)) {
            return cache.cells.get(key);
        }
        int value = compute(cache, level, pos, statics, dynamics ? snapshot.lights() : List.of());
        cache.cells.put(key, (byte) value);
        return value;
    }

    /**
     * Gameplay sky factor at a cell (0-1, see {@link LightTransport#skyFactor}) for the current quantised bake:
     * sky openness plus the visible part of the sun/moon disk. The per-cell rays are cached in the shared
     * {@link SkyCells} store (openness until a nearby block changes, disks until the sun steps).
     */
    static float skyFactor(ClientLevel level, BlockPos pos, SkyShadows.Bake bake) {
        return LightTransport.skyFactor(bake.sky(), SkyCells.sample(level, pos, bake.sky()));
    }

    private static int compute(ThreadCache cache, ClientLevel level, BlockPos pos, boolean statics, List<ClientDynamicLights.Light> lights) {
        List<LightTransport.Candidate> candidates = cache.scratch;
        candidates.clear();
        Vec3 point = Vec3.atCenterOf(pos);
        int flags = 0;
        if (statics) {
            List<Source> sources = neighbourhood(cache, level, pos);
            if (sources == null) {
                flags = DENSE;
            } else {
                for (Source source : sources) {
                    int emission = source.emission();
                    if (source.pos().distSqr(pos) < (double) emission * emission) {
                        LightTransport.offer(candidates, Vec3.atCenterOf(source.pos()), emission, source.pos(), point);
                    }
                }
            }
        }
        for (ClientDynamicLights.Light light : lights) {
            LightTransport.offer(candidates, light.pos(), light.emission(), light.block(), point);
        }
        if (candidates.isEmpty()) {
            return flags;
        }
        return flags | LightTransport.cellLevel(level, pos, candidates);
    }

    @SuppressWarnings("unchecked")
    private static @Nullable List<Source> neighbourhood(ThreadCache cache, ClientLevel level, BlockPos pos) {
        long section = SectionPos.asLong(pos);
        for (int i = 0; i < HOOD_SLOTS; i++) {
            if (cache.hoodValid[i] && cache.hoodKeys[i] == section) {
                return (List<Source>) cache.hoods[i];
            }
        }
        List<Source> hood = ClientLightSources.neighbourhood(level, SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
        int slot = cache.hoodNext;
        cache.hoodNext = (slot + 1) % HOOD_SLOTS;
        cache.hoodKeys[slot] = section;
        cache.hoods[slot] = hood;
        cache.hoodValid[slot] = true;
        return hood;
    }
}
