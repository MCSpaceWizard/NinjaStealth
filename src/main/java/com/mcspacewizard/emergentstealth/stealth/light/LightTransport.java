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
 * The light maths shared by the server's {@link ExposureModel} and the client's visual lighting (doc 30), so
 * what the player sees is computed by the same code as what the AI uses. Pure: it only reads blocks through a
 * {@link BlockGetter}, so it runs on any thread and either side.
 * <p>
 * <b>Exposure means perceived brightness</b> (doc 13 §1): 0 is black, 1 is full daylight, and every value
 * renders at that fraction of full brightness through vanilla's lightmap curve ({@link #brightness}). Block
 * light falls off along that curve, and the sky is split into diffuse light from the visible part of the sky
 * dome ({@link #openness}) plus direct light from the visible part of the sun/moon disk.
 */
public final class LightTransport {
    private LightTransport() {}

    // ------------------------------------------------------------------------------------------------
    // Brightness curve

    /**
     * Perceived brightness (0-1) of a light level fraction {@code v} (level / 15): vanilla's lightmap curve
     * {@code v / (4 - 3v)}. Level 15 is 1, level 7 about 0.18, level 3 about 0.06: light pools fade gradually
     * instead of being "lit" right to their edge.
     */
    public static float brightness(float v) {
        v = Mth.clamp(v, 0.0F, 1.0F);
        return v / (4.0F - 3.0F * v);
    }

    /** Inverse of {@link #brightness}: the light level fraction that renders at {@code brightness}. */
    public static float levelFraction(float brightness) {
        brightness = Mth.clamp(brightness, 0.0F, 1.0F);
        return 4.0F * brightness / (1.0F + 3.0F * brightness);
    }

    /** Light level (0-15) to bake so it renders at {@code brightness}. */
    public static int levelFor(float brightness) {
        return Math.clamp(Math.round(15.0F * levelFraction(brightness)), 0, 15);
    }

    // ------------------------------------------------------------------------------------------------
    // Block and dynamic lights

    /** Only the strongest few sources at a point are traced; weaker ones barely change the result. */
    public static final int MAX_TRACED_SOURCES = 8;
    /** Lights further than this (in blocks) never reach a point: emission is at most 15. */
    public static final int SOURCE_RADIUS = 15;
    /** The strongest sources are traced from several points of their block, so shadow edges come out soft. */
    public static final int SOFT_SOURCES = 3;
    /** ...but only while they matter. */
    private static final float SOFT_MIN_POTENTIAL = 0.04F;
    /** Sub-ray origins: a tetrahedron around the source centre (stays inside its block). */
    private static final Vec3[] SOFT_OFFSETS = {
            new Vec3(0.25, 0.25, 0.25), new Vec3(0.25, -0.25, -0.25),
            new Vec3(-0.25, 0.25, -0.25), new Vec3(-0.25, -0.25, 0.25)};

    /** A light that could reach the point: where it is, its unshadowed strength (0-1), and its own block. */
    public record Candidate(Vec3 pos, float potential, BlockPos skip) {}

    /**
     * Unshadowed brightness of a light of {@code emission} at {@code distance}: vanilla's level falloff
     * (one level per block, Euclidean) through the brightness curve.
     */
    public static float potential(int emission, double distance) {
        float level = emission - (float) distance;
        return level <= 0.0F ? 0.0F : brightness(level / 15.0F);
    }

    /** Adds a candidate if the light can reach the point at all. */
    public static void offer(List<Candidate> candidates, Vec3 lightPos, int emission, BlockPos skip, Vec3 point) {
        float potential = potential(emission, lightPos.distanceTo(point));
        if (potential > 0.0F) {
            candidates.add(new Candidate(lightPos, potential, skip));
        }
    }

    /**
     * Combined light at {@code point}, 0-1: the strongest {@link #MAX_TRACED_SOURCES} candidates are each traced
     * with shadow rays (the strongest {@link #SOFT_SOURCES} with four, for soft edges) and combined like light
     * does, {@code 1 - Π(1 - potential · transmittance)}. Sorts {@code candidates} in place.
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
            if (traced >= MAX_TRACED_SOURCES) {
                break;
            }
            float transmittance = traced < SOFT_SOURCES && candidate.potential() >= SOFT_MIN_POTENTIAL
                    ? softTransmittance(level, candidate, point, skipEnd)
                    : SightRay.transmittance(level, candidate.pos(), point, false, candidate.skip(), skipEnd);
            traced++;
            if (transmittance > 0.0F) {
                darkness *= 1.0F - Math.min(1.0F, candidate.potential() * transmittance);
            }
        }
        return 1.0F - darkness;
    }

    /** Mean transmittance of rays from four points spread over the source's block: partial at shadow edges. */
    private static float softTransmittance(BlockGetter level, Candidate candidate, Vec3 point, @Nullable BlockPos skipEnd) {
        float sum = 0.0F;
        for (Vec3 offset : SOFT_OFFSETS) {
            sum += SightRay.transmittance(level, candidate.pos().add(offset), point, false, candidate.skip(), skipEnd);
        }
        return sum / SOFT_OFFSETS.length;
    }

    /**
     * Light level (0-15) of a block cell as baked into terrain meshes: the level that renders at the
     * {@link #shadowed} brightness at the cell's centre, ignoring the cell itself (a slab or torch being lit must
     * not shadow its own light). A single unshadowed source gives exactly vanilla's level (emission − distance).
     */
    public static int cellLevel(BlockGetter level, BlockPos cell, List<Candidate> candidates) {
        return levelFor(shadowed(level, Vec3.atCenterOf(cell), candidates, cell));
    }

    // ------------------------------------------------------------------------------------------------
    // Sky: diffuse light from the visible sky dome, direct light from the sun and moon disks (doc 13 §1)

    /** Diffuse daylight from a fully open sky, as a share of full sun. */
    public static final float SHADE_FRACTION = 0.3F;
    /** Diffuse night light from a fully open sky (stars, airglow). */
    public static final float NIGHT_BASE = 0.03F;
    /** Direct light from a clear full moon high in the sky. */
    public static final float MOONLIGHT = 0.25F;
    /** Rays toward the sun or moon stop after this many blocks. */
    public static final double SKY_RAY_LENGTH = 64.0;
    /** Rays measuring sky openness stop after this many blocks: openness is about the local surroundings. */
    public static final double OPENNESS_RAY_LENGTH = 20.0;
    /** Below this height over the horizon (direction y, ~3°) a body gives no direct light. */
    private static final double HORIZON = 0.05;
    /** Direct light reaches full strength once the body is this high (direction y, ~20°): a twilight ramp. */
    private static final double FULL_ALTITUDE = 0.34;
    /**
     * Angular radius of the sun and moon disks as traced (degrees). The real disks are ~0.27°; a wider disk gives
     * shadow edges a readable half-block-or-so penumbra.
     */
    public static final double DISK_RADIUS_DEGREES = 3.0;

    /** Sky-dome sample directions (zenith, a ring at 60° and a ring at 30°) and their cosine weights. */
    private static final Vec3[] DOME;
    private static final float[] DOME_WEIGHTS;
    private static final float DOME_WEIGHT_SUM;

    static {
        int[] perRing = {1, 4, 8};
        double[] elevations = {90.0, 60.0, 30.0};
        double[] azimuthOffsets = {0.0, 45.0, 22.5};
        int count = 1 + 4 + 8;
        DOME = new Vec3[count];
        DOME_WEIGHTS = new float[count];
        int i = 0;
        float sum = 0.0F;
        for (int ring = 0; ring < 3; ring++) {
            double elevation = Math.toRadians(elevations[ring]);
            for (int k = 0; k < perRing[ring]; k++) {
                double azimuth = Math.toRadians(azimuthOffsets[ring] + 360.0 * k / perRing[ring]);
                DOME[i] = new Vec3(Math.cos(elevation) * Math.cos(azimuth), Math.sin(elevation), Math.cos(elevation) * Math.sin(azimuth));
                DOME_WEIGHTS[i] = (float) Math.sin(elevation);
                sum += DOME_WEIGHTS[i];
                i++;
            }
        }
        DOME_WEIGHT_SUM = sum;
    }

    /**
     * The sky at one moment: how much it is day (0-1), and directions toward the sun and moon with the moon's
     * brightness (1 full, 0 new). Weather is not included; callers scale by it.
     */
    public record SkyState(float day, Vec3 sun, Vec3 moon, float moonBrightness) {
        /** Current sky state at a point of a level (either side). */
        public static SkyState of(Level level, Vec3 point) {
            return quantised(level, point, 0.0F);
        }

        /**
         * Current sky state with the sun and moon angles rounded to {@code stepDegrees} (0 = exact), so
         * per-cell sun visibility can be cached until the sun has visibly moved.
         */
        public static SkyState quantised(Level level, Vec3 point, float stepDegrees) {
            float skyLevel = level.environmentAttributes().getDimensionValue(EnvironmentAttributes.SKY_LIGHT_LEVEL);
            float sun = level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, point);
            float moon = level.environmentAttributes().getValue(EnvironmentAttributes.MOON_ANGLE, point);
            if (stepDegrees > 0.0F) {
                sun = Math.round(sun / stepDegrees) * stepDegrees;
                moon = Math.round(moon / stepDegrees) * stepDegrees;
            }
            return at(skyLevel, sun, moon, level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, point));
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

        /** Direct sunlight strength from the sun's height: 0 at the horizon, 1 from ~20° up (smooth). */
        public float sunStrength() {
            return sunUp() ? altitude(sun.y) : 0.0F;
        }

        public float moonStrength() {
            return moonUp() ? altitude(moon.y) : 0.0F;
        }

        /**
         * Sky light (before weather) at a spot with this much of the sky dome and of each disk visible:
         * {@code day × (0.3·openness + 0.7·sunStrength·sunVisible) + night × (0.03·openness + 0.25·moon·moonStrength·moonVisible)}.
         */
        public float light(float openness, float sunVisible, float moonVisible) {
            float light = 0.0F;
            if (day > 0.0F) {
                light += day * (SHADE_FRACTION * openness + (1.0F - SHADE_FRACTION) * sunStrength() * sunVisible);
            }
            if (day < 1.0F) {
                light += (1.0F - day) * (NIGHT_BASE * openness + MOONLIGHT * moonBrightness * moonStrength() * moonVisible);
            }
            return light;
        }

        public float light(SkySample sample) {
            return light(sample.openness(), sample.sunVisible(), sample.moonVisible());
        }

        /** Sky light in a fully open spot with nothing in front of the sun or moon: the sky's full strength now. */
        public float max() {
            return light(1.0F, 1.0F, 1.0F);
        }
    }

    private static float altitude(double y) {
        float t = (float) Mth.clamp((y - HORIZON) / (FULL_ALTITUDE - HORIZON), 0.0, 1.0);
        return t * t * (3.0F - 2.0F * t);
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
     * How high blocks can be along a sky ray: rays stop once above it, since from there up there is only air.
     * The result is unchanged; it only saves work. {@link #buildLimit} is always correct; a heightmap-based
     * ceiling ({@link SkyCells#ceiling}) is much tighter in the open.
     */
    @FunctionalInterface
    public interface SkyCeiling {
        int along(Vec3 from, Vec3 direction, double length);
    }

    public static SkyCeiling buildLimit(BlockGetter level) {
        return (from, direction, length) -> level.getMaxY();
    }

    /** What a cell sees of the sky: openness of the dome, and the visible fraction of each disk (0-1). */
    public record SkySample(float openness, float sunVisible, float moonVisible) {
        public static final SkySample CLOSED = new SkySample(0.0F, 0.0F, 0.0F);
    }

    /** Transmittance of one ray toward the sky, cut short above the ceiling. */
    public static float skyRay(BlockGetter level, Vec3 from, Vec3 direction, double length, SkyCeiling ceiling,
            @Nullable BlockPos skip) {
        if (direction.y > 0.0) {
            int top = ceiling.along(from, direction, length);
            double toCeiling = (top + 1.0 - from.y) / direction.y;
            if (toCeiling <= 0.0) {
                return 1.0F;
            }
            length = Math.min(length, toCeiling);
        }
        return SightRay.transmittance(level, from, from.add(direction.scale(length)), false, skip, null);
    }

    /**
     * Fraction of the sky dome visible from a cell's centre (cosine-weighted, 13 rays of up to
     * {@link #OPENNESS_RAY_LENGTH} blocks; leaves and fences let part through). 1 in an open field, about
     * 0.5–0.7 against a wall, lower in alleys, doorways and under eaves or trees, 0 indoors.
     */
    public static float openness(BlockGetter level, BlockPos cell, SkyCeiling ceiling) {
        Vec3 from = Vec3.atCenterOf(cell);
        float sum = 0.0F;
        for (int i = 0; i < DOME.length; i++) {
            sum += DOME_WEIGHTS[i] * skyRay(level, from, DOME[i], OPENNESS_RAY_LENGTH, ceiling, cell);
        }
        return sum / DOME_WEIGHT_SUM;
    }

    /**
     * Visible fraction of a sun or moon disk from a cell's centre: the centre ray plus four rays at the disk's
     * edge, so a cell at a shadow's edge is partly lit (penumbra).
     */
    public static float diskVisible(BlockGetter level, BlockPos cell, Vec3 direction, SkyCeiling ceiling) {
        Vec3 from = Vec3.atCenterOf(cell);
        // Two directions across the disk: along the body's arc and sideways (bodies move in the X-Y plane).
        Vec3 across = new Vec3(-direction.y, direction.x, 0.0).normalize();
        Vec3 side = new Vec3(0.0, 0.0, 1.0);
        double r = Math.tan(Math.toRadians(DISK_RADIUS_DEGREES));
        float sum = skyRay(level, from, direction, SKY_RAY_LENGTH, ceiling, cell);
        sum += skyRay(level, from, direction.add(across.scale(r)).normalize(), SKY_RAY_LENGTH, ceiling, cell);
        sum += skyRay(level, from, direction.add(across.scale(-r)).normalize(), SKY_RAY_LENGTH, ceiling, cell);
        sum += skyRay(level, from, direction.add(side.scale(r)).normalize(), SKY_RAY_LENGTH, ceiling, cell);
        sum += skyRay(level, from, direction.add(side.scale(-r)).normalize(), SKY_RAY_LENGTH, ceiling, cell);
        return sum / 5.0F;
    }

    /** Full sky sample of a cell for a sky state (disks only traced while up). */
    public static SkySample sample(BlockGetter level, BlockPos cell, SkyState sky, SkyCeiling ceiling) {
        return new SkySample(openness(level, cell, ceiling),
                sky.sunUp() ? diskVisible(level, cell, sky.sun(), ceiling) : 0.0F,
                sky.moonUp() ? diskVisible(level, cell, sky.moon(), ceiling) : 0.0F);
    }

    /**
     * Share of the sky's current full strength reaching a cell, 0-1 (1 in an open, sunlit field). This is what
     * the visuals bake: vanilla's lightmap already applies time of day and weather on top (doc 30 §10).
     */
    public static float skyFactor(SkyState sky, SkySample sample) {
        float max = sky.max();
        return max <= 0.0F ? 1.0F : Math.min(1.0F, sky.light(sample) / max);
    }
}
