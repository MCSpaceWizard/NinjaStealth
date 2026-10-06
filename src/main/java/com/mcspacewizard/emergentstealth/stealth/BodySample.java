package com.mcspacewizard.emergentstealth.stealth;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

/**
 * A point on a target's body that observers try to see, with how much of the body it represents.
 * Points follow the pose: crouching lowers them, crawling/swimming puts them all near the ground.
 */
public record BodySample(Part part, Vec3 position, float weight) {
    public enum Part { HEAD, CHEST, HIPS, LEFT_FOOT, RIGHT_FOOT }

    /**
     * @param full false for the cheaper head + chest set used by distant (tier 2) observers;
     *             weights are rescaled so the total stays 1
     */
    public static List<BodySample> of(LivingEntity target, boolean full) {
        Vec3 base = target.position();
        float height = target.getBbHeight();
        boolean lowPose = target.getPose() == Pose.SWIMMING || target.isVisuallyCrawling() || height < 1.0F;
        // Sideways offset for the feet, perpendicular to the body's facing.
        float yawRad = target.yBodyRot * ((float) Math.PI / 180F);
        Vec3 side = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad)).scale(0.18);

        List<BodySample> samples = new ArrayList<>(5);
        if (lowPose) {
            // Lying flat: points spread along the body at ground level.
            Vec3 forward = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad)).scale(0.6);
            double y = 0.3;
            samples.add(new BodySample(Part.HEAD, base.add(forward).add(0, y, 0), full ? 0.3F : 0.5F));
            samples.add(new BodySample(Part.CHEST, base.add(0, y, 0), full ? 0.3F : 0.5F));
            if (full) {
                samples.add(new BodySample(Part.HIPS, base.subtract(forward.scale(0.5)).add(0, y, 0), 0.2F));
                samples.add(new BodySample(Part.LEFT_FOOT, base.subtract(forward).add(side).add(0, 0.15, 0), 0.1F));
                samples.add(new BodySample(Part.RIGHT_FOOT, base.subtract(forward).subtract(side).add(0, 0.15, 0), 0.1F));
            }
            return samples;
        }

        double eye = target.getEyeHeight();
        samples.add(new BodySample(Part.HEAD, base.add(0, eye, 0), full ? 0.3F : 0.5F));
        samples.add(new BodySample(Part.CHEST, base.add(0, height * 0.62, 0), full ? 0.3F : 0.5F));
        if (full) {
            samples.add(new BodySample(Part.HIPS, base.add(0, height * 0.42, 0), 0.2F));
            samples.add(new BodySample(Part.LEFT_FOOT, base.add(side).add(0, 0.15, 0), 0.1F));
            samples.add(new BodySample(Part.RIGHT_FOOT, base.subtract(side).add(0, 0.15, 0), 0.1F));
        }
        return samples;
    }
}
