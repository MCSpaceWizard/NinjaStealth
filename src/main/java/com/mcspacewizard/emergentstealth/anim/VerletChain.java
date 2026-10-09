package com.mcspacewizard.emergentstealth.anim;

/**
 * A small verlet point chain (design doc 17 §4): a dragged body is the hand it is held by, then shoulders,
 * hips and feet. Point 0 is pinned to the dragger's hand; the rest fall, drag along the floor and keep their
 * segment lengths. Stepped at 20 Hz in client tick, never in rendering.
 */
public final class VerletChain {
    /** Floor height below a point, or {@code Double.NEGATIVE_INFINITY} for none. */
    @FunctionalInterface
    public interface Ground {
        double floorBelow(double x, double y, double z);
    }

    /** Blocks per tick², vanilla entity gravity. */
    public static final double GRAVITY = 0.08;

    private final int count;
    private final double[] lengths;
    final double[] pos;
    final double[] prev;
    /** Positions at the start of the last step, for partial-tick interpolation when rendering. */
    private final double[] last;
    /** How far above the floor each point rests (body thickness). */
    private final double radius;
    private boolean placed;

    public VerletChain(double radius, double... lengths) {
        this.count = lengths.length + 1;
        this.lengths = lengths.clone();
        this.pos = new double[count * 3];
        this.prev = new double[count * 3];
        this.last = new double[count * 3];
        this.radius = radius;
    }

    public int size() {
        return count;
    }

    public boolean isPlaced() {
        return placed;
    }

    /** Lays the chain out straight from {@code (x, y, z)} along direction {@code (dx, dy, dz)}, at rest. */
    public void place(double x, double y, double z, double dx, double dy, double dz) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) {
            dx = 1.0;
            dy = 0.0;
            dz = 0.0;
            len = 1.0;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        double along = 0.0;
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                along += lengths[i - 1];
            }
            pos[i * 3] = x + dx * along;
            pos[i * 3 + 1] = y + dy * along;
            pos[i * 3 + 2] = z + dz * along;
        }
        System.arraycopy(pos, 0, prev, 0, pos.length);
        System.arraycopy(pos, 0, last, 0, pos.length);
        placed = true;
    }

    /**
     * One 20 Hz step: pin point 0, integrate with gravity and damping, then satisfy lengths and the floor.
     * Points touching the floor lose most of their horizontal speed (they drag rather than glide).
     */
    public void step(double pinX, double pinY, double pinZ, Ground ground, double damping, int iterations) {
        System.arraycopy(pos, 0, last, 0, pos.length);
        for (int i = 1; i < count; i++) {
            int k = i * 3;
            double vx = (pos[k] - prev[k]) * damping;
            double vy = (pos[k + 1] - prev[k + 1]) * damping;
            double vz = (pos[k + 2] - prev[k + 2]) * damping;
            double floor = ground.floorBelow(pos[k], pos[k + 1], pos[k + 2]) + radius;
            if (pos[k + 1] <= floor + 1.0E-3) {
                vx *= 0.35; // friction
                vz *= 0.35;
            }
            prev[k] = pos[k];
            prev[k + 1] = pos[k + 1];
            prev[k + 2] = pos[k + 2];
            pos[k] += vx;
            pos[k + 1] += vy - GRAVITY;
            pos[k + 2] += vz;
        }
        prev[0] = pos[0];
        prev[1] = pos[1];
        prev[2] = pos[2];
        pos[0] = pinX;
        pos[1] = pinY;
        pos[2] = pinZ;
        for (int it = 0; it < iterations; it++) {
            for (int i = 0; i < count - 1; i++) {
                satisfy(i, i == 0);
            }
            for (int i = 1; i < count; i++) {
                int k = i * 3;
                double floor = ground.floorBelow(pos[k], pos[k + 1], pos[k + 2]) + radius;
                if (pos[k + 1] < floor) {
                    pos[k + 1] = floor;
                }
            }
        }
    }

    /** Moves points {@code i} and {@code i + 1} to their rest distance; a pinned first point doesn't move. */
    private void satisfy(int i, boolean firstPinned) {
        int a = i * 3;
        int b = a + 3;
        double dx = pos[b] - pos[a];
        double dy = pos[b + 1] - pos[a + 1];
        double dz = pos[b + 2] - pos[a + 2];
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1.0E-9) {
            return;
        }
        double diff = (d - lengths[i]) / d;
        double wa = firstPinned ? 0.0 : 0.5;
        double wb = firstPinned ? 1.0 : 0.5;
        pos[a] += dx * diff * wa;
        pos[a + 1] += dy * diff * wa;
        pos[a + 2] += dz * diff * wa;
        pos[b] -= dx * diff * wb;
        pos[b + 1] -= dy * diff * wb;
        pos[b + 2] -= dz * diff * wb;
    }

    /**
     * Nudges point {@code i} horizontally towards {@code (x, z)} by {@code strength} (0..1) of the gap, keeping
     * the motion it implies. Lets a free chain settle where the server says the body is.
     */
    public void pull(int i, double x, double z, double strength) {
        int k = i * 3;
        pos[k] += (x - pos[k]) * strength;
        pos[k + 2] += (z - pos[k + 2]) * strength;
    }

    public double x(int i) {
        return pos[i * 3];
    }

    public double y(int i) {
        return pos[i * 3 + 1];
    }

    public double z(int i) {
        return pos[i * 3 + 2];
    }

    /** Point {@code i} interpolated between the last two steps; writes {@code out[0..2]}. */
    public void lerp(int i, float partialTick, double[] out) {
        int k = i * 3;
        out[0] = last[k] + (pos[k] - last[k]) * partialTick;
        out[1] = last[k + 1] + (pos[k + 1] - last[k + 1]) * partialTick;
        out[2] = last[k + 2] + (pos[k + 2] - last[k + 2]) * partialTick;
    }

    /** Distance between neighbouring points {@code i} and {@code i + 1}. */
    public double segmentLength(int i) {
        int a = i * 3;
        int b = a + 3;
        double dx = pos[b] - pos[a];
        double dy = pos[b + 1] - pos[a + 1];
        double dz = pos[b + 2] - pos[a + 2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double restLength(int i) {
        return lengths[i];
    }
}
