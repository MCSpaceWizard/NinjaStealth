package com.mcspacewizard.emergentstealth.ui;

/**
 * Smooth, inertial scrolling along one axis (design doc 31 §0), in pixels and seconds:
 * <ul>
 *   <li>the wheel moves a target and the position glides to it;</li>
 *   <li>a drag moves the content 1:1 (with resistance past the ends);</li>
 *   <li>releasing a drag flings: the content coasts and slows with friction;</li>
 *   <li>past either end it springs back (rubber band).</li>
 * </ul>
 * With {@link #setInstant(boolean) instant} on (reduced motion) every change applies immediately.
 */
public final class InertialScroll {
    /** How fast the position glides to the target (per second). */
    private static final float SMOOTHING = 16.0F;
    /** Fling velocity decay (per second). */
    private static final float FRICTION = 3.5F;
    /** Extra decay while coasting past an end. */
    private static final float EDGE_FRICTION = 28.0F;
    /** Drag resistance past an end. */
    private static final float OVERDRAG = 0.35F;
    /** Below this speed (px/s) a fling stops. */
    private static final float MIN_FLING = 25.0F;
    /** A release this long after the last drag movement doesn't fling. */
    private static final long FLING_WINDOW_MS = 80L;

    private float position;
    private float target;
    private float velocity;
    private float max;
    private boolean dragging;
    private boolean coasting;
    private boolean instant;
    private long lastDragMs;

    public void setInstant(boolean instant) {
        this.instant = instant;
    }

    /** The scrollable distance (content size minus viewport size). */
    public void setMax(float max) {
        this.max = Math.max(0.0F, max);
        if (!dragging && !coasting) {
            target = clamp(target);
            if (instant) {
                position = target;
            }
        }
    }

    public float max() {
        return max;
    }

    public float position() {
        return position;
    }

    public float target() {
        return target;
    }

    public float velocity() {
        return velocity;
    }

    public boolean isDragging() {
        return dragging;
    }

    /** At rest: not dragging, not coasting and at the target. */
    public boolean isSettled() {
        return !dragging && !coasting && Math.abs(target - position) < 0.01F;
    }

    /** Wheel / key scrolling: moves the target. */
    public void scrollBy(float delta) {
        coasting = false;
        velocity = 0.0F;
        target = clamp(target + delta);
        if (instant) {
            position = target;
        }
    }

    public void scrollTo(float value, boolean immediately) {
        coasting = false;
        velocity = 0.0F;
        target = clamp(value);
        if (immediately || instant) {
            position = target;
        }
    }

    public void beginDrag(long nowMs) {
        dragging = true;
        coasting = false;
        velocity = 0.0F;
        lastDragMs = nowMs;
        target = position;
    }

    /** Moves the content by {@code delta} pixels of scroll (positive scrolls towards the end). */
    public void dragBy(float delta, long nowMs) {
        if (!dragging) {
            beginDrag(nowMs);
        }
        boolean outside = (position <= 0.0F && delta < 0.0F) || (position >= max && delta > 0.0F);
        position += outside ? delta * OVERDRAG : delta;
        target = position;
        float dt = Math.clamp((nowMs - lastDragMs) / 1000.0F, 1.0F / 240.0F, 0.1F);
        velocity = 0.6F * (delta / dt) + 0.4F * velocity;
        lastDragMs = nowMs;
    }

    public void endDrag(long nowMs) {
        if (!dragging) {
            return;
        }
        dragging = false;
        if (instant || nowMs - lastDragMs > FLING_WINDOW_MS) {
            velocity = 0.0F;
        }
        coasting = Math.abs(velocity) > MIN_FLING;
        if (!coasting) {
            target = clamp(position);
            if (instant) {
                position = target;
            }
        }
    }

    /** Advances the animation by {@code dt} seconds. */
    public void update(float dt) {
        if (dragging || dt <= 0.0F) {
            return;
        }
        dt = Math.min(dt, 0.1F);
        if (coasting) {
            position += velocity * dt;
            boolean outside = position < 0.0F || position > max;
            velocity *= (float) Math.exp(-(outside ? EDGE_FRICTION : FRICTION) * dt);
            target = position;
            if (Math.abs(velocity) < MIN_FLING) {
                coasting = false;
                velocity = 0.0F;
                target = clamp(position);
            }
            return;
        }
        target = clamp(target);
        if (instant) {
            position = target;
            return;
        }
        position += (target - position) * (1.0F - (float) Math.exp(-SMOOTHING * dt));
        if (Math.abs(target - position) < 0.05F) {
            position = target;
        }
    }

    private float clamp(float value) {
        return Math.clamp(value, 0.0F, max);
    }
}
