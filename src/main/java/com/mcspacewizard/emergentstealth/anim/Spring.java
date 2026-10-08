package com.mcspacewizard.emergentstealth.anim;

/**
 * A critically damped spring stepped at a fixed rate (20 Hz client tick). Keeps the previous value so renders
 * interpolate with the partial tick instead of stepping in {@code setupAnim}.
 */
public final class Spring {
    private final float stiffness;
    private float value;
    private float previous;
    private float velocity;

    /** @param stiffness angular frequency per tick; higher follows the target faster */
    public Spring(float stiffness) {
        this.stiffness = stiffness;
    }

    public void step(float target) {
        previous = value;
        // Semi-implicit Euler on x'' = k²(t - x) - 2k x' (critically damped).
        float k = stiffness;
        velocity += (k * k * (target - value) - 2.0F * k * velocity);
        value += velocity;
    }

    public void snap(float target) {
        value = target;
        previous = target;
        velocity = 0.0F;
    }

    public float value() {
        return value;
    }

    public float get(float partialTick) {
        return previous + (value - previous) * partialTick;
    }
}
