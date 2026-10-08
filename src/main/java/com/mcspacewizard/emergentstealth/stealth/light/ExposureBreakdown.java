package com.mcspacewizard.emergentstealth.stealth.light;

import java.util.ArrayList;
import java.util.List;

import com.mcspacewizard.emergentstealth.stealth.SightRay;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Explains an {@link ExposureModel} result for the light debug view: which sources reached the point and
 * through how much occlusion, and what the sky contributed (openness, disk visibility, altitude).
 * Debug-only (recomputes the block part; the sky part reads the same {@link SkyCells} cache as gameplay).
 */
public final class ExposureBreakdown {
    private ExposureBreakdown() {}

    /** One light source's contribution: where it is, its unoccluded strength and how much got through. */
    public record Source(Vec3 pos, float potential, float transmittance, boolean dynamic) {}

    /**
     * @param sources        block and dynamic lights considered (strongest first)
     * @param blockExposure  combined block/dynamic light exposure
     * @param skyExposure    sky exposure (diffuse + sun or moon)
     * @param openness       0-1 how much of the sky dome the point sees
     * @param celestialDir   direction toward the sun by day or the moon by night
     * @param diskVisible    0-1 how much of that body's disk is visible (fractional at shadow edges)
     * @param altitude       0-1 that body's strength from its height (twilight ramp)
     * @param skyAccess      0-1 vanilla sky light (0 means sealed off, so the sky term is skipped)
     * @param total          final exposure
     */
    public record Result(List<Source> sources, float blockExposure, float skyExposure, float openness,
                         Vec3 celestialDir, float diskVisible, float altitude, float skyAccess, float total) {}

    private static final int MAX_LISTED = 8;

    public static Result explain(ServerLevel level, Vec3 point) {
        List<Source> sources = new ArrayList<>();
        BlockPos center = BlockPos.containing(point);
        for (LightSourceIndex.Source source : LightSourceIndex.sourcesNear(level, center, LightTransport.SOURCE_RADIUS)) {
            Vec3 pos = Vec3.atCenterOf(source.pos());
            float potential = LightTransport.potential(source.emission(), pos.distanceTo(point));
            if (potential > 0.0F) {
                sources.add(new Source(pos, potential, SightRay.transmittance(level, pos, point, false, source.pos()), false));
            }
        }
        for (DynamicLights.Light light : DynamicLights.near(level, point)) {
            float potential = LightTransport.potential(light.emission(), light.pos().distanceTo(point));
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
        LightTransport.SkyState state = ExposureModel.skyState(level, point);
        LightTransport.SkySample sample = skyAccess > 0.0F ? SkyCells.sample(level, center, state) : LightTransport.SkySample.CLOSED;
        boolean day = state.day() >= 0.5F;
        Vec3 dir = day ? state.sun() : state.moon();
        float disk = day ? sample.sunVisible() : sample.moonVisible();
        float altitude = day ? state.sunStrength() : state.moonStrength();
        float total = Mth.clamp(1.0F - (1.0F - block) * (1.0F - sky), 0.0F, 1.0F);
        return new Result(listed, block, sky, sample.openness(), dir, disk, altitude, skyAccess, total);
    }
}
