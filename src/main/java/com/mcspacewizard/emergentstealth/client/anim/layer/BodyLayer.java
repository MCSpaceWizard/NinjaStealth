package com.mcspacewizard.emergentstealth.client.anim.layer;

import com.mcspacewizard.emergentstealth.action.BodyState;
import com.mcspacewizard.emergentstealth.action.CarryLink;
import com.mcspacewizard.emergentstealth.anim.Bone;
import com.mcspacewizard.emergentstealth.anim.BodyPoses;
import com.mcspacewizard.emergentstealth.anim.HumanoidPose;
import com.mcspacewizard.emergentstealth.anim.VerletChain;
import com.mcspacewizard.emergentstealth.client.anim.PoseContext;
import com.mcspacewizard.emergentstealth.client.anim.PoseLayer;
import com.mcspacewizard.emergentstealth.client.anim.ProceduralState;
import com.mcspacewizard.emergentstealth.config.ESConfig;

/**
 * Limbs of a body (design doc 17 §2 and §4). Lying: a slack knocked-out pose or a limp corpse, varied per body
 * ({@link BodyPoses}). Dragged: arms stretched overhead to the dragger's hand, legs trailing the drag chain.
 * Carried: everything dangles. The renderer lays the whole model down ({@code BodyPlacement}); this only moves
 * the limbs, and fades in with the fall.
 */
public final class BodyLayer implements PoseLayer {
    private static final float ARMS_OVERHEAD = -2.95F;
    private static final float DANGLE = -1.5F;

    @Override
    public int priority() {
        return 300;
    }

    @Override
    public float weight(PoseContext context) {
        if (context.isPlayer() || !ESConfig.PROCEDURAL_BODIES.get()) {
            return 0.0F;
        }
        if (context.isBody() && context.carry().mode() == CarryLink.Mode.CARRY) {
            return 1.0F;
        }
        return context.sim().fall(context.isBody(), context.partialTick());
    }

    @Override
    public void apply(PoseContext context, HumanoidPose pose, float weight) {
        ProceduralState sim = context.sim();
        int id = context.entityId();
        if (context.isBody() && context.carry().mode() == CarryLink.Mode.CARRY) {
            carried(context, pose, weight);
            return;
        }
        BodyState state = context.isBody() ? context.bodyState() : sim.bodyState;
        if (state == BodyState.DEAD) {
            BodyPoses.dead(pose, id, sim.faceUp(), weight);
        } else {
            BodyPoses.knockedOut(pose, id, sim.faceUp(), weight);
        }
        float drag = sim.dragBlend(context.partialTick()) * weight;
        if (drag > 0.0F) {
            dragged(context, pose, drag);
        }
    }

    /** Pulled along by the wrists: arms overhead, head lolling, legs following the chain's hip angle. */
    private static void dragged(PoseContext context, HumanoidPose pose, float w) {
        pose.setRot(Bone.RIGHT_ARM, ARMS_OVERHEAD, 0.0F, -0.12F, w);
        pose.setRot(Bone.LEFT_ARM, ARMS_OVERHEAD, 0.0F, 0.12F, w);
        pose.setBend(Bone.RIGHT_ARM, 0.0F, w);
        pose.setBend(Bone.LEFT_ARM, 0.0F, w);
        pose.setRot(Bone.HEAD, -0.3F, 0.0F, 0.0F, w);
        float hip = hipFlex(context.sim().chain, context.partialTick());
        pose.setRot(Bone.RIGHT_LEG, -hip, 0.0F, 0.06F, w);
        pose.setRot(Bone.LEFT_LEG, -hip, 0.0F, -0.06F, w);
        pose.setBend(Bone.RIGHT_LEG, 0.15F, w);
        pose.setBend(Bone.LEFT_LEG, 0.1F, w);
    }

    /** Angle between the torso and the legs along the drag chain, radians (0 = straight). */
    static float hipFlex(VerletChain chain, float partialTick) {
        if (chain == null || chain.size() < 4) {
            return 0.0F;
        }
        double[] shoulders = new double[3];
        double[] hips = new double[3];
        double[] feet = new double[3];
        chain.lerp(1, partialTick, shoulders);
        chain.lerp(2, partialTick, hips);
        chain.lerp(3, partialTick, feet);
        double ax = hips[0] - shoulders[0];
        double ay = hips[1] - shoulders[1];
        double az = hips[2] - shoulders[2];
        double bx = feet[0] - hips[0];
        double by = feet[1] - hips[1];
        double bz = feet[2] - hips[2];
        double la = Math.sqrt(ax * ax + ay * ay + az * az);
        double lb = Math.sqrt(bx * bx + by * by + bz * bz);
        if (la < 1.0E-4 || lb < 1.0E-4) {
            return 0.0F;
        }
        double cos = (ax * bx + ay * by + az * bz) / (la * lb);
        float angle = (float) Math.acos(Math.max(-1.0, Math.min(1.0, cos)));
        // The torso is lifted towards the hand while the legs lie on the floor: the legs point more upwards than
        // the torso's continuation, which is hip flexion (towards the face-up front). The other way is extension;
        // keep that to a little.
        boolean flexed = by / lb >= ay / la;
        return flexed ? Math.min(angle, 1.2F) : -Math.min(angle, 0.3F);
    }

    /** Over the shoulders: arms and legs hang straight down, head drops. */
    private static void carried(PoseContext context, HumanoidPose pose, float w) {
        // Face down, the body's front faces the ground, so "down" for a limb is -z: xRot = -pi/2.
        float sway = 0.06F * (float) Math.sin(context.sim().distance(context.partialTick()) * 3.0);
        pose.setRot(Bone.RIGHT_ARM, DANGLE + sway, 0.0F, 0.08F, w);
        pose.setRot(Bone.LEFT_ARM, DANGLE - sway, 0.0F, -0.08F, w);
        pose.setBend(Bone.RIGHT_ARM, -0.1F, w);
        pose.setBend(Bone.LEFT_ARM, -0.1F, w);
        pose.setRot(Bone.RIGHT_LEG, DANGLE + 0.15F - sway, 0.0F, 0.05F, w);
        pose.setRot(Bone.LEFT_LEG, DANGLE + 0.15F + sway, 0.0F, -0.05F, w);
        pose.setBend(Bone.RIGHT_LEG, 0.35F, w);
        pose.setBend(Bone.LEFT_LEG, 0.3F, w);
        pose.setRot(Bone.HEAD, 0.9F, 0.2F, 0.0F, w);
    }
}
