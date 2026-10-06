package com.mcspacewizard.emergentstealth.stealth.light;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.stealth.SightRay;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * The shadow-casting light maths shared by the server's {@link ExposureModel} and the client's visual
 * lighting (doc 30), so what the player sees is computed by the same code as what the AI uses.
 * Pure: it only reads blocks through a {@link BlockGetter}, so it runs on any thread and either side.
 */
public final class LightTransport {
    private LightTransport() {}

    /** Only the strongest few sources at a point are traced; weaker ones barely change the result. */
    public static final int MAX_TRACED_SOURCES = 8;
    /** Lights further than this (in blocks) never reach a point: emission is at most 15. */
    public static final int SOURCE_RADIUS = 15;

    /** A light that could reach the point: where it is, its unshadowed strength (0-1), and its own block. */
    public record Candidate(Vec3 pos, float potential, BlockPos skip) {}

    /** Unshadowed strength of a light of {@code emission} at {@code distance}: vanilla's falloff, Euclidean. */
    public static float potential(int emission, double distance) {
        return (emission - (float) distance) / 15.0F;
    }

    /**
     * Adds a candidate if the light can reach the point at all.
     */
    public static void offer(List<Candidate> candidates, Vec3 lightPos, int emission, BlockPos skip, Vec3 point) {
        float potential = potential(emission, lightPos.distanceTo(point));
        if (potential > 0.0F) {
            candidates.add(new Candidate(lightPos, potential, skip));
        }
    }

    /**
     * Combined light at {@code point}, 0-1: the strongest {@link #MAX_TRACED_SOURCES} candidates are each traced
     * with a shadow ray and combined like light does, {@code 1 - Π(1 - potential · transmittance)}.
     * Sorts {@code candidates} in place.
     *
     * @param skipEnd a block the rays ignore at the receiving end (the cell being lit when baking), or null
     */
    public static float shadowed(BlockGetter level, Vec3 point, List<Candidate> candidates, @Nullable BlockPos skipEnd) {
        if (candidates.isEmpty()) {
            return 0.0F;
        }
        candidates.sort((a, b) -> Float.compare(b.potential(), a.potential()));

        float darkness = 1.0F;
        int traced = 0;
        for (Candidate candidate : candidates) {
            if (traced++ >= MAX_TRACED_SOURCES) {
                break;
            }
            float transmittance = SightRay.transmittance(level, candidate.pos(), point, false, candidate.skip(), skipEnd);
            if (transmittance > 0.0F) {
                darkness *= 1.0F - Math.min(1.0F, candidate.potential() * transmittance);
            }
        }
        return 1.0F - darkness;
    }

    /**
     * Light level (0-15) of a block cell as baked into terrain meshes: {@link #shadowed} at the cell's centre,
     * ignoring the cell itself (a slab or torch being lit must not shadow its own light). For an air cell this
     * equals {@code round(15 × exposure)} from the server's block-light term.
     */
    public static int cellLevel(BlockGetter level, BlockPos cell, List<Candidate> candidates) {
        float light = shadowed(level, Vec3.atCenterOf(cell), candidates, cell);
        return Math.clamp(Math.round(15.0F * light), 0, 15);
    }
}
