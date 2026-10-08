package com.mcspacewizard.emergentstealth.client.ui.widget;

import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.ui.UiLayout;

/**
 * A row or column (design doc 31 §2 Layout). Children get their preferred size along the axis unless they
 * have a flex weight, in which case they share the leftover space. Across the axis they stretch, or are
 * centred with {@link #center()}.
 */
public class UiFlex extends UiNode {
    public enum Axis { ROW, COLUMN }

    private final Axis axis;
    private int gap;
    private UiLayout.Insets padding = UiLayout.Insets.NONE;
    private boolean center;

    public UiFlex(Axis axis) {
        this.axis = axis;
    }

    public static UiFlex row() {
        return new UiFlex(Axis.ROW);
    }

    public static UiFlex column() {
        return new UiFlex(Axis.COLUMN);
    }

    public UiFlex gap(int gap) {
        this.gap = gap;
        return this;
    }

    public UiFlex padding(UiLayout.Insets padding) {
        this.padding = padding;
        return this;
    }

    public UiFlex padding(int all) {
        return padding(UiLayout.Insets.all(all));
    }

    /** Centre children across the axis at their preferred size instead of stretching them. */
    public UiFlex center() {
        this.center = true;
        return this;
    }

    @Override
    public int preferredWidth() {
        if (prefWidth >= 0) {
            return prefWidth;
        }
        int total = 0;
        int count = 0;
        for (UiNode child : children) {
            if (!child.isVisible()) {
                continue;
            }
            count++;
            total = axis == Axis.ROW ? total + child.preferredWidth() : Math.max(total, child.preferredWidth());
        }
        if (axis == Axis.ROW && count > 1) {
            total += gap * (count - 1);
        }
        return total + padding.horizontal();
    }

    @Override
    public int preferredHeight(int forWidth) {
        if (prefHeight >= 0) {
            return prefHeight;
        }
        int inner = forWidth - padding.horizontal();
        int total = 0;
        int count = 0;
        if (axis == Axis.ROW) {
            int[] widths = rowWidths(inner);
            int i = 0;
            for (UiNode child : children) {
                if (child.isVisible()) {
                    total = Math.max(total, child.preferredHeight(widths[i++]));
                }
            }
        } else {
            for (UiNode child : children) {
                if (child.isVisible()) {
                    count++;
                    total += child.preferredHeight(inner);
                }
            }
            if (count > 1) {
                total += gap * (count - 1);
            }
        }
        return total + padding.vertical();
    }

    private int[] rowWidths(int inner) {
        int n = (int) children.stream().filter(UiNode::isVisible).count();
        int[] pref = new int[n];
        float[] weights = new float[n];
        int i = 0;
        for (UiNode child : children) {
            if (child.isVisible()) {
                pref[i] = child.preferredWidth();
                weights[i] = child.flexWeight();
                i++;
            }
        }
        return UiLayout.flex(inner, gap, pref, weights);
    }

    @Override
    protected void layoutChildren() {
        int ix = x + padding.left();
        int iy = y + padding.top();
        int iw = Math.max(0, width - padding.horizontal());
        int ih = Math.max(0, height - padding.vertical());
        int n = (int) children.stream().filter(UiNode::isVisible).count();
        int[] pref = new int[n];
        float[] weights = new float[n];
        int i = 0;
        for (UiNode child : children) {
            if (child.isVisible()) {
                pref[i] = axis == Axis.ROW ? child.preferredWidth() : child.preferredHeight(iw);
                weights[i] = child.flexWeight();
                i++;
            }
        }
        int[] sizes = UiLayout.flex(axis == Axis.ROW ? iw : ih, gap, pref, weights);
        int[] offsets = UiLayout.offsets(axis == Axis.ROW ? ix : iy, gap, sizes);
        i = 0;
        for (UiNode child : children) {
            if (!child.isVisible()) {
                continue;
            }
            if (axis == Axis.ROW) {
                int h = center ? Math.min(ih, child.preferredHeight(sizes[i])) : ih;
                child.layout(offsets[i], iy + (ih - h) / 2, sizes[i], h);
            } else {
                int w = center ? Math.min(iw, child.preferredWidth()) : iw;
                child.layout(ix + (iw - w) / 2, offsets[i], w, sizes[i]);
            }
            i++;
        }
    }
}
