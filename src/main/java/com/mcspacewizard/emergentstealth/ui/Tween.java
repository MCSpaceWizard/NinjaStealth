package com.mcspacewizard.emergentstealth.ui;

/**
 * An eased float animation driven by real time in milliseconds (design doc 31 §0). Retargeting mid-flight
 * starts from the current value, so animations never jump. A duration of 0 (reduced motion) snaps.
 */
public final class Tween {
    private float from;
    private float to;
    private long start;
    private int duration;
    private Easing easing = Easing.CUBIC_OUT;

    public Tween(float value) {
        this.from = value;
        this.to = value;
    }

    /** Animates towards {@code target} unless it's already the target. */
    public Tween animate(float target, long nowMs, int durationMs, Easing easing) {
        if (target == to) {
            return this;
        }
        restart(value(nowMs), target, nowMs, durationMs, easing);
        return this;
    }

    /** Animates from {@code start} to {@code target}, even if the target is unchanged. */
    public Tween restart(float startValue, float target, long nowMs, int durationMs, Easing easing) {
        this.from = startValue;
        this.to = target;
        this.start = nowMs;
        this.duration = Math.max(0, durationMs);
        this.easing = easing;
        return this;
    }

    public Tween snap(float value) {
        this.from = value;
        this.to = value;
        this.duration = 0;
        return this;
    }

    public float value(long nowMs) {
        if (duration <= 0 || nowMs >= start + duration) {
            return to;
        }
        if (nowMs <= start) {
            return from;
        }
        float t = (nowMs - start) / (float) duration;
        return from + (to - from) * easing.apply(t);
    }

    public float target() {
        return to;
    }

    public boolean done(long nowMs) {
        return duration <= 0 || nowMs >= start + duration;
    }
}
