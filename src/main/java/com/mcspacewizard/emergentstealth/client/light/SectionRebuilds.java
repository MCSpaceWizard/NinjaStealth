package com.mcspacewizard.emergentstealth.client.light;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

/**
 * Collects chunk sections whose baked light is stale and asks the renderer to rebuild them once per tick,
 * deduplicated. {@code LevelRenderer.setSectionDirty} is redirected by Sodium to its own renderer, so this
 * works with either. Main thread only.
 */
final class SectionRebuilds {
    private SectionRebuilds() {}

    private static final LongOpenHashSet PENDING = new LongOpenHashSet();

    /** Marks every section touched by a light of {@code radius} blocks around {@code center}. */
    static void markSphere(Vec3 center, int radius) {
        if (radius <= 0) {
            return;
        }
        // Light reaches cells whose centre is closer than `radius`; pad by one block for smooth-lighting
        // samples taken from neighbouring cells.
        double reach = radius + 1.0;
        int minX = SectionPos.blockToSectionCoord(Math.floor(center.x - reach));
        int maxX = SectionPos.blockToSectionCoord(Math.floor(center.x + reach));
        int minY = SectionPos.blockToSectionCoord(Math.floor(center.y - reach));
        int maxY = SectionPos.blockToSectionCoord(Math.floor(center.y + reach));
        int minZ = SectionPos.blockToSectionCoord(Math.floor(center.z - reach));
        int maxZ = SectionPos.blockToSectionCoord(Math.floor(center.z + reach));
        double reachSqr = reach * reach;
        for (int sx = minX; sx <= maxX; sx++) {
            for (int sy = minY; sy <= maxY; sy++) {
                for (int sz = minZ; sz <= maxZ; sz++) {
                    if (distanceSqrToSection(center, sx, sy, sz) < reachSqr) {
                        PENDING.add(SectionPos.asLong(sx, sy, sz));
                    }
                }
            }
        }
    }

    private static double distanceSqrToSection(Vec3 p, int sx, int sy, int sz) {
        double dx = axisDistance(p.x, sx);
        double dy = axisDistance(p.y, sy);
        double dz = axisDistance(p.z, sz);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double v, int section) {
        double min = SectionPos.sectionToBlockCoord(section);
        double max = min + 16.0;
        return v < min ? min - v : v > max ? v - max : 0.0;
    }

    static void flush(Minecraft minecraft) {
        if (PENDING.isEmpty()) {
            return;
        }
        if (minecraft.level != null) {
            for (LongIterator it = PENDING.iterator(); it.hasNext(); ) {
                long section = it.nextLong();
                minecraft.levelRenderer.setSectionDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
            }
        }
        PENDING.clear();
    }

    static void clear() {
        PENDING.clear();
    }
}
