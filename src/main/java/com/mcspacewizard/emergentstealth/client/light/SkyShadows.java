package com.mcspacewizard.emergentstealth.client.light;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import com.mcspacewizard.emergentstealth.stealth.light.LightTransport;
import com.mcspacewizard.emergentstealth.stealth.light.SkyCells;
import com.mcspacewizard.emergentstealth.stealth.light.LightTransport.SkyState;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;

/**
 * Sun and moon shadows baked into the sky-light channel (doc 30 §10). Baking uses a <b>quantised</b> sky
 * state (sun/moon angle in {@code skyShadowAngleStep} steps, day factor in tenths) so terrain only re-bakes
 * when the shadows have visibly moved. Re-bakes are limited to a radius around an anchor (the camera when
 * the area was last laid out), to sections near the surface, and are fed to the renderer a few sections per
 * tick, nearest first. Main thread, except {@link #bake()}, which meshing threads read.
 */
final class SkyShadows {
    private SkyShadows() {}

    /** Width of the band at the edge of the radius over which shadows fade back to vanilla sky light. */
    private static final double FADE = 16.0;
    /** Blocks around a changed block whose sky openness is re-baked immediately. */
    private static final int OPENNESS_REBAKE_RADIUS = 6;

    /**
     * What meshing should bake right now. {@code version} changes with every new bake; together with
     * {@link #generation()} it stamps per-thread caches.
     */
    record Bake(boolean active, SkyState sky, double anchorX, double anchorZ, int radius, long version) {
        /** How much of the shadow applies at a cell: 1 inside the radius, fading to 0 at its edge. */
        float weight(BlockPos pos) {
            double dx = pos.getX() + 0.5 - anchorX;
            double dz = pos.getZ() + 0.5 - anchorZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            return (float) Math.clamp((radius - distance) / FADE, 0.0, 1.0);
        }
    }

    private record Key(long sunStep, long moonStep, MoonPhase phase, int dayTenths, int radius) {}

    private static final SkyState NOON = new SkyState(1.0F, new Vec3(0, 1, 0), new Vec3(0, -1, 0), 1.0F);
    private static volatile Bake bake = new Bake(false, NOON, 0, 0, 0, 0);
    private static final AtomicLong GENERATION = new AtomicLong();
    private static Key lastKey;
    private static final LongLinkedOpenHashSet QUEUE = new LongLinkedOpenHashSet();

    static Bake bake() {
        return bake;
    }

    /** Changes whenever a block change may have moved a sun or moon shadow. */
    static long generation() {
        return GENERATION.get();
    }

