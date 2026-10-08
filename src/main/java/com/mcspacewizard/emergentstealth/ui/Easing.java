package com.mcspacewizard.emergentstealth.ui;

/** Easing curves for {@link Tween}s (design doc 31 §0). Each maps 0..1 to 0..1 (BACK_OUT overshoots briefly). */
public enum Easing {
    LINEAR,
    QUAD_OUT,
    CUBIC_OUT,
    CUBIC_IN_OUT,
    QUART_OUT,
    EXPO_OUT,
    BACK_OUT,
    SINE_IN_OUT;

    public float apply(float t) {
        if (t <= 0.0F) {
            return 0.0F;
        }
        if (t >= 1.0F) {
            return 1.0F;
        }
        return switch (this) {
            case LINEAR -> t;
            case QUAD_OUT -> 1.0F - (1.0F - t) * (1.0F - t);
            case CUBIC_OUT -> 1.0F - cube(1.0F - t);
            case CUBIC_IN_OUT -> t < 0.5F ? 4.0F * t * t * t : 1.0F - cube(-2.0F * t + 2.0F) / 2.0F;
            case QUART_OUT -> 1.0F - (float) Math.pow(1.0F - t, 4);
            case EXPO_OUT -> 1.0F - (float) Math.pow(2.0, -10.0 * t);
            case BACK_OUT -> {
                float c1 = 1.70158F;
                float c3 = c1 + 1.0F;
                yield 1.0F + c3 * cube(t - 1.0F) + c1 * (t - 1.0F) * (t - 1.0F);
            }
            case SINE_IN_OUT -> (float) (-(Math.cos(Math.PI * t) - 1.0) / 2.0);
        };
    }

    private static float cube(float v) {
        return v * v * v;
    }
}
