package com.mcspacewizard.emergentstealth.client.anim;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.action.ActionPlayback;
import com.mcspacewizard.emergentstealth.action.BodyState;
import com.mcspacewizard.emergentstealth.anim.Spring;
import com.mcspacewizard.emergentstealth.anim.VerletChain;

/**
 * Per-entity animation memory (design doc 17 §8): everything that needs history. {@link ProceduralSim}s step
 * it at 20 Hz in client tick; layers only read it (interpolating with the partial tick), so the pose stays a
 * pure function of this state plus the render state, however many times a frame {@code setupAnim} runs.
 */
public final class ProceduralState {
    public final int entityId;
    /** Client tick this state was last stepped (stale states are dropped). */
    long lastStepped;
    /** True until the first step finishes. */
    boolean fresh = true;

    // --- Motion (MotionSim) ---
    /** Horizontal distance travelled, blocks, at the last two ticks. Drives gait phases. */
    public double distance;
    public double distancePrev;
    double lastX;
    double lastY;
    double lastZ;
    float lastYaw;
    boolean hasLast;
    /** Lean into turns (roll) and speed changes (pitch), radians. */
    public final Spring leanRoll = new Spring(0.3F);
    public final Spring leanPitch = new Spring(0.3F);
    /** 0 = rested, 1 = out of breath after sprinting. */
    public final Spring exertion = new Spring(0.04F);
    /** Ticks since the entity last moved, for idle blending. */
    public int stillTicks;

    // --- Bodies (BodySim) ---
    public BodyState bodyState = BodyState.NONE;
    /** Fall progress from standing (0) to lying (1), last two ticks. */
    public float fall;
    public float fallPrev;
    /** Which side the body lies on, decided when it falls. */
    public boolean faceUp = true;
    /** The takedown that put this body down, if seen (sets the fall direction). */
    public @Nullable ActionPlayback lastVictimAction;
    /** Slope under the body: pitch along its length and roll across, degrees. */
    public final Spring groundPitch = new Spring(0.35F);
    public final Spring groundRoll = new Spring(0.35F);

    // --- Dragged bodies (DragChainSim) ---
    /** Dragger's hand, shoulders, hips, feet. Null when not dragged. */
    public @Nullable VerletChain chain;
    /** How settled the drag pose is (0 = just grabbed), last two ticks. */
    public float dragBlend;
    public float dragBlendPrev;

    ProceduralState(int entityId) {
        this.entityId = entityId;
    }

    public boolean hasLastPosition() {
        return hasLast;
    }

    /** Remembers this tick's position and yaw for the next step. */
    public void resetMotion(double x, double y, double z, float yaw) {
        lastX = x;
        lastY = y;
        lastZ = z;
        lastYaw = yaw;
        hasLast = true;
    }

    public double lastX() {
        return lastX;
    }

    public double lastZ() {
        return lastZ;
    }

    public float lastYaw() {
        return lastYaw;
    }

    /** Whether this state was created this tick (first seen): bodies then start already lying down. */
    public boolean isFresh() {
        return fresh;
    }

    public float distance(float partialTick) {
        return (float) (distancePrev + (distance - distancePrev) * partialTick);
    }

    public float fall(float partialTick) {
        return fallPrev + (fall - fallPrev) * partialTick;
    }

    public float dragBlend(float partialTick) {
        return dragBlendPrev + (dragBlend - dragBlendPrev) * partialTick;
    }
}
