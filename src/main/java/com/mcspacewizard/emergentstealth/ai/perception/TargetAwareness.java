package com.mcspacewizard.emergentstealth.ai.perception;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.phys.Vec3;

/** What one NPC knows about one target. Only perception writes here: there is no free information (D-08). */
public final class TargetAwareness {
    /** 0 = unaware, 1 = detected. */
    float awareness;
    /** Where the target was last actually perceived; null if never. */
    @Nullable Vec3 lastKnownPos;
    /** Game time of the last perception; -1 if never. */
    long lastPerceivedTick = -1;
    /** Game time the target was last heard (S6); -1 if never. Hearing never counts as perceiving. */
    long lastHeardTick = -1;
    /** Whether the target is in view right now (as of the last perception update). */
    boolean seenNow;
    /** Last computed visibility (0-1+) for debugging. */
    float lastVisibility;

    public float awareness() {
        return awareness;
    }

    public @Nullable Vec3 lastKnownPos() {
        return lastKnownPos;
    }

    public long lastPerceivedTick() {
        return lastPerceivedTick;
    }

    public boolean seenNow() {
        return seenNow;
    }

    public float lastVisibility() {
        return lastVisibility;
    }

    /** Ticks since last perceived; Long.MAX_VALUE if never. */
    public long ticksSincePerceived(long now) {
        return lastPerceivedTick < 0 ? Long.MAX_VALUE : now - lastPerceivedTick;
    }

    public long lastHeardTick() {
        return lastHeardTick;
    }

    /** Last time anything (sight, a hit or a sound) told the NPC about this target; -1 if never. */
    public long lastClueTick() {
        return Math.max(lastPerceivedTick, lastHeardTick);
    }

    /**
     * The target was heard (design doc 16 §4): awareness rises by {@code gain}, but hearing alone never goes
     * past {@code cap}, and the last known position becomes where the noise was, not where the target is.
     */
    public void hear(Vec3 noisePosition, float gain, float cap, long now) {
        if (this.awareness < cap) {
            this.awareness = Math.min(cap, this.awareness + gain);
        }
        this.lastKnownPos = noisePosition;
        this.lastHeardTick = now;
    }

    /** External stimulus (damage, noise, reports): raises awareness and sets where to look. */
    public void stimulate(Vec3 position, float minAwareness, long now) {
        this.awareness = Math.max(this.awareness, Math.min(1.0F, minAwareness));
        this.lastKnownPos = position;
        this.lastPerceivedTick = now;
    }
}
