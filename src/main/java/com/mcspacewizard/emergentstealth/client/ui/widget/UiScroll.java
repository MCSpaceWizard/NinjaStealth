package com.mcspacewizard.emergentstealth.client.ui.widget;

import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.ui.Easing;
import com.mcspacewizard.emergentstealth.ui.InertialScroll;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.Tween;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

/**
 * A vertical scroll view with inertia (design doc 31 §2 Layout): the wheel glides, a drag flings and
 * rubber-bands, and a thin ink scrollbar fades in while moving. Content taller than the view scrolls; the
 * content is one child laid out at its preferred height.
 */
public class UiScroll extends UiNode {
    private static final float WHEEL_STEP = 28.0F;
    private static final int GUTTER = 6;

    private final UiNode content;
    private final InertialScroll scroll = new InertialScroll();
    private final Tween barAlpha = new Tween(0.0F);
    private long lastActivity;
    private int contentHeight;

    public UiScroll(UiNode content) {
        this.content = add(content);
    }

    public UiNode content() {
        return content;
    }

    public InertialScroll state() {
        return scroll;
    }

    @Override
    protected void layoutChildren() {
        int cw = width - GUTTER;
        contentHeight = Math.max(height, content.preferredHeight(cw));
        content.layout(x, y, cw, contentHeight);
        scroll.setMax(contentHeight - height);
    }

    @Override
    protected float childOffsetY(UiNode child) {
        return Math.round(scroll.position() * 2.0F) / 2.0F;
    }

    @Override
    protected boolean clipsChildren() {
        return true;
    }

    @Override
    public void update(UiContext ctx) {
        scroll.setInstant(Sumi.reducedMotion());
        scroll.update(ctx.dt());
        if (!scroll.isSettled()) {
            lastActivity = ctx.now();
        }
        boolean show = scroll.max() > 0 && (ctx.now() - lastActivity < 900L || ctx.isHoveredWithin(this));
        barAlpha.animate(show ? 1.0F : 0.0F, ctx.now(), Sumi.duration(SumiTheme.NORMAL), Easing.CUBIC_OUT);
        super.update(ctx);
    }

    @Override
    protected void renderChildren(UiContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        g.enableScissor(x, y, x + width, y + height);
        super.renderChildren(ctx);
        g.disableScissor();
        // Soft paper fades at the edges hint at more content.
        int paper = ctx.theme().color(SumiTheme.PAPER);
        if (scroll.position() > 1.0F) {
            Paint.gradientV(g, x, y, width - GUTTER, 8, Paint.fade(paper, 0.9F), Paint.fade(paper, 0.0F));
        }
        if (scroll.position() < scroll.max() - 1.0F) {
            Paint.gradientV(g, x, y + height - 8, width - GUTTER, 8, Paint.fade(paper, 0.0F), Paint.fade(paper, 0.9F));
        }
        float a = barAlpha.value(ctx.now());
        if (a > 0.01F && scroll.max() > 0) {
            float viewRatio = height / (float) contentHeight;
            float barH = Math.max(14.0F, height * viewRatio);
            float over = scroll.position() < 0 ? -scroll.position() : Math.max(0, scroll.position() - scroll.max());
            barH = Math.max(8.0F, barH - over * 0.5F);
            float t = scroll.max() <= 0 ? 0 : Math.clamp(scroll.position() / scroll.max(), 0.0F, 1.0F);
            float by = y + 2 + (height - 4 - barH) * t;
            float bx = x + width - 3.5F;
            int ink = Paint.fade(ctx.theme().color(SumiTheme.INK), 0.55F * a);
            Paint.line(g, bx, by + 1.5F, bx, by + barH - 1.5F, 3.0F, ink);
            Paint.circle(g, bx, by + 1.5F, 1.5F, ink);
            Paint.circle(g, bx, by + barH - 1.5F, 1.5F, ink);
        }
    }

    @Override
    public boolean scroll(UiContext ctx, double mx, double my, double scrollX, double scrollY) {
        if (scroll.max() <= 0 || scrollY == 0) {
            return false;
        }
        scroll.scrollBy((float) (-scrollY * WHEEL_STEP));
        lastActivity = ctx.now();
        return true;
    }

    @Override
    public boolean claimDrag(UiContext ctx, Gesture gesture) {
        if (scroll.max() <= 0 || gesture.horizontal()) {
            return false;
        }
        scroll.beginDrag(ctx.now());
        return true;
    }

    @Override
    public void drag(UiContext ctx, double mx, double my, double dx, double dy) {
        scroll.dragBy((float) -dy, ctx.now());
        lastActivity = ctx.now();
    }

    @Override
    public void dragEnd(UiContext ctx) {
        scroll.endDrag(ctx.now());
    }

    @Override
    public boolean key(UiContext ctx, KeyEvent event) {
        return switch (event.key()) {
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                scroll.scrollBy(height * 0.85F);
                yield true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                scroll.scrollBy(-height * 0.85F);
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public void reveal(UiNode node) {
        if (node != this && isDescendant(node)) {
            int top = node.y() - y;
            int bottom = top + node.height();
            float pos = scroll.target();
            if (top < pos + 4) {
                scroll.scrollTo(top - 4, false);
            } else if (bottom > pos + height - 4) {
                scroll.scrollTo(bottom - height + 4, false);
            }
        }
        super.reveal(node);
    }

    private boolean isDescendant(UiNode node) {
        for (UiNode n = node.parent(); n != null; n = n.parent()) {
            if (n == this) {
                return true;
            }
        }
        return false;
    }
}
