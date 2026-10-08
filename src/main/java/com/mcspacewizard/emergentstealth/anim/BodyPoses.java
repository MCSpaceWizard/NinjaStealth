package com.mcspacewizard.emergentstealth.anim;

/**
 * Lying poses for knocked-out and dead NPCs (design doc 17 §2), as limb angles relative to the torso. The
 * renderer lays the whole model down (root); these only splay the limbs.
 *
 * <p>Every body gets a random but stable variation from its entity id, so a pile of bodies doesn't look
 * cloned, and the pose never changes between frames, clients or relogs of the same entity.
 */
public final class BodyPoses {
    private BodyPoses() {}

    /** Stable pseudo-random bits for an entity id and a salt (SplitMix64). */
    public static long hash(int entityId, int salt) {
        long z = (entityId * 0x9E3779B97F4A7C15L) ^ (salt * 0xC2B2AE3D27D4EB4FL);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Stable value in [0, 1). */
    public static float unit(int entityId, int salt) {
        return (hash(entityId, salt) >>> 40) / (float) (1L << 24);
    }

    /** Stable value in [-1, 1). */
    public static float signedUnit(int entityId, int salt) {
        return unit(entityId, salt) * 2.0F - 1.0F;
    }

    /** Whether a body with no known fall direction lies on its back. */
    public static boolean faceUp(int entityId) {
        return unit(entityId, 1) < 0.6F;
    }

    /** Yaw added to the body's own facing once it lies down, degrees. */
    public static float yawOffset(int entityId) {
        return signedUnit(entityId, 2) * 35.0F;
    }

    /** Smooth breathing wave in [0, 1]: 0 = exhaled. */
    public static float breath(float ageTicks, float periodTicks) {
        return 0.5F - 0.5F * (float) Math.cos(2.0 * Math.PI * ageTicks / periodTicks);
    }

    /** Slack, ragdoll-like knocked-out pose: limbs relaxed, one knee or elbow bent. */
    public static void knockedOut(HumanoidPose pose, int id, boolean faceUp, float w) {
        float side = unit(id, 3) < 0.5F ? 1.0F : -1.0F; // which way the head rolls
        float r1 = unit(id, 4);
        float r2 = unit(id, 5);
        float r3 = unit(id, 6);
        if (faceUp) {
            pose.setRot(Bone.HEAD, -0.12F - 0.15F * r1, side * (0.45F + 0.35F * r2), side * 0.08F, w);
            pose.setRot(Bone.RIGHT_ARM, -0.12F + 0.1F * r3, 0.0F, 0.35F + 0.55F * r1, w);
            pose.setRot(Bone.LEFT_ARM, -0.08F - 0.1F * r2, 0.0F, -0.3F - 0.6F * r3, w);
            pose.setBend(Bone.RIGHT_ARM, -0.25F - 0.6F * r2, w);
            pose.setBend(Bone.LEFT_ARM, -0.2F - 0.5F * r1, w);
            // One knee up, the other leg out.
            boolean rightKnee = r3 < 0.5F;
            Bone up = rightKnee ? Bone.RIGHT_LEG : Bone.LEFT_LEG;
            Bone flat = up.mirror();
            float out = rightKnee ? 1.0F : -1.0F;
            pose.setRot(up, -0.45F - 0.2F * r1, 0.0F, out * 0.12F, w);
            pose.setBend(up, 0.85F + 0.3F * r2, w);
            pose.setRot(flat, -0.04F, 0.0F, -out * (0.12F + 0.12F * r2), w);
            pose.setBend(flat, 0.08F, w);
        } else {
            // Face down: head turned to rest on a cheek, one arm up by the head.
            pose.setRot(Bone.HEAD, 0.05F, side * (1.05F + 0.25F * r1), 0.0F, w);
            Bone upArm = side > 0 ? Bone.LEFT_ARM : Bone.RIGHT_ARM;
            Bone downArm = upArm.mirror();
            float upOut = upArm == Bone.RIGHT_ARM ? 1.0F : -1.0F;
            pose.setRot(upArm, -2.55F - 0.25F * r2, 0.0F, upOut * (0.35F + 0.2F * r3), w);
            pose.setBend(upArm, -0.15F, w);
            pose.setRot(downArm, 0.12F, 0.0F, -upOut * (0.12F + 0.15F * r1), w);
            pose.setBend(downArm, -0.1F, w);
            pose.setRot(Bone.RIGHT_LEG, 0.02F, 0.0F, 0.1F + 0.1F * r3, w);
            pose.setRot(Bone.LEFT_LEG, 0.02F, 0.0F, -0.1F - 0.1F * r2, w);
            pose.setBend(Bone.RIGHT_LEG, 0.1F + 0.2F * r1, w);
            pose.setBend(Bone.LEFT_LEG, 0.05F, w);
        }
    }

    /** Limp corpse: more sprawled and asymmetric, nothing held. */
    public static void dead(HumanoidPose pose, int id, boolean faceUp, float w) {
        float side = unit(id, 7) < 0.5F ? 1.0F : -1.0F;
        float r1 = unit(id, 8);
        float r2 = unit(id, 9);
        if (faceUp) {
            pose.setRot(Bone.HEAD, -0.35F - 0.15F * r1, side * (0.8F + 0.4F * r2), side * 0.15F, w);
            // One arm flung over the head, the other thrown out.
            Bone flung = side > 0 ? Bone.RIGHT_ARM : Bone.LEFT_ARM;
            Bone out = flung.mirror();
            float f = flung == Bone.RIGHT_ARM ? 1.0F : -1.0F;
            pose.setRot(flung, -2.75F - 0.3F * r1, 0.0F, f * (0.45F + 0.3F * r2), w);
            pose.setBend(flung, -0.1F, w);
            pose.setRot(out, -0.05F, 0.0F, -f * (1.0F + 0.35F * r1), w);
            pose.setBend(out, -0.05F, w);
            pose.setRot(Bone.RIGHT_LEG, 0.0F, 0.0F, 0.25F + 0.15F * r2, w);
            pose.setRot(Bone.LEFT_LEG, 0.0F, 0.0F, -0.22F - 0.15F * r1, w);
            pose.setBend(Bone.RIGHT_LEG, 0.04F, w);
            pose.setBend(Bone.LEFT_LEG, 0.04F, w);
        } else {
            pose.setRot(Bone.HEAD, 0.1F, side * (1.25F + 0.2F * r1), 0.0F, w);
            pose.setRot(Bone.RIGHT_ARM, 0.18F, 0.0F, 0.18F + 0.25F * r1, w);
            pose.setRot(Bone.LEFT_ARM, 0.18F, 0.0F, -0.18F - 0.25F * r2, w);
            pose.setBend(Bone.RIGHT_ARM, 0.0F, w);
            pose.setBend(Bone.LEFT_ARM, 0.0F, w);
            pose.setRot(Bone.RIGHT_LEG, 0.0F, 0.0F, 0.2F + 0.15F * r2, w);
            pose.setRot(Bone.LEFT_LEG, 0.0F, 0.0F, -0.18F - 0.15F * r1, w);
            pose.setBend(Bone.RIGHT_LEG, 0.0F, w);
            pose.setBend(Bone.LEFT_LEG, 0.0F, w);
        }
        pose.setRot(Bone.BODY, 0.0F, 0.0F, 0.0F, w);
    }
}
