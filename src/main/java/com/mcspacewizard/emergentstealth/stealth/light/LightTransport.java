package com.mcspacewizard.emergentstealth.stealth.light;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.stealth.SightRay;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
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

    // ------------------------------------------------------------------------------------------------
    // Sky: sun and moon as directional lights (doc 13 §1, doc 30 §10)

    /** Direct sunlight that still reaches a sky-lit spot in shade (sky glow). */
    public static final float SHADE_FRACTION = 0.3F;
    /** Night light with the moon blocked or absent. */
    public static final float NIGHT_BASE = 0.03F;
    /** Extra night light from a clear full moon. */
    public static final float MOONLIGHT = 0.25F;
    /** Rays toward the sun or moon stop after this many blocks. */
    public static final double SKY_RAY_LENGTH = 64.0;
    /** A body this low over the horizon (direction y) casts no direct light. */
    private static final double HORIZON = 0.05;

    /**
     * The sky at one moment: how much it is day (0-1), and directions toward the sun and moon with the moon's
     * brightness (1 full, 0 new). Weather is not included; callers scale by it.
     */
    public record SkyState(float day, Vec3 sun, Vec3 moon, float moonBrightness) {
        /** Current sky state at a point of a level (either side). */
        public static SkyState of(Level level, Vec3 point) {
            float skyLevel = level.environmentAttributes().getDimensionValue(EnvironmentAttributes.SKY_LIGHT_LEVEL);
            return at(skyLevel,
                    level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, point),
                    level.environmentAttributes().getValue(EnvironmentAttributes.MOON_ANGLE, point),
                    level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, point));
        }

        /** Sky state from raw values: the dimension's sky light level (0-15) and the body angles in degrees. */
        public static SkyState at(float skyLightLevel, float sunAngle, float moonAngle, MoonPhase phase) {
            float day = Mth.clamp((skyLightLevel - 4.0F) / 11.0F, 0.0F, 1.0F);
            return new SkyState(day, celestialDirection(sunAngle), celestialDirection(moonAngle), LightTransport.moonBrightness(phase));
        }

        public boolean sunUp() {
            return day > 0.0F && sun.y > HORIZON;
        }

        public boolean moonUp() {
            return day < 1.0F && moon.y > HORIZON;
        }

        /** Direct light (before sky access and weather) given how clear the rays to the sun and moon are. */
        public float direct(float sunClear, float moonClear) {
            float direct = 0.0F;
            if (day > 0.0F) {
                direct += day * (SHADE_FRACTION + (1.0F - SHADE_FRACTION) * sunClear);
            }
            if (day < 1.0F) {
                direct += (1.0F - day) * (NIGHT_BASE + MOONLIGHT * moonBrightness * moonClear);
            }
            return direct;
        }

        /** Direct light with nothing in the way: the sky's full strength right now. */
        public float directMax() {
            return direct(sunUp() ? 1.0F : 0.0F, moonUp() ? 1.0F : 0.0F);
        }
    }

    /** Direction toward a celestial body from its angle in degrees (0 = overhead; rises in the east, +X). */
    public static Vec3 celestialDirection(float angleDegrees) {
        double rad = Math.toRadians(angleDegrees);
        return new Vec3(-Math.sin(rad), Math.cos(rad), 0.0);
    }

    /** 1 at full moon, 0 at new moon. */
    public static float moonBrightness(MoonPhase phase) {
        int index = phase.index();
        return 1.0F - Math.min(index, 8 - index) / 4.0F;
    }

    /**
     * Direct sun/moon light at a point: one shadow ray toward each body that is up.
     *
     * @param ceiling the highest block Y anywhere along the rays. Rays stop once above it: from there to the sky
     *                is air, so the result is unchanged, but the client can pass a heightmap and skip most of
     *                each ray. {@code level.getMaxY()} is always correct.
     * @param skip    a block the rays ignore (the cell being lit when baking), or null
     */
    public static float skyDirect(BlockGetter level, Vec3 point, SkyState sky, int ceiling, @Nullable BlockPos skip) {
        float sunClear = sky.sunUp() ? rayToSky(level, point, sky.sun(), ceiling, skip) : 0.0F;
        float moonClear = sky.moonUp() ? rayToSky(level, point, sky.moon(), ceiling, skip) : 0.0F;
        return sky.direct(sunClear, moonClear);
    }

    private static float rayToSky(BlockGetter level, Vec3 point, Vec3 direction, int ceiling, @Nullable BlockPos skip) {
        double length = SKY_RAY_LENGTH;
        if (direction.y > 0.0) {
            double toCeiling = (ceiling + 1.0 - point.y) / direction.y;
            if (toCeiling <= 0.0) {
                return 1.0F;
            }
            length = Math.min(length, toCeiling);
        }
        return SightRay.transmittance(level, point, point.add(direction.scale(length)), false, skip, null);
    }

    /**
     * How much of the sky's current direct light reaches a cell, 0-1: {@code skyDirect / directMax}. This is
     * the gameplay shade factor (1 in the open, 0.3 in sun shade, ~0.1 in a full moon's shadow).
     */
    public static float cellSkyFactor(BlockGetter level, BlockPos cell, SkyState sky, int ceiling) {
        float max = sky.directMax();
        if (max <= 0.0F) {
            return 1.0F;
        }
        return Math.min(1.0F, skyDirect(level, Vec3.atCenterOf(cell), sky, ceiling, cell) / max);
    }

    /**
     * The sky-light level (0-15) to bake so the rendered sky term is {@code factor} times as bright as vanilla's
     * {@code vanillaSky} would be. Vanilla's lightmap brightens a level {@code v} as {@code v / (4 - 3v)}, which
     * is very non-linear, so this inverts that curve instead of scaling the level. The time of day is still applied
     * by the lightmap, so the sun's overall strength isn't counted twice (doc 30 §10).
     */
    public static int skyLevelForFactor(int vanillaSky, float factor) {
        if (vanillaSky <= 0 || factor >= 1.0F) {
            return vanillaSky;
        }
        float v = vanillaSky / 15.0F;
        float brightness = v / (4.0F - 3.0F * v) * Math.max(0.0F, factor);
        float level = 4.0F * brightness / (1.0F + 3.0F * brightness);
        return Math.clamp(Math.round(15.0F * level), 0, 15);
    }
}
