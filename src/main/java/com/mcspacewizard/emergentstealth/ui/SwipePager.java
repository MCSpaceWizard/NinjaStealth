package com.mcspacewizard.emergentstealth.ui;

/**
 * Pages (tabs) that can be swiped with a drag and snap into place (design doc 31 §0). The position is in
 * pages: 1.4 means 40% of the way from page 1 to page 2. Draw page {@code i} at
 * {@code x = (i - position) * pageWidth}.
 */
public final class SwipePager {
    /** Fraction of a page a slow drag must cover to change page. */
    private static final float SNAP_FRACTION = 0.22F;
    /** Fling speed (px/s) that changes page regardless of distance. */
    private static final float FLING_SPEED = 450.0F;
    private static final float EDGE_RESISTANCE = 0.3F;

    private final int pages;
    private int index;
    private final Tween settle = new Tween(0.0F);
    private float pageWidth = 1.0F;
    private boolean dragging;
    private float dragBase;
    private float dragOffset;
    private float velocity;
    private long lastDragMs;

    public SwipePager(int pages) {
        this.pages = Math.max(1, pages);
    }

    public int pages() {
        return pages;
    }

    /** The page being shown, or being snapped to. */
    public int index() {
        return index;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void setPageWidth(float width) {
        this.pageWidth = Math.max(1.0F, width);
    }

    public float position(long nowMs) {
        if (!dragging) {
            return settle.value(nowMs);
        }
        float raw = dragBase - dragOffset / pageWidth;
        if (raw < 0.0F) {
            return raw * EDGE_RESISTANCE;
        }
        if (raw > pages - 1) {
            return (pages - 1) + (raw - (pages - 1)) * EDGE_RESISTANCE;
        }
        return raw;
    }

    public void select(int page, long nowMs, int durationMs) {
        index = Math.clamp(page, 0, pages - 1);
        dragging = false;
        settle.restart(settle.value(nowMs), index, nowMs, durationMs, Easing.CUBIC_OUT);
    }

    public void beginDrag(long nowMs) {
        dragBase = position(nowMs);
        dragOffset = 0.0F;
        velocity = 0.0F;
        lastDragMs = nowMs;
        dragging = true;
    }

    /** {@code dx}: pointer movement in pixels (positive = right, towards the previous page). */
    public void drag(float dx, long nowMs) {
        if (!dragging) {
            beginDrag(nowMs);
        }
        dragOffset += dx;
        float dt = Math.clamp((nowMs - lastDragMs) / 1000.0F, 1.0F / 240.0F, 0.1F);
        velocity = 0.6F * (dx / dt) + 0.4F * velocity;
        lastDragMs = nowMs;
    }

    /** Ends a drag and snaps to the nearest sensible page. */
    public void release(long nowMs, int durationMs) {
        if (!dragging) {
            return;
        }
        float current = position(nowMs);
        dragging = false;
        if (nowMs - lastDragMs > 80L) {
            velocity = 0.0F;
        }
        int origin = Math.round(dragBase);
        int next = origin;
        if (velocity < -FLING_SPEED || current - origin > SNAP_FRACTION) {
            next = origin + 1;
        } else if (velocity > FLING_SPEED || origin - current > SNAP_FRACTION) {
            next = origin - 1;
        }
        index = Math.clamp(next, 0, pages - 1);
        settle.restart(current, index, nowMs, durationMs, Easing.CUBIC_OUT);
    }
}
