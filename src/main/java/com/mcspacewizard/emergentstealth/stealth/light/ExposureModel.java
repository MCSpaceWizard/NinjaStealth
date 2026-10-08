package com.mcspacewizard.emergentstealth.stealth.light;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mcspacewizard.emergentstealth.stealth.LightSampler;

import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Exposure model (design doc 13): how lit a point really is, as perceived brightness 0-1, with cast shadows.
 * <ul>
 *   <li>Block lights: the strongest nearby emitters traced to the point (soft-edged for the strongest),
 *       falling off along vanilla's brightness curve.</li>
 *   <li>Dynamic lights: held light items and burning entities.</li>
 *   <li>Sky: diffuse light from the visible part of the sky dome (openness), plus the visible part of the sun
 *       or moon disk, weakened near the horizon.</li>
 * </ul>
 * The maths lives in {@link LightTransport} (shared with the client's visuals); per-cell sky results are cached
 * in {@link SkyCells}, and whole values per tick per quantised point.
 */
public final class ExposureModel implements LightSampler {
    public static final ExposureModel INSTANCE = new ExposureModel();

    /** Visibility multiplier at zero exposure: hard to see, not invisible. */
    public static final float FLOOR = 0.10F;
    /**
     * Exposure is raised to this before mapping to visibility, so the mid-range (shade, dusk, torch edges)
     * gives clearly graded detection instead of everything below "lit" feeling equally dark (doc 13 §1).
     */
    public static final float VISIBILITY_GAMMA = 0.75F;
    /** Sun/moon angles are rounded to this many degrees, so per-cell sun visibility can be cached. */
    public static final float SKY_ANGLE_STEP = 1.0F;

    private static final int SOURCE_RADIUS = LightTransport.SOURCE_RADIUS;

    private final Map<ServerLevel, TickCache> caches = new WeakHashMap<>();

    private static final class TickCache {
        long tick = Long.MIN_VALUE;
        final Long2FloatMap values = new Long2FloatOpenHashMap();
    }

    private ExposureModel() {}

    @Override
    public float lightFactor(ServerLevel level, Vec3 point) {
        return lightFactorFor(exposure(level, point));
    }

    /** Perception's visibility multiplier for an exposure: {@code 0.10 + 0.90 · exposure^0.75}. */
    public static float lightFactorFor(float exposure) {
        return FLOOR + (1.0F - FLOOR) * (float) Math.pow(Mth.clamp(exposure, 0.0F, 1.0F), VISIBILITY_GAMMA);
    }

    /** Exposure at a point, 0 (pitch dark) to 1 (fully lit). */
    public float exposure(ServerLevel level, Vec3 point) {
        TickCache cache = caches.computeIfAbsent(level, l -> new TickCache());
        long now = level.getGameTime();
        if (cache.tick != now) {
            cache.tick = now;
            cache.values.clear();
        }
        long key = quantise(point);
        if (cache.values.containsKey(key)) {
            return cache.values.get(key);
        }
        float value = compute(level, point);
        cache.values.put(key, value);
        return value;
    }

    /** Exposure without the per-tick cache (tests and one-off queries after block changes). */
    public static float exposureUncached(ServerLevel level, Vec3 point) {
        return compute(level, point);
    }

    /** Block and dynamic light only (no sky), uncached: the term visual lighting bakes into terrain (doc 30). */
    public static float blockExposureUncached(ServerLevel level, Vec3 point) {
        return blockExposure(level, point);
    }

    private static float compute(ServerLevel level, Vec3 point) {
        float blocks = blockExposure(level, point);
        float sky = skyExposure(level, point);
        return Mth.clamp(1.0F - (1.0F - blocks) * (1.0F - sky), 0.0F, 1.0F);
    }

    // ------------------------------------------------------------------------------------------------
    // Block + dynamic lights

    static float blockExposure(ServerLevel level, Vec3 point) {
        BlockPos center = BlockPos.containing(point);
        List<LightTransport.Candidate> candidates = new ArrayList<>();
        for (LightSourceIndex.Source source : LightSourceIndex.sourcesNear(level, center, SOURCE_RADIUS)) {
            LightTransport.offer(candidates, Vec3.atCenterOf(source.pos()), source.emission(), source.pos(), point);
        }
        for (DynamicLights.Light light : DynamicLights.near(level, point)) {
            LightTransport.offer(candidates, light.pos(), light.emission(), BlockPos.containing(light.pos()), point);
        }
        // Shared with the client's visual lighting (doc 30), so both compute the same shadows.
        return LightTransport.shadowed(level, point, candidates, null);
    }

    // ------------------------------------------------------------------------------------------------
    // Sky

    static float skyExposure(ServerLevel level, Vec3 point) {
        BlockPos cell = BlockPos.containing(point);
        if (level.getBrightness(LightLayer.SKY, cell) <= 0) {
            return 0.0F; // sealed off from the sky: no ray can reach it, so skip them
        }
        LightTransport.SkyState sky = skyState(level, point);
        // Shared with the client's baked sky light (doc 30 §10).
        return Mth.clamp(sky.light(SkyCells.sample(level, cell, sky)) * weather(level), 0.0F, 1.0F);
    }

    /** Rain dims sky light by 40%, thunder by a further 30%. */
    static float weather(ServerLevel level) {
        return 1.0F - 0.4F * level.getRainLevel(1.0F) - 0.3F * level.getThunderLevel(1.0F);
    }

    /** The sky state gameplay uses at a point (sun/moon angles rounded to {@link #SKY_ANGLE_STEP}). */
    public static LightTransport.SkyState skyState(ServerLevel level, Vec3 point) {
        return LightTransport.SkyState.quantised(level, point, SKY_ANGLE_STEP);
    }

    /**
     * What a cell sees of the sky for a given sky state, traced with full-length rays (no heightmap shortcut)
     * and no cache: the reference the visuals are tested against.
     */
    public static LightTransport.SkySample skySampleUncached(ServerLevel level, BlockPos cell, LightTransport.SkyState sky) {
        return LightTransport.sample(level, cell, sky, LightTransport.buildLimit(level));
    }

    /** Direction toward a celestial body from its angle in degrees (0 = overhead; rises in the east, +X). */
    static Vec3 celestialDirection(float angleDegrees) {
        return LightTransport.celestialDirection(angleDegrees);
    }

    private static long quantise(Vec3 point) {
        long x = Math.round(point.x * 4.0) & 0x1FFFFF;
        long y = Math.round(point.y * 4.0) & 0x3FFFF;
        long z = Math.round(point.z * 4.0) & 0x1FFFFF;
        return (x << 43) | (y << 25) | z;
    }
}
