package com.mcspacewizard.emergentstealth.client.ui.widget;

import java.util.function.IntConsumer;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.SwipePager;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Swipeable pages (design doc 31 §0): each child is a page the size of this node. A horizontal drag (or a
 * horizontal trackpad scroll) slides between them and snaps; {@link #select} animates there. Tab bars
 * follow {@link #position} so their marker moves with the swipe.
 */
public class UiPager extends UiNode {
    private final SwipePager pager;
    private @Nullable IntConsumer onChange;
    private int lastIndex;
    private float wheelAccum;
    private long wheelCooldownUntil;
    private long now;

    public UiPager(int pages, int initial) {
        this.pager = new SwipePager(pages);
        this.pager.select(initial, 0L, 0);
        this.lastIndex = initial;
    }

    public UiPager onChange(IntConsumer onChange) {
        this.onChange = onChange;
        return this;
    }

    public int index() {
        return pager.index();
    }

    /** Continuous page position (for tab markers). */
    public float position() {
        return pager.position(now == 0 ? Sumi.now() : now);
    }

    public void select(int page) {
        pager.select(page, Sumi.now(), Sumi.duration(SumiTheme.SLOW));
        fireChange();
    }

    /** Swipes with a drag delta from outside (e.g. a canvas that's out of room to pan). */
    public void dragFrom(float dx, long nowMs) {
        pager.drag(dx, nowMs);
    }

    public void releaseFrom(long nowMs) {
        pager.release(nowMs, Sumi.duration(SumiTheme.SLOW));
        fireChange();
    }

    public boolean isDragging() {
        return pager.isDragging();
    }

    private void fireChange() {
        if (pager.index() != lastIndex) {
            lastIndex = pager.index();
            if (onChange != null) {
                onChange.accept(lastIndex);
            }
        }
    }

    @Override
    protected void layoutChildren() {
        pager.setPageWidth(width);
        for (UiNode child : children) {
            child.layout(x, y, width, height);
        }
    }

    @Override
    public void update(UiContext ctx) {
        now = ctx.now();
        super.update(ctx);
    }

    @Override
    protected float childOffsetX(UiNode child) {
        int i = children.indexOf(child);
        return (position() - i) * width;
    }

    @Override
    protected boolean childHittable(UiNode child) {
        return Math.abs(children.indexOf(child) - position()) < 0.5F;
    }

    @Override
    protected boolean clipsChildren() {
        return true;
    }

    @Override
    protected void renderChildren(UiContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        float pos = position();
        g.enableScissor(x, y, x + width, y + height);
        for (int i = 0; i < children.size(); i++) {
            if (Math.abs(i - pos) >= 1.0F) {
                continue;
            }
            UiNode child = children.get(i);
            g.pose().pushMatrix();
            g.pose().translate((i - pos) * width, 0);
            child.render(ctx);
            g.pose().popMatrix();
        }
        g.disableScissor();
    }

    @Override
    public boolean claimDrag(UiContext ctx, Gesture gesture) {
        if (!gesture.horizontal() || children.size() < 2) {
            return false;
        }
        pager.beginDrag(ctx.now());
        return true;
    }

    @Override
    public void drag(UiContext ctx, double mx, double my, double dx, double dy) {
        pager.drag((float) dx, ctx.now());
    }

    @Override
    public void dragEnd(UiContext ctx) {
        releaseFrom(ctx.now());
    }

    @Override
    public boolean scroll(UiContext ctx, double mx, double my, double scrollX, double scrollY) {
        if (scrollX == 0 || Math.abs(scrollX) < Math.abs(scrollY) || children.size() < 2) {
            return false;
        }
        if (ctx.now() < wheelCooldownUntil) {
            return true;
        }
        wheelAccum += (float) scrollX;
        if (Math.abs(wheelAccum) >= 1.5F) {
            select(pager.index() + (wheelAccum > 0 ? -1 : 1));
            wheelAccum = 0;
            wheelCooldownUntil = ctx.now() + 350L;
        }
        return true;
    }

    @Override
    public void reveal(UiNode node) {
        for (int i = 0; i < children.size(); i++) {
            if (isWithin(node, children.get(i)) && i != pager.index()) {
                select(i);
            }
        }
        super.reveal(node);
    }

    private static boolean isWithin(UiNode node, UiNode ancestor) {
        for (UiNode n = node; n != null; n = n.parent()) {
            if (n == ancestor) {
                return true;
            }
        }
        return false;
    }
}
