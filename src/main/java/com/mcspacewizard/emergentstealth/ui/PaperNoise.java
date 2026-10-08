package com.mcspacewizard.emergentstealth.ui;

/**
 * Deterministic, tileable value noise for Sumi's procedural paper grain and ink speckle (design doc 31 §0).
 * Every function wraps at {@code period} pixels, so textures built from it tile without seams.
 */
public final class PaperNoise {
    private PaperNoise() {}

    /** A hash of a lattice point, in [0, 1). */
    public static float hash(int x, int y, long seed) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + y * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** Smooth value noise with lattice cells of {@code cell} pixels, wrapping at {@code period} pixels. In [0, 1). */
    public static float value(float x, float y, int cell, int period, long seed) {
        int cells = Math.max(1, period / Math.max(1, cell));
        float fx = x / cell;
        float fy = y / cell;
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        float tx = smooth(fx - x0);
        float ty = smooth(fy - y0);
        float a = hash(Math.floorMod(x0, cells), Math.floorMod(y0, cells), seed);
        float b = hash(Math.floorMod(x0 + 1, cells), Math.floorMod(y0, cells), seed);
        float c = hash(Math.floorMod(x0, cells), Math.floorMod(y0 + 1, cells), seed);
        float d = hash(Math.floorMod(x0 + 1, cells), Math.floorMod(y0 + 1, cells), seed);
        float top = a + (b - a) * tx;
        float bottom = c + (d - c) * tx;
        return top + (bottom - top) * ty;
    }

    /** Fractal noise: {@code octaves} layers, each half the cell size and half the weight. In [0, 1). */
    public static float fbm(float x, float y, int cell, int period, long seed, int octaves) {
        float sum = 0.0F;
        float weight = 1.0F;
        float total = 0.0F;
        int c = cell;
        for (int i = 0; i < octaves && c >= 1; i++) {
            sum += value(x, y, c, period, seed + i * 7919L) * weight;
            total += weight;
            weight *= 0.5F;
            c /= 2;
        }
        return total == 0.0F ? 0.0F : sum / total;
    }

    /** A 1D noise in [-1, 1] for brush stroke wobble. */
    public static float wobble(float t, long seed) {
        int i = (int) Math.floor(t);
        float f = smooth(t - i);
        float a = hash(i, 0, seed) * 2.0F - 1.0F;
        float b = hash(i + 1, 0, seed) * 2.0F - 1.0F;
        return a + (b - a) * f;
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }
}