    static void tick(Minecraft minecraft, ClientLevel level, Vec3 camera, VisualSettings settings) {
        Bake current = bake;
        boolean active = settings.skyShadows() && (settings.skyShadowsWithShaders() || !ShaderPacks.inUse());
        if (!active) {
            if (current.active()) {
                // Bake vanilla sky light back in where shadows were.
                Bake off = new Bake(false, current.sky(), current.anchorX(), current.anchorZ(), current.radius(), current.version() + 1);
                bake = off;
                enqueueArea(level, current, camera);
                lastKey = null;
            }
            drain(minecraft, settings.skySectionsPerTick());
            return;
        }

        float step = settings.skyShadowAngleStep();
        float skyLevel = level.environmentAttributes().getDimensionValue(EnvironmentAttributes.SKY_LIGHT_LEVEL);
        float sunAngle = level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, camera);
        float moonAngle = level.environmentAttributes().getValue(EnvironmentAttributes.MOON_ANGLE, camera);
        MoonPhase phase = level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, camera);
        float day = Math.clamp((skyLevel - 4.0F) / 11.0F, 0.0F, 1.0F);
        Key key = new Key(Math.round(sunAngle / step), Math.round(moonAngle / step), phase, Math.round(day * 10.0F),
                settings.skyShadowRadius());

        double dx = camera.x - current.anchorX();
        double dz = camera.z - current.anchorZ();
        boolean moved = !current.active() || dx * dx + dz * dz > Math.pow(settings.skyShadowRadius() / 4.0, 2);
        if (moved || !Objects.equals(key, lastKey)) {
            lastKey = key;
            SkyState quantised = new SkyState(key.dayTenths() / 10.0F,
                    LightTransport.celestialDirection(key.sunStep() * step),
                    LightTransport.celestialDirection(key.moonStep() * step),
                    LightTransport.moonBrightness(phase));
            double anchorX = moved ? camera.x : current.anchorX();
            double anchorZ = moved ? camera.z : current.anchorZ();
            Bake next = new Bake(true, quantised, anchorX, anchorZ, settings.skyShadowRadius(), current.version() + 1);
            bake = next;
            if (current.active()) {
                enqueueArea(level, current, camera);
            }
            enqueueArea(level, next, camera);
        }
        drain(minecraft, settings.skySectionsPerTick());
    }

    /**
     * Queues the near-surface sections of every chunk the bake area touches, nearest to the camera first.
     * Sections well below a chunk's lowest surface or above its highest can't be in a sun or moon shadow
     * that changes, so they're skipped.
     */
    private static void enqueueArea(ClientLevel level, Bake area, Vec3 camera) {
        double reach = area.radius() + 1.0;
        int minCx = SectionPos.blockToSectionCoord(Math.floor(area.anchorX() - reach));
        int maxCx = SectionPos.blockToSectionCoord(Math.floor(area.anchorX() + reach));
        int minCz = SectionPos.blockToSectionCoord(Math.floor(area.anchorZ() - reach));
        int maxCz = SectionPos.blockToSectionCoord(Math.floor(area.anchorZ() + reach));
        List<long[]> sections = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                double nearestX = Math.clamp(area.anchorX(), cx * 16.0, cx * 16.0 + 16.0);
                double nearestZ = Math.clamp(area.anchorZ(), cz * 16.0, cz * 16.0 + 16.0);
                double ax = nearestX - area.anchorX();
                double az = nearestZ - area.anchorZ();
                if (ax * ax + az * az > reach * reach) {
                    continue;
                }
                int[] range = SkyCells.surfaceRange(level, cx, cz);
                if (range == null) {
                    continue; // not loaded: it bakes with the current state when it arrives
                }
                int minSy = SectionPos.blockToSectionCoord(Math.max(level.getMinY(), range[0] - 16));
                int maxSy = SectionPos.blockToSectionCoord(Math.min(level.getMaxY(), range[1] + 1));
                for (int sy = minSy; sy <= maxSy; sy++) {
                    double cxm = cx * 16.0 + 8.0 - camera.x;
                    double cym = sy * 16.0 + 8.0 - camera.y;
                    double czm = cz * 16.0 + 8.0 - camera.z;
                    sections.add(new long[] {SectionPos.asLong(cx, sy, cz), (long) (cxm * cxm + cym * cym + czm * czm)});
                }
            }
        }
        sections.sort((a, b) -> Long.compare(a[1], b[1]));
        for (long[] section : sections) {
            QUEUE.add(section[0]);
        }
    }

    private static void drain(Minecraft minecraft, int budget) {
        int sent = 0;
        for (LongIterator it = QUEUE.iterator(); it.hasNext() && sent < budget; sent++) {
            long section = it.nextLong();
            it.remove();
            minecraft.levelRenderer.setSectionDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
        }
    }

    /**
     * A block changed: cells whose sun or moon ray passes through it lie "down-ray" from it, up to the ray
     * length. Re-bake the sections along that line (main thread).
     */
    static void onBlockChanged(BlockPos pos) {
        Bake current = bake;
        // Affected cells are at most one ray length from the block, so a block that far outside the area can't matter.
        if (!current.active() || distanceToAnchor(current, pos) > current.radius() + LightTransport.SKY_RAY_LENGTH) {
            return;
        }
        GENERATION.incrementAndGet();
        Vec3 origin = Vec3.atCenterOf(pos);
        // Sky openness changes most right around (and below) the block; further cells catch up at the next
        // sun step, when the whole area re-bakes anyway.
        SectionRebuilds.markSphere(origin.add(0.0, -4.0, 0.0), OPENNESS_REBAKE_RADIUS);
        SkyState sky = current.sky();
        if (sky.sunUp()) {
            markAlong(origin, sky.sun());
        }
        if (sky.moonUp()) {
            markAlong(origin, sky.moon());
        }
    }

    private static double distanceToAnchor(Bake bake, BlockPos pos) {
        double dx = pos.getX() + 0.5 - bake.anchorX();
        double dz = pos.getZ() + 0.5 - bake.anchorZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void markAlong(Vec3 origin, Vec3 toBody) {
        for (double t = 0.0; t <= LightTransport.SKY_RAY_LENGTH; t += 4.0) {
            SectionRebuilds.markSphere(origin.subtract(toBody.scale(t)), 1);
        }
    }

    static void clear() {
        QUEUE.clear();
        lastKey = null;
        Bake current = bake;
        bake = new Bake(false, current.sky(), 0, 0, 0, current.version() + 1);
    }
}
