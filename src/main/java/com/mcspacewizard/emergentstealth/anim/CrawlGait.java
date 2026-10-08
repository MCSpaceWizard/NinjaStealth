package com.mcspacewizard.emergentstealth.anim;

/**
 * The procedural crawl cycle (design doc 17 §1): an elbow crawl driven by distance, not time, so hands and
 * knees don't slide.
 *
 * <p>The phase advances by {@link #STRIDE} blocks per cycle. Each arm has a stance half (hand planted, body
 * pulled past it) and a swing half (hand lifted and reached forward again). While a hand is planted the body
 * moves {@code STRIDE / 2}, so the hand slides back relative to the shoulder by exactly that much: in the world
 * it stays put. The opposite knee pushes at the same time.
 *
 * <p>Geometry is in the prone frame vanilla's crawl pose gives the model (face down, head forward): world
 * forward is model -y, world down is model -z.
 */
public final class CrawlGait {
    private CrawlGait() {}

    /** Body travel per full cycle, in blocks. */
    public static final float STRIDE = 0.9F;
    /** Hand travel relative to the shoulder during stance, in pixels (half a stride). */
    public static final float HAND_TRAVEL = STRIDE * 16.0F / 2.0F;
    /** Where the planted hand is at the start of stance, pixels ahead of the shoulder. */
    public static final float REACH = 8.5F;
    /** Shoulder pivot height above the hand's contact point when prone, pixels. */
    public static final float SHOULDER_DROP = 3.0F;
    /** How high a hand lifts during the swing, pixels. */
    public static final float LIFT = 2.5F;

    /** Phase in [0, 1) for a crawled distance in blocks. */
    public static float phase(double distance) {
        double cycles = distance / STRIDE;
        return (float) (cycles - Math.floor(cycles));
    }

    /**
     * Hand target relative to the shoulder in prone-frame pixels: {@code out[0]} forward, {@code out[1]} up
     * from the ground contact (0 = planted).
     *
     * @param phase this arm's phase; the left arm uses {@code phase + 0.5}
     */
    public static void hand(float phase, float[] out) {
        float p = phase - (float) Math.floor(phase);
        if (p < 0.5F) {
            float u = p / 0.5F; // stance: planted, the body moves past the hand
            out[0] = REACH - u * HAND_TRAVEL;
            out[1] = 0.0F;
        } else {
            float u = (p - 0.5F) / 0.5F; // swing: lift and reach forward again (smoothstep)
            float s = u * u * (3.0F - 2.0F * u);
            out[0] = REACH - HAND_TRAVEL + s * HAND_TRAVEL;
            out[1] = LIFT * (float) Math.sin(Math.PI * u);
        }
    }

    /** Knee push amount in [0, 1] for a leg: pushes while the opposite arm is planted. */
    public static float push(float phase) {
        float p = phase - (float) Math.floor(phase);
        // Knee drawn up at the start of the opposite arm's stance, extending through it, recovering in swing.
        return p < 0.5F ? 1.0F - p / 0.5F : (float) Math.sin(Math.PI * (p - 0.5F) / 0.5F * 0.5F);
    }

    /** Converts a prone-frame hand target into the arm's model-space (y, z): forward = -y, down = -z. */
    public static void toModel(float forward, float up, float[] out) {
        out[0] = -forward;
        out[1] = -(SHOULDER_DROP - up);
    }
}
