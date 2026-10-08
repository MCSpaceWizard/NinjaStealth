package com.mcspacewizard.emergentstealth.anim;

import com.mcspacewizard.emergentstealth.action.ActionPlayback;

import net.minecraft.resources.Identifier;

/**
 * Time inside an {@link ActionPlayback} (design doc 17 §3). Clients never count their own time: every frame
 * evaluates {@code gameTime - startTick + partialTick}, so a client that sees the action late (lag, late join,
 * tracking range) seeks straight to the right frame, and both participants stay aligned.
 */
public final class ActionClock {
    private ActionClock() {}

    /** Ticks since the start, clamped to {@code [0, length]}. */
    public static float ticks(ActionPlayback action, long gameTime, float partialTick) {
        if (action.isNone()) {
            return 0.0F;
        }
        float t = (float) (gameTime - action.startTick()) + partialTick;
        return Math.max(0.0F, Math.min(action.length(), t));
    }

    public static float seconds(ActionPlayback action, long gameTime, float partialTick) {
        return ticks(action, gameTime, partialTick) / 20.0F;
    }

    /** 0 at the start, 1 at the end. */
    public static float progress(ActionPlayback action, long gameTime, float partialTick) {
        return action.length() <= 0 ? 1.0F : ticks(action, gameTime, partialTick) / action.length();
    }

    /** Whether the action is playing at this moment (between ticks, too). */
    public static boolean playing(ActionPlayback action, long gameTime, float partialTick) {
        if (action.isNone()) {
            return false;
        }
        float t = (float) (gameTime - action.startTick()) + partialTick;
        return t >= 0.0F && t < action.length();
    }

    /**
     * Where a clip player should be, in ticks, when it learns about the action at {@code gameTime}: 0 if on
     * time, otherwise how far to seek in.
     */
    public static float seekTicks(ActionPlayback action, long gameTime) {
        return ticks(action, gameTime, 0.0F);
    }

    /**
     * The clip for one side of an action: {@code ns:takedown/rear_nonlethal} played as the {@code attacker} is
     * {@code ns:takedown/rear_nonlethal.attacker} (a PAL player clip; NPC victims use NeoForge entity animations).
     */
    public static Identifier clipId(ActionPlayback action, String role) {
        return action.action().withSuffix("." + role);
    }

    /** Clip time in milliseconds for keyframe players, clamped to the clip so it holds its last frame. */
    public static long clipMillis(ActionPlayback action, long gameTime, float partialTick, float clipSeconds) {
        float s = Math.min(seconds(action, gameTime, partialTick), clipSeconds);
        return (long) (s * 1000.0F);
    }
}
