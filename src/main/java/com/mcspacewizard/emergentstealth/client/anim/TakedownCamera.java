package com.mcspacewizard.emergentstealth.client.anim;

import com.mcspacewizard.emergentstealth.action.ActionPlayback;
import com.mcspacewizard.emergentstealth.anim.ActionClock;

/**
 * First-person camera during an action (design doc 17 §3): it only turns, never moves (angle-only, no mixin).
 * Rear takedowns look down over the victim's shoulder, with a struggle shake through a choke; air takedowns dip
 * hard on landing and recover. Offsets fade in and out, so the view hands back exactly where it was.
 */
public final class TakedownCamera {
    private TakedownCamera() {}

    /** Pitch (degrees, positive looks down) and yaw offsets at this moment, written to {@code out}. */
    public static void offsets(ActionPlayback action, long gameTime, float partialTick, float[] out) {
        out[0] = 0.0F;
        out[1] = 0.0F;
        if (action.role() != ActionPlayback.Role.ATTACKER || !ActionClock.playing(action, gameTime, partialTick)) {
            return;
        }
        float p = ActionClock.progress(action, gameTime, partialTick);
        String path = action.action().getPath();
        if (path.startsWith("takedown/air")) {
            // Slam down at once, recover over the rest.
            float dip = p < 0.15F ? smooth(p / 0.15F) : 1.0F - smooth((p - 0.15F) / 0.85F);
            out[0] = 22.0F * dip;
            return;
        }
        float envelope = smooth(Math.min(1.0F, p / 0.2F)) * (1.0F - smooth(Math.max(0.0F, (p - 0.8F) / 0.2F)));
        if (path.endsWith("nonlethal")) {
            float seconds = ActionClock.seconds(action, gameTime, partialTick);
            out[0] = 16.0F * envelope + 1.2F * (float) Math.sin(seconds * 11.0) * envelope;
            out[1] = 2.0F * (float) Math.sin(seconds * 7.0) * envelope;
        } else {
            out[0] = 12.0F * envelope;
            out[1] = -4.0F * envelope;
        }
    }

    private static float smooth(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }
}
