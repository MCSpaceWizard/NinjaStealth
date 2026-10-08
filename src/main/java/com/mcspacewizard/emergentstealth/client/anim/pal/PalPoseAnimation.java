package com.mcspacewizard.emergentstealth.client.anim.pal;

import org.jetbrains.annotations.NotNull;

import com.mcspacewizard.emergentstealth.anim.Bone;
import com.mcspacewizard.emergentstealth.anim.HumanoidPose;
import com.mcspacewizard.emergentstealth.client.anim.PoseContext;
import com.mcspacewizard.emergentstealth.client.anim.PoseGraph;
import com.mcspacewizard.emergentstealth.client.anim.ProceduralStates;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;

import net.minecraft.world.entity.Avatar;

/**
 * The procedural pose graph on players (design doc 17 §8): the one adapter between our {@link HumanoidPose} and
 * PAL. PAL seeds each bone with vanilla's pose for this frame; every channel of our pose is relative to that base,
 * so this is one multiply-add per channel. The whole-body {@code body} bone carries {@link Bone#ROOT} (lean).
 */
public final class PalPoseAnimation implements IAnimation {
    private final Avatar avatar;
    private final HumanoidPose pose = new HumanoidPose();
    /** Decided once a tick (PAL asks before it sets anything up); pose evaluated once a frame. */
    private boolean active;

    public PalPoseAnimation(Avatar avatar) {
        this.avatar = avatar;
    }

    @Override
    public void tick(AnimationData state) {
        active = avatar.level() != null && PoseGraph.HUMANOID.wants(PoseContext.of(avatar, 1.0F, ProceduralStates.get(avatar)));
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setupAnim(AnimationData state) {
        PoseGraph.HUMANOID.evaluate(PoseContext.of(avatar, state.getPartialTick(), ProceduralStates.get(avatar)), pose);
    }

    @Override
    public void get3DTransform(@NotNull PlayerAnimBone bone) {
        Bone ours = switch (bone.getName()) {
            case "head" -> Bone.HEAD;
            case "torso" -> Bone.BODY;
            case "right_arm" -> Bone.RIGHT_ARM;
            case "left_arm" -> Bone.LEFT_ARM;
            case "right_leg" -> Bone.RIGHT_LEG;
            case "left_leg" -> Bone.LEFT_LEG;
            case "body" -> Bone.ROOT;
            default -> null;
        };
        if (ours == null || !pose.touched(ours)) {
            return;
        }
        bone.rotation.x = pose.apply(ours, HumanoidPose.ROT_X, bone.rotation.x);
        bone.rotation.y = pose.apply(ours, HumanoidPose.ROT_Y, bone.rotation.y);
        bone.rotation.z = pose.apply(ours, HumanoidPose.ROT_Z, bone.rotation.z);
        // PAL positions are y-up; ours are model pixels, y down.
        bone.position.x = pose.apply(ours, HumanoidPose.POS_X, bone.position.x);
        bone.position.y = -pose.apply(ours, HumanoidPose.POS_Y, -bone.position.y);
        bone.position.z = pose.apply(ours, HumanoidPose.POS_Z, bone.position.z);
        if (ours.isLimb()) {
            bone.bend = pose.apply(ours, HumanoidPose.BEND, bone.bend);
        }
    }
}
