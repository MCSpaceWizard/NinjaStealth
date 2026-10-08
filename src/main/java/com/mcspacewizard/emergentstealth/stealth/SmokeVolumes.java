package com.mcspacewizard.emergentstealth.stealth;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Active smoke clouds per level (design doc 21 §2): spheres that block sight completely until they expire.
 * {@link SightRay} asks {@link #blocksSight} before walking any blocks, so every sight ray (perception, evidence,
 * light) is blocked by smoke without knowing about it.
 *
 * <p>Cost when no smoke exists anywhere: one volatile read per ray. With smoke: a segment-sphere test per active
 * volume in that level. Reads are lock-free (each level's volumes are an immutable array swapped on change), so
 * the hot path never synchronises. Writes happen on the server thread.
 */
public final class SmokeVolumes {
    private SmokeVolumes() {}

    /**
     * One smoke sphere.
     *
     * @param id        handle for {@link #remove}
     * @param center    centre of the sphere
     * @param radius    radius in blocks
     * @param expiresAt game time at which it stops blocking sight
     */
    public record Volume(long id, Vec3 center, float radius, long expiresAt) {
        public boolean active(long gameTime) {
            return gameTime < expiresAt;
        }

        public boolean contains(Vec3 point) {
            return point.distanceToSqr(center) <= (double) radius * radius;
        }

        /** Whether the segment from {@code a} to {@code b} passes through (or starts or ends inside) this sphere. */
        public boolean intersects(Vec3 a, Vec3 b) {
            return segmentHitsSphere(a, b, center, radius);
        }
    }

    private record LevelVolumes(Level level, Volume[] volumes) {}

    private static final Volume[] NONE = new Volume[0];
    private static final AtomicLong NEXT_ID = new AtomicLong(1);
    /** One entry per level that has (or recently had) smoke. Copy-on-write; tiny (one per dimension at most). */
    private static volatile LevelVolumes[] levels = new LevelVolumes[0];
    /** Total volumes across levels: the hot-path "no smoke anywhere" check. */
    private static volatile int total;

    // ------------------------------------------------------------------------------------------------
    // Queries (any thread)

    /** True if a sight ray from {@code from} to {@code to} passes through active smoke in this level. */
    public static boolean blocksSight(BlockGetter getter, Vec3 from, Vec3 to) {
        if (total == 0 || !(getter instanceof Level level)) {
            return false;
        }
        Volume[] volumes = volumesOf(level);
        if (volumes.length == 0) {
            return false;
        }
        long now = level.getGameTime();
        for (Volume volume : volumes) {
            if (volume.active(now) && volume.intersects(from, to)) {
                return true;
            }
        }
        return false;
    }

    /** True if the point is inside active smoke. */
    public static boolean isInSmoke(Level level, Vec3 point) {
        if (total == 0) {
            return false;
        }
        long now = level.getGameTime();
        for (Volume volume : volumesOf(level)) {
            if (volume.active(now) && volume.contains(point)) {
                return true;
            }
        }
        return false;
    }

    /** The active volumes in a level (a snapshot; empty if none). */
    public static List<Volume> active(Level level) {
        if (total == 0) {
            return List.of();
        }
        long now = level.getGameTime();
        List<Volume> out = new ArrayList<>();
        for (Volume volume : volumesOf(level)) {
            if (volume.active(now)) {
                out.add(volume);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------
    // Changes (server thread)

    /** Adds a smoke sphere that blocks sight until {@code expiresAt} (game time). Returns its handle. */
    public static synchronized Volume add(Level level, Vec3 center, float radius, long expiresAt) {
        Volume volume = new Volume(NEXT_ID.getAndIncrement(), center, radius, expiresAt);
        Volume[] current = volumesOf(level);
        Volume[] next = new Volume[current.length + 1];
        System.arraycopy(current, 0, next, 0, current.length);
        next[current.length] = volume;
        put(level, next);
        return volume;
    }

    /** Removes a volume early (e.g. its cloud entity unloaded). No-op if it's already gone. */
    public static synchronized void remove(Level level, long id) {
        Volume[] current = volumesOf(level);
        int keep = 0;
        for (Volume volume : current) {
            if (volume.id() != id) {
                keep++;
            }
        }
        if (keep == current.length) {
            return;
        }
        Volume[] next = new Volume[keep];
        int i = 0;
        for (Volume volume : current) {
            if (volume.id() != id) {
                next[i++] = volume;
            }
        }
        put(level, next);
    }

    /** Drops expired volumes in this level. Called once a tick per level by the toolkit. */
    public static synchronized void prune(Level level) {
        Volume[] current = volumesOf(level);
        if (current.length == 0) {
            return;
        }
        long now = level.getGameTime();
        int keep = 0;
        for (Volume volume : current) {
            if (volume.active(now)) {
                keep++;
            }
        }
        if (keep == current.length) {
            return;
        }
        Volume[] next = new Volume[keep];
        int i = 0;
        for (Volume volume : current) {
            if (volume.active(now)) {
                next[i++] = volume;
            }
        }
        put(level, next);
    }

    /** Forgets a level (unloaded). */
    public static synchronized void clear(Level level) {
        put(level, NONE);
    }

    // ------------------------------------------------------------------------------------------------

    private static Volume[] volumesOf(Level level) {
        for (LevelVolumes entry : levels) {
            if (entry.level() == level) {
                return entry.volumes();
            }
        }
        return NONE;
    }

    /** Replaces a level's volumes; levels with none are dropped from the table. Caller holds the lock. */
    private static void put(Level level, Volume[] volumes) {
        List<LevelVolumes> next = new ArrayList<>();
        int count = 0;
        for (LevelVolumes entry : levels) {
            if (entry.level() != level && entry.volumes().length > 0) {
                next.add(entry);
                count += entry.volumes().length;
            }
        }
        if (volumes.length > 0) {
            next.add(new LevelVolumes(level, volumes));
            count += volumes.length;
        }
        levels = next.toArray(LevelVolumes[]::new);
        total = count;
    }

    /** Segment-sphere test: does any point of segment ab lie within {@code radius} of {@code c}? */
    public static boolean segmentHitsSphere(Vec3 a, Vec3 b, Vec3 c, double radius) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        double fx = a.x - c.x;
        double fy = a.y - c.y;
        double fz = a.z - c.z;
        double lenSq = dx * dx + dy * dy + dz * dz;
        double t = lenSq < 1.0E-9 ? 0.0 : -(fx * dx + fy * dy + fz * dz) / lenSq;
        if (t < 0.0) {
            t = 0.0;
        } else if (t > 1.0) {
            t = 1.0;
        }
        double px = fx + dx * t;
        double py = fy + dy * t;
        double pz = fz + dz * t;
        return px * px + py * py + pz * pz <= radius * radius;
    }

    /** For tests and the debug command: the volume with this id in a level, or null. */
    public static @Nullable Volume find(Level level, long id) {
        for (Volume volume : volumesOf(level)) {
            if (volume.id() == id) {
                return volume;
            }
        }
        return null;
    }
}
