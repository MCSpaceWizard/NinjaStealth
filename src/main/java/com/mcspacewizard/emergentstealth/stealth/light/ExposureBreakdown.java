package com.mcspacewizard.emergentstealth.stealth.light;

import java.util.ArrayList;
import java.util.List;

import com.mcspacewizard.emergentstealth.stealth.SightRay;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Explains an {@link ExposureModel} result for the light debug view: which sources reached the point and
 * through how much occlusion, and what the sky contributed. Debug-only (recomputes, no cache).
 */
public final class ExposureBreakdown {
    private ExposureBreakdown() {}

    /** One light source's contribution: where it is, its unoccluded strength and how much got through. */
    public record Source(Vec3 pos, float potential, float transmittance, boolean dynamic) {}

    /**
     * @param sources       block and dynamic lights considered (strongest first)
     * @param blockExposure combined block/dynamic light exposure
     * @param skyExposure   sky exposure (sun or moon + diffuse)
     * @param skyAccess     0-1 how open to the sky the point is
     * @param celestialDir  direction toward the sun by day or the moon by night
     * @param celestialClear 0-1 whether a ray toward that body is clear
     * @param total         final exposure
     */
    public record Result(List<Source> sources, float blockExposure, float skyExposure, float skyAccess,
                         Vec3 celestialDir, float celestialClear, float total) {}

    private static final int MAX_LISTED = 8;

    public static Result explain(ServerLevel level, Vec3 point) {
        List<Source> sources = new ArrayList<>();
        BlockPos center = BlockPos.containing(point);
        for (LightSourceIndex.Source source : LightSourceIndex.sourcesNear(level, center, 15)) {
            Vec3 pos = Vec3.atCenterOf(source.pos());
            float potential = (source.emission() - (float) pos.distanceTo(point)) / 15.0F;
            if (potential > 0.0F) {
                sources.add(new Source(pos, potential, SightRay.transmittance(level, pos, point, false, source.pos()), false));
            }
        }
        for (DynamicLights.Light light : DynamicLights.near(level, point)) {
            float potential = (light.emission() - (float) light.pos().distanceTo(point)) / 15.0F;
            if (potential > 0.0F) {
                sources.add(new Source(light.pos(), potential,
                        SightRay.transmittance(level, light.pos(), point, false, BlockPos.containing(light.pos())), true));
            }
        }
        sources.sort((a, b) -> Float.compare(b.potential(), a.potential()));
        List<Source> listed = sources.size() > MAX_LISTED ? List.copyOf(sources.subList(0, MAX_LISTED)) : List.copyOf(sources);

        float block = ExposureModel.blockExposure(level, point);
        float sky = ExposureModel.skyExposure(level, point);
        float skyAccess = level.getBrightness(LightLayer.SKY, center) / 15.0F;
        float skyLevel = level.environmentAttributes().getDimensionValue(EnvironmentAttributes.SKY_LIGHT_LEVEL);
        boolean day = skyLevel > 4.0F + 5.5F;
        Vec3 dir = ExposureModel.celestialDirection(level.environmentAttributes()
                .getValue(day ? EnvironmentAttributes.SUN_ANGLE : EnvironmentAttributes.MOON_ANGLE, point));
        float clear = dir.y > 0.05 ? SightRay.transmittance(level, point, point.add(dir.scale(64.0)), false) : 0.0F;
        float total = Mth.clamp(1.0F - (1.0F - block) * (1.0F - sky), 0.0F, 1.0F);
        return new Result(listed, block, sky, skyAccess, dir, clear, total);
    }
}
