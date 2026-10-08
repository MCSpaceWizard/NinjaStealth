package com.mcspacewizard.emergentstealth.client.anim;

import com.mcspacewizard.emergentstealth.anim.HumanoidPose;

/**
 * One procedural contribution to a pose (design doc 17 §8): crawl cycle, body poses, breathing, lean, ...
 * Layers run in priority order (low first); later layers blend over earlier ones. A layer decides itself
 * whether it overrides channels ({@code HumanoidPose.set*}) or adds to them ({@code add*}).
 *
 * <p>Must be a pure function of the context: no stepping, no caching across frames (that's what
 * {@link ProceduralSim} is for), because {@code setupAnim} runs several times per frame.
 */
public interface PoseLayer {
    int priority();

    /** 0 = off for this entity this frame. */
    float weight(PoseContext context);

    void apply(PoseContext context, HumanoidPose pose, float weight);
}
