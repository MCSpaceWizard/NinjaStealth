package com.mcspacewizard.emergentstealth.client.anim.layer;

import com.mcspacewizard.emergentstealth.action.Stance;
import com.mcspacewizard.emergentstealth.anim.Bone;
import com.mcspacewizard.emergentstealth.anim.CrawlGait;
import com.mcspacewizard.emergentstealth.anim.HumanoidPose;
import com.mcspacewizard.emergentstealth.client.anim.PoseContext;
import com.mcspacewizard.emergentstealth.client.anim.PoseLayer;
import com.mcspacewizard.emergentstealth.config.ESConfig;

/**
 * The elbow crawl (design doc 17 §1), replacing vanilla's swimming arms while a player crawls on land. Vanilla's
 * crawl pose already lays the body face down; this moves the limbs. Hands are planted and pulled past by
 * distance travelled ({@link CrawlGait}), so they don't slide; the opposite knee pushes at the same time.
 */
public final class CrawlLayer implements PoseLayer {
    /** How far a pushing knee swings out to the side, radians. */
    private static final float KNEE_OUT = 0.55F;
    private static final float KNEE_DRAW = 0.35F;
    private static final float KNEE_BEND = 1.25F;
    /** Legs at rest lie slightly apart. */
    private static final float LEG_SPREAD = 0.08F;

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public float weight(PoseContext context) {
        if (!context.isPlayer() || context.stance() != Stance.CRAWLING || context.entity().isInWater()
                || !ESConfig.PROCEDURAL_CRAWL.get()) {
            return 0.0F;
        }
        return context.swimAmount();
    }

    @Override
    public void apply(PoseContext context, HumanoidPose pose, float weight) {
        float phase = CrawlGait.phase(context.sim().distance(context.partialTick()));
        arm(pose, Bone.RIGHT_ARM, phase, weight);
        arm(pose, Bone.LEFT_ARM, phase + 0.5F, weight);
        // Each knee pushes while the opposite hand is planted.
        leg(pose, Bone.RIGHT_LEG, CrawlGait.push(phase + 0.5F), 1.0F, weight);
        leg(pose, Bone.LEFT_LEG, CrawlGait.push(phase), -1.0F, weight);
    }

    private static void arm(HumanoidPose pose, Bone bone, float phase, float weight) {
        float[] hand = new float[2];
        CrawlGait.hand(phase, hand);
        float[] target = new float[2];
        CrawlGait.toModel(hand[0], hand[1], target);
        float side = bone == Bone.RIGHT_ARM ? 1.0F : -1.0F;
        // Elbows flex towards the front of the body, which faces the ground here; they splay out a little so the
        // forearms land under the shoulders, not under the chest.
        Reach.to(pose, bone, target[0], target[1], side * 0.18F, -1.0F, weight);
    }

    /** @param side +1 for the right leg (its outward roll is +z), -1 for the left */
    private static void leg(HumanoidPose pose, Bone bone, float push, float side, float weight) {
        pose.setRot(bone, -KNEE_DRAW * push, 0.0F, side * (LEG_SPREAD + KNEE_OUT * push), weight);
        pose.setBend(bone, KNEE_BEND * push, weight);
    }
}
