package com.mcspacewizard.emergentstealth.client.render;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.anim.Bone;
import com.mcspacewizard.emergentstealth.anim.HumanoidPose;
import com.mcspacewizard.emergentstealth.client.anim.Bends;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The NPC body model (design doc 17 §8): vanilla humanoid animation first, then a takedown victim clip (NeoForge
 * JSON entity animation) if one is playing, then the procedural {@link HumanoidPose} from the render state,
 * then elbow/knee bends. The armour models are {@code NpcModel}s too and the outfit layers re-render this one,
 * so everything worn follows the same pose.
 */
public class NpcModel extends HumanoidModel<NpcRenderState> {
    private final ModelPart[] parts = new ModelPart[Bone.COUNT];
    /** Overlay children that must bend with their limb (sleeves, trouser legs). */
    private final List<List<ModelPart>> bendParts = new ArrayList<>(Bone.COUNT);
    /** Victim clips baked against this model's parts, keyed by the loaded definition (rebakes on F3+T). */
    private final Map<AnimationDefinition, KeyframeAnimation> baked = new IdentityHashMap<>();

    public NpcModel(ModelPart root) {
        super(root);
        parts[Bone.HEAD.ordinal()] = head;
        parts[Bone.BODY.ordinal()] = body;
        parts[Bone.RIGHT_ARM.ordinal()] = rightArm;
        parts[Bone.LEFT_ARM.ordinal()] = leftArm;
        parts[Bone.RIGHT_LEG.ordinal()] = rightLeg;
        parts[Bone.LEFT_LEG.ordinal()] = leftLeg;
        for (Bone bone : Bone.VALUES) {
            List<ModelPart> list = new ArrayList<>(2);
            ModelPart part = parts[bone.ordinal()];
            if (part != null && bone.isLimb()) {
                list.add(part);
                String overlay = switch (bone) {
                    case RIGHT_ARM -> "right_sleeve";
                    case LEFT_ARM -> "left_sleeve";
                    case RIGHT_LEG -> "right_pants";
                    case LEFT_LEG -> "left_pants";
                    default -> null;
                };
                if (overlay != null && part.hasChild(overlay)) {
                    list.add(part.getChild(overlay));
                }
            }
            bendParts.add(list);
        }
    }

    @Override
    public void setupAnim(NpcRenderState state) {
        super.setupAnim(state);
        if (state.victimClip != null) {
            // The clip replaces the walk: start from the rest pose, then add the keyframes.
            for (Bone bone : Bone.VALUES) {
                ModelPart part = parts[bone.ordinal()];
                if (part != null) {
                    part.resetPose();
                }
            }
            KeyframeAnimation clip = bake(state.victimClip);
            if (clip != null) {
                clip.apply(state.victimClipMillis, 1.0F);
            }
        }
        applyPose(state.pose);
    }

    private @Nullable KeyframeAnimation bake(AnimationDefinition definition) {
        KeyframeAnimation clip = baked.get(definition);
        if (clip == null && !baked.containsKey(definition)) {
            if (baked.size() > 16) {
                baked.clear(); // stale definitions from earlier reloads
            }
            try {
                clip = definition.bake(root());
            } catch (IllegalArgumentException e) {
                EmergentStealth.LOGGER.warn("Animation: victim clip doesn't fit the NPC model: {}", e.getMessage());
            }
            baked.put(definition, clip);
        }
        return clip;
    }

    /** Applies the procedural pose over whatever the parts hold now, and sets every limb's bend. */
    public void applyPose(HumanoidPose pose) {
        for (Bone bone : Bone.VALUES) {
            ModelPart part = parts[bone.ordinal()];
            if (part == null) {
                continue;
            }
            if (pose.touched(bone)) {
                part.xRot = pose.apply(bone, HumanoidPose.ROT_X, part.xRot);
                part.yRot = pose.apply(bone, HumanoidPose.ROT_Y, part.yRot);
                part.zRot = pose.apply(bone, HumanoidPose.ROT_Z, part.zRot);
                float baseX = part.getInitialPose().x();
                float baseY = part.getInitialPose().y();
                float baseZ = part.getInitialPose().z();
                part.x = baseX + pose.apply(bone, HumanoidPose.POS_X, part.x - baseX);
                part.y = baseY + pose.apply(bone, HumanoidPose.POS_Y, part.y - baseY);
                part.z = baseZ + pose.apply(bone, HumanoidPose.POS_Z, part.z - baseZ);
            }
            if (bone.isLimb()) {
                float bend = pose.apply(bone, HumanoidPose.BEND, 0.0F);
                for (ModelPart bendPart : bendParts.get(bone.ordinal())) {
                    Bends.bend(bendPart, bend);
                }
            }
        }
    }
}
