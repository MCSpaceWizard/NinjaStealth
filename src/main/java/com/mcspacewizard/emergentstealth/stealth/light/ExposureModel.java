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
 * Stage 3 exposure model (design doc 13): how lit a point really is, with cast shadows.
 * <ul>
 *   <li>Block lights: the strongest nearby emitters, each traced to the point with a shadow ray.</li>
 *   <li>Dynamic lights: held light items and burning entities.</li>
 *   <li>Sky: sun or moon as a directional light (shadow ray toward it), plus diffuse sky light.</li>
 * </ul>
 * Values are cached per tick per quantised point, so many observers of one target cost the maths once.
 */
public final class ExposureModel implements LightSampler {
    public static final ExposureModel INSTANCE = new ExposureModel();

    /** Visibility multiplier at zero exposure: hard to see, not invisible. */
    public static final float FLOOR = 0.12F;

    private static final int SOURCE_RADIUS = LightTransport.SOURCE_RADIUS;

    private final Map<ServerLevel, TickCache> caches = new WeakHashMap<>();

    private static final class TickCache {
        long tick = Long.MIN_VALUE;
        final Long2FloatMap values = new Long2FloatOpenHashMap();
    }

    private ExposureModel() {}

    @Override
    public float lightFactor(ServerLevel level, Vec3 point) {
        return FLOOR + (1.0F - FLOOR) * exposure(level, point);
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
        BlockPos pos = BlockPos.containing(point);
        float skyAccess = level.getBrightness(LightLayer.SKY, pos) / 15.0F;
        if (skyAccess <= 0.0F) {
            return 0.0F;
        }
        float weather = 1.0F - 0.4F * level.getRainLevel(1.0F) - 0.3F * level.getThunderLevel(1.0F);
        // Shared with the client's sky shadows (doc 30 §10).
        float direct = skyDirect(level, point, LightTransport.SkyState.of(level, point));
        return Mth.clamp(skyAccess * direct * weather, 0.0F, 1.0F);
    }

    /** Direct sun/moon light at a point for a given sky state, before sky access and weather. */
    public static float skyDirect(ServerLevel level, Vec3 point, LightTransport.SkyState sky) {
        return LightTransport.skyDirect(level, point, sky, level.getMaxY(), null);
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
