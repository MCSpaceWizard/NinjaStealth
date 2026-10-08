package com.mcspacewizard.emergentstealth.client.anim.layer;

import com.mcspacewizard.emergentstealth.action.BodyState;
import com.mcspacewizard.emergentstealth.action.Stance;
import com.mcspacewizard.emergentstealth.anim.Bone;
import com.mcspacewizard.emergentstealth.anim.HumanoidPose;
import com.mcspacewizard.emergentstealth.client.anim.PoseContext;
import com.mcspacewizard.emergentstealth.client.anim.PoseLayer;
import com.mcspacewizard.emergentstealth.config.ESConfig;

/**
 * Breathing (design doc 17 §2 and §8), added on top of everything else. Barely visible at rest, heavier after
 * sprinting, and slow and deep on a knocked-out NPC so you can tell it from a corpse. Corpses don't breathe.
 */
public final class BreathingLayer implements PoseLayer {
    /** Shoulders rise and the arms ease out on the in-breath, radians. */
    private static final float ARM_OUT = 0.03F;
    private static final float HEAD_LIFT = 0.02F;
    /** How far an unconscious chest rises, model pixels. */
    private static final float CHEST_RISE = 0.45F;

    @Override
    public int priority() {
        return 400;
    }

    @Override
    public float weight(PoseContext context) {
        if (!ESConfig.BREATHING.get() || context.bodyState() == BodyState.DEAD) {
            return 0.0F;
        }
        if (context.isPlayer() && (!ESConfig.PLAYER_IDLE_ANIMATION.get() || context.stance() == Stance.CRAWLING)) {
            return 0.0F;
        }
        return context.actionPlaying() ? 0.0F : 1.0F;
    }

    @Override
    public void apply(PoseContext context, HumanoidPose pose, float weight) {
        float b = context.sim().breath(context.partialTick());
        if (context.bodyState() == BodyState.UNCONSCIOUS) {
            // The chest rises towards the body's front; the head and arms follow a little.
            pose.addPos(Bone.BODY, 0.0F, 0.0F, -CHEST_RISE * b, weight);
            pose.addRot(Bone.HEAD, -0.04F * b, 0.0F, 0.0F, weight);
            pose.addRot(Bone.RIGHT_ARM, 0.0F, 0.0F, 0.04F * b, weight);
            pose.addRot(Bone.LEFT_ARM, 0.0F, 0.0F, -0.04F * b, weight);
            return;
        }
        float depth = 1.0F + 2.0F * Math.max(0.0F, Math.min(1.0F, context.sim().exertion.get(context.partialTick())));
        pose.addRot(Bone.RIGHT_ARM, 0.0F, 0.0F, ARM_OUT * depth * b, weight);
        pose.addRot(Bone.LEFT_ARM, 0.0F, 0.0F, -ARM_OUT * depth * b, weight);
        pose.addRot(Bone.HEAD, -HEAD_LIFT * depth * b, 0.0F, 0.0F, weight);
        // Arms and head ride up with the shoulders.
        float lift = -0.25F * depth * b;
        pose.addPos(Bone.RIGHT_ARM, 0.0F, lift, 0.0F, weight);
        pose.addPos(Bone.LEFT_ARM, 0.0F, lift, 0.0F, weight);
        pose.addPos(Bone.HEAD, 0.0F, lift * 0.5F, 0.0F, weight);
    }
}
