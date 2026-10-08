package com.mcspacewizard.emergentstealth.client.anim.pal;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;

import net.minecraft.resources.Identifier;

/**
 * Registers our layers on every player through PAL (design doc 17 §8). Priorities sit above emote mods, so a
 * crawl or a takedown is never overridden by a dance; the takedown clip sits above the procedural pose.
 */
public final class PalAnimations {
    private PalAnimations() {}

    public static final Identifier POSE_LAYER = EmergentStealth.id("procedural_pose");
    public static final Identifier CLIP_LAYER = EmergentStealth.id("action_clip");
    private static final int POSE_PRIORITY = 1500;
    private static final int CLIP_PRIORITY = 1600;

    /** Called once from client setup, before any player exists. */
    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(POSE_LAYER, POSE_PRIORITY, PalPoseAnimation::new);
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(CLIP_LAYER, CLIP_PRIORITY, TakedownClipController::new);
    }
}
