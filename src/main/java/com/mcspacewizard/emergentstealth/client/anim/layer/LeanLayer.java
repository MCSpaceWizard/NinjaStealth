package com.mcspacewizard.emergentstealth.client.anim.layer;

import com.mcspacewizard.emergentstealth.action.Stance;
import com.mcspacewizard.emergentstealth.anim.Bone;
import com.mcspacewizard.emergentstealth.anim.HumanoidPose;
import com.mcspacewizard.emergentstealth.client.anim.PoseContext;
import com.mcspacewizard.emergentstealth.client.anim.PoseLayer;
import com.mcspacewizard.emergentstealth.config.ESConfig;

/**
 * Leaning into turns and speed (design doc 17 §8): the whole body tilts about the hips, from the springs in
 * {@code MotionSim}. Off for bodies, crawlers and anyone in an action.
 */
public final class LeanLayer implements PoseLayer {
    @Override
    public int priority() {
        return 500;
    }

    @Override
    public float weight(PoseContext context) {
        if (!ESConfig.LEAN.get() || context.isBody() || context.actionPlaying() || context.entity().isPassenger()) {
            return 0.0F;
        }
        if (context.isPlayer() && (!ESConfig.PLAYER_IDLE_ANIMATION.get() || context.stance() == Stance.CRAWLING
                || context.swimAmount() > 0.0F || context.entity().isFallFlying())) {
            return 0.0F;
        }
        return 1.0F;
    }

    @Override
    public void apply(PoseContext context, HumanoidPose pose, float weight) {
        float pitch = context.sim().leanPitch.get(context.partialTick());
        float roll = context.sim().leanRoll.get(context.partialTick());
        pose.addRot(Bone.ROOT, pitch, 0.0F, roll, weight);
    }
}
