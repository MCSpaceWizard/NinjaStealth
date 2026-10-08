package com.mcspacewizard.emergentstealth.client.anim.layer;

import com.mcspacewizard.emergentstealth.action.CarryLink;
import com.mcspacewizard.emergentstealth.anim.Bone;
import com.mcspacewizard.emergentstealth.anim.HumanoidPose;
import com.mcspacewizard.emergentstealth.client.anim.PoseContext;
import com.mcspacewizard.emergentstealth.client.anim.PoseLayer;
import com.mcspacewizard.emergentstealth.config.ESConfig;

/**
 * Whoever moves a body (design doc 17 §4). Dragging: the right hand reaches back and down to the body's wrists
 * (where the drag chain is pinned) and the body leans into the pull. Carrying: both hands up, steadying the
 * body across the shoulders. The left arm keeps walking while dragging.
 */
public final class CarrierLayer implements PoseLayer {
    /** Drag hand relative to the right shoulder: below and behind, model pixels (matches DragChainSim). */
    private static final float DRAG_HAND_DOWN = 12.0F;
    private static final float DRAG_HAND_BACK = 8.0F;
    private static final float DRAG_LEAN = 0.14F;
    private static final float CARRY_ARM_PITCH = -2.7F;
    private static final float CARRY_ARM_OUT = 0.5F;
    private static final float CARRY_ELBOW = -1.15F;

    @Override
    public int priority() {
        return 200;
    }

    @Override
    public float weight(PoseContext context) {
        return context.isCarrier() && ESConfig.PROCEDURAL_BODIES.get() ? 1.0F : 0.0F;
    }

    @Override
    public void apply(PoseContext context, HumanoidPose pose, float weight) {
        if (context.carry().mode() == CarryLink.Mode.DRAG) {
            Reach.to(pose, Bone.RIGHT_ARM, DRAG_HAND_DOWN, DRAG_HAND_BACK, 0.12F, -1.0F, weight);
            pose.addRot(Bone.ROOT, DRAG_LEAN, 0.0F, 0.0F, weight);
            return;
        }
        pose.setRot(Bone.RIGHT_ARM, CARRY_ARM_PITCH, 0.0F, CARRY_ARM_OUT, weight);
        pose.setRot(Bone.LEFT_ARM, CARRY_ARM_PITCH, 0.0F, -CARRY_ARM_OUT, weight);
        pose.setBend(Bone.RIGHT_ARM, CARRY_ELBOW, weight);
        pose.setBend(Bone.LEFT_ARM, CARRY_ELBOW, weight);
    }
}
