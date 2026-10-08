package com.mcspacewizard.emergentstealth.anim;

/**
 * Analytic limb IK in the limb's sagittal plane (design doc 17 §8). Blocky limbs are one or two segments, so
 * "IK" is aiming a bone, plus an elbow/knee angle when bends are available.
 *
 * <p>Model-part space: a limb with {@code xRot = a} points along {@code (y, z) = (cos a, sin a)} (y down,
 * -z is the front). The bend rotates the lower half the same way, so the lower segment points along
 * {@code a + bend}.
 */
public final class LimbIk {
    private LimbIk() {}

    /** Pivot to elbow and elbow to hand, in pixels (vanilla arm: 12 long, pivot 2 below the top, bend mid-cube). */
    public static final float ARM_UPPER = 4.0F;
    public static final float ARM_LOWER = 6.0F;
    /** Hip to knee and knee to foot. */
    public static final float LEG_UPPER = 6.0F;
    public static final float LEG_LOWER = 6.0F;

    /** {@code xRot} that points a straight limb at {@code (y, z)}. */
    public static float aim(float y, float z) {
        return (float) Math.atan2(z, y);
    }

    /**
     * Two-bone solve. Writes {@code out[0] = xRot} and {@code out[1] = bend}. Unreachable targets are clamped
     * to the limb's reach (fully stretched, or fully folded).
     *
     * @param bendSign +1 bends the lower segment towards +z (knees), -1 towards -z (elbows)
     */
    public static void solve(float y, float z, float upper, float lower, float bendSign, float[] out) {
        float d = (float) Math.sqrt(y * y + z * z);
        float min = Math.abs(upper - lower) + 1.0E-3F;
        float max = upper + lower - 1.0E-3F;
        d = Math.max(min, Math.min(max, d));
        float base = d < 1.0E-5F ? 0.0F : (float) Math.atan2(z, y);
        float cosShoulder = (upper * upper + d * d - lower * lower) / (2.0F * upper * d);
        float cosElbow = (upper * upper + lower * lower - d * d) / (2.0F * upper * lower);
        float shoulder = (float) Math.acos(clamp(cosShoulder));
        float flex = (float) (Math.PI - Math.acos(clamp(cosElbow)));
        float sign = bendSign >= 0.0F ? 1.0F : -1.0F;
        out[0] = base - sign * shoulder;
        out[1] = sign * flex;
    }

    /** Where the end of a two-segment limb ends up (forward kinematics), for tests and debugging. */
    public static void endPoint(float xRot, float bend, float upper, float lower, float[] out) {
        out[0] = upper * (float) Math.cos(xRot) + lower * (float) Math.cos(xRot + bend);
        out[1] = upper * (float) Math.sin(xRot) + lower * (float) Math.sin(xRot + bend);
    }

    private static float clamp(float v) {
        return Math.max(-1.0F, Math.min(1.0F, v));
    }
}
