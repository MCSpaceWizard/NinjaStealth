package com.mcspacewizard.emergentstealth.ui;

/**
 * Wedge maths for radial menus such as the tool wheel (design doc 34 §1). Side-neutral so GameTests can check it.
 * Angles are radians in screen space (x right, y down): 0 points right and angles grow clockwise. Wedge 0 is
 * centred on the top and the rest follow clockwise.
 */
public final class RadialMenu {
    private RadialMenu() {}

    /** The top of the screen. */
    public static final double TOP = -Math.PI / 2.0;

    /** How wide each of {@code n} wedges is. */
    public static double sweep(int n) {
        return n <= 0 ? 0.0 : Math.PI * 2.0 / n;
    }

    /** The centre angle of wedge {@code i} of {@code n}. */
    public static double angleOf(int i, int n) {
        return TOP + sweep(n) * i;
    }

    /** Where wedge {@code i} starts (its counter-clockwise edge). */
    public static double startOf(int i, int n) {
        return angleOf(i, n) - sweep(n) / 2.0;
    }

    /** The wedge in the direction of ({@code dx}, {@code dy}) from the centre, or -1 inside the dead zone or with no wedges. */
    public static int indexAt(double dx, double dy, int n, double deadZone) {
        if (n <= 0 || dx * dx + dy * dy < deadZone * deadZone) {
            return -1;
        }
        double fromTop = Math.atan2(dy, dx) - TOP;
        return Math.floorMod((int) Math.round(fromTop / sweep(n)), n);
    }

    /**
     * Steps round the ring ({@code delta} positive = clockwise), wrapping. From nothing selected, a clockwise step
     * lands on the first wedge and an anticlockwise one on the last.
     */
    public static int step(int current, int n, int delta) {
        if (n <= 0) {
            return -1;
        }
        if (current < 0 || current >= n) {
            return delta >= 0 ? Math.floorMod(delta - 1, n) : Math.floorMod(delta, n);
        }
        return Math.floorMod(current + delta, n);
    }

    /** Number keys 1–9 pick wedges 0–8 directly; -1 if there is no such wedge. */
    public static int numberKey(int digit, int n) {
        return digit >= 1 && digit <= 9 && digit <= n ? digit - 1 : -1;
    }

    /** The shortest signed turn from angle {@code from} to {@code to}, in (-π, π]. */
    public static double turn(double from, double to) {
        double d = (to - from) % (Math.PI * 2.0);
        if (d > Math.PI) {
            d -= Math.PI * 2.0;
        } else if (d <= -Math.PI) {
            d += Math.PI * 2.0;
        }
        return d;
    }
}
