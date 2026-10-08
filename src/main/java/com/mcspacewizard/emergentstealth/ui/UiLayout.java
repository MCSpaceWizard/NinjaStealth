package com.mcspacewizard.emergentstealth.ui;

/** Layout maths for Sumi containers (design doc 31 §2): rows, columns and grids. Pure integer maths. */
public final class UiLayout {
    private UiLayout() {}

    /** Padding or margins. */
    public record Insets(int left, int top, int right, int bottom) {
        public static final Insets NONE = new Insets(0, 0, 0, 0);

        public static Insets all(int v) {
            return new Insets(v, v, v, v);
        }

        public static Insets of(int horizontal, int vertical) {
            return new Insets(horizontal, vertical, horizontal, vertical);
        }

        public int horizontal() {
            return left + right;
        }

        public int vertical() {
            return top + bottom;
        }
    }

    /**
     * Sizes children along one axis, like CSS flex: children with weight 0 get their preferred size; the
     * space left over (after gaps) is shared by weight. Rounding leftovers go to the last weighted child so
     * the sizes always add up exactly.
     */
    public static int[] flex(int available, int gap, int[] preferred, float[] weights) {
        int n = preferred.length;
        int[] sizes = new int[n];
        if (n == 0) {
            return sizes;
        }
        int fixed = gap * (n - 1);
        float totalWeight = 0.0F;
        int lastWeighted = -1;
        for (int i = 0; i < n; i++) {
            if (weights[i] > 0.0F) {
                totalWeight += weights[i];
                lastWeighted = i;
            } else {
                sizes[i] = Math.max(0, preferred[i]);
                fixed += sizes[i];
            }
        }
        int free = Math.max(0, available - fixed);
        int given = 0;
        for (int i = 0; i < n; i++) {
            if (weights[i] > 0.0F) {
                sizes[i] = i == lastWeighted ? free - given : (int) Math.floor(free * weights[i] / totalWeight);
                given += sizes[i];
            }
        }
        return sizes;
    }

    /** Start offsets for consecutive sizes separated by {@code gap}. */
    public static int[] offsets(int start, int gap, int[] sizes) {
        int[] out = new int[sizes.length];
        int at = start;
        for (int i = 0; i < sizes.length; i++) {
            out[i] = at;
            at += sizes[i] + gap;
        }
        return out;
    }

    /** Total length of sizes plus gaps. */
    public static int total(int gap, int[] sizes) {
        int sum = 0;
        for (int size : sizes) {
            sum += size;
        }
        return sum + Math.max(0, sizes.length - 1) * gap;
    }

    /** Cell positions ({@code [i][0]} = x, {@code [i][1]} = y) for a grid filled row by row. */
    public static int[][] grid(int count, int columns, int cellWidth, int cellHeight, int gapX, int gapY) {
        int cols = Math.max(1, columns);
        int[][] out = new int[count][2];
        for (int i = 0; i < count; i++) {
            out[i][0] = (i % cols) * (cellWidth + gapX);
            out[i][1] = (i / cols) * (cellHeight + gapY);
        }
        return out;
    }

    /** How many columns of {@code cellWidth} fit in {@code available}. At least 1. */
    public static int columnsThatFit(int available, int cellWidth, int gap) {
        return Math.max(1, (available + gap) / Math.max(1, cellWidth + gap));
    }
}
