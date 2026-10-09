package com.mcspacewizard.emergentstealth.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.ui.Easing;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.Tween;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A vanilla {@link net.minecraft.client.gui.screens.Screen} that hosts a Sumi node tree (design doc 31 §2
 * Core): it builds the tree, routes pointer and keyboard input (bubbling, drag claiming, Tab focus order),
 * steps animations, plays the open animation, and draws tooltips as paper cards.
 */
public abstract class UiScreen extends net.minecraft.client.gui.screens.Screen {
    private static final double CLICK_SLOP = 3.0;

    protected @Nullable UiNode root;
    protected final UiContext ctx = new UiContext(this);
    private final Tween open = new Tween(0.0F);
    private long lastFrame;

    private @Nullable UiNode pressed;
    private @Nullable UiNode dragOwner;
    private int pressButton = -1;
    private double pressX;
    private double pressY;
    private double dragTotalX;
    private double dragTotalY;

    private @Nullable UiNode focusedNode;
    private @Nullable UiNode tooltipNode;
    private long tooltipSince;
    private final Tween tooltipFade = new Tween(0.0F);

    protected UiScreen(Component title) {
        super(title);
    }

    /** Builds the node tree for the current screen size. Called on open and on resize. */
    protected abstract UiNode build();

    @Override
    protected void init() {
        boolean first = root == null;
        Sumi.reloadTheme();
        // Cleared before building, so build() may pick the starting focus with setFocus().
        focusedNode = null;
        pressed = null;
        dragOwner = null;
        root = build();
        root.layout(0, 0, width, height);
        long now = Sumi.now();
        if (first) {
            open.restart(0.0F, 1.0F, now, Sumi.duration(SumiTheme.SLOW), Easing.QUART_OUT);
            lastFrame = now;
        }
    }

    /** Re-runs layout (after content changes size). */
    public void relayout() {
        if (root != null) {
            root.layout(0, 0, width, height);
        }
    }

    public Font font() {
        return font;
    }

    @Nullable UiNode pressed() {
        return pressed;
    }

    /** 0..1 progress of the open animation. */
    protected float openProgress() {
        return open.value(Sumi.now());
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        SumiTheme theme = Sumi.theme();
        float t = openProgress();
        int backdrop = theme.color(SumiTheme.BACKDROP);
        if (minecraft.level == null) {
            Paint.gradientV(graphics, 0, 0, width, height, Paint.mix(backdrop, theme.color(SumiTheme.INK_SOFT), 0.35F), backdrop);
        } else {
            float a = theme.metricF(SumiTheme.BACKDROP_ALPHA) * t;
            Paint.gradientV(graphics, 0, 0, width, height, Paint.fade(backdrop, a * 0.75F), Paint.fade(backdrop, a));
        }
        // An ink wash bleeding in from the lower corners.
        float r = Math.max(width, height) * 0.55F;
        Paint.glow(graphics, 0, height, r * (0.6F + 0.4F * t), Paint.fade(backdrop, 0.45F * t), Paint.fade(backdrop, 0.0F));
        Paint.glow(graphics, width, height, r * 0.8F * (0.6F + 0.4F * t), Paint.fade(backdrop, 0.35F * t), Paint.fade(backdrop, 0.0F));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (root == null) {
            return;
        }
        long now = Sumi.now();
        ctx.now = now;
        ctx.dt = Math.clamp((now - lastFrame) / 1000.0F, 0.0F, 0.1F);
        lastFrame = now;
        ctx.mouseX = mouseX;
        ctx.mouseY = mouseY;
        ctx.hovered = root.hit(mouseX, mouseY);
        root.update(ctx);

        ctx.setGraphics(graphics);
        float t = openProgress();
        float previous = Paint.pushAlpha(t);
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, (1.0F - t) * 14.0F);
        root.render(ctx);
        graphics.pose().popMatrix();
        Paint.popAlpha(previous);

        graphics.nextStratum();
        drawTooltip(graphics, now);
        drawOverlay(graphics, mouseX, mouseY);
        ctx.setGraphics(null);
    }

    /** Hook for subclasses: drawn above everything, including tooltips. */
    protected void drawOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {}

    private void drawTooltip(GuiGraphicsExtractor graphics, long now) {
        UiNode source = ctx.keyboardMode ? focusedNode : ctx.hovered;
        UiNode withTip = source;
        while (withTip != null && withTip.tooltip() == null) {
            withTip = withTip.parent();
        }
        if (dragOwner != null) {
            withTip = null;
        }
        if (withTip != tooltipNode) {
            tooltipNode = withTip;
            tooltipSince = now;
            tooltipFade.snap(0.0F);
        }
        if (tooltipNode == null || now - tooltipSince < Sumi.theme().ms(SumiTheme.TOOLTIP_DELAY)) {
            return;
        }
        tooltipFade.animate(1.0F, now, Sumi.duration(SumiTheme.FAST), Easing.CUBIC_OUT);
        Supplier<List<Component>> lines = tooltipNode.tooltip();
        List<Component> text = lines != null ? lines.get() : List.of();
        if (text.isEmpty()) {
            return;
        }
        int ax;
        int ay;
        if (ctx.keyboardMode) {
            float[] box = tooltipNode.screenBox();
            ax = (int) (box[0] + box[2] / 2);
            ay = (int) (box[1] + box[3]);
        } else {
            ax = (int) ctx.mouseX + 10;
            ay = (int) ctx.mouseY + 12;
        }
        float fade = tooltipFade.value(now);
        float previous = Paint.pushAlpha(fade);
        drawCard(graphics, text, ax, ay, Sumi.theme().metric(SumiTheme.CARD_WIDTH), (1.0F - fade) * 4.0F);
        Paint.popAlpha(previous);
    }

    /** A tooltip-style paper card: the first line as a heading, the rest wrapped and muted. Kept on screen. */
    public void drawCard(GuiGraphicsExtractor graphics, List<Component> lines, int anchorX, int anchorY, int maxWidth, float slide) {
        SumiTheme theme = Sumi.theme();
        int pad = theme.metric(SumiTheme.PADDING) - 2;
        int inner = 0;
        for (Component line : lines) {
            inner = Math.max(inner, Math.min(maxWidth, font.width(line)));
        }
        int textHeight = 0;
        for (int i = 0; i < lines.size(); i++) {
            textHeight += Paint.wrappedHeight(font, lines.get(i), inner) + (i == 0 && lines.size() > 1 ? 3 : 0);
        }
        int w = inner + pad * 2;
        int h = textHeight + pad * 2 - 1;
        int x = Math.clamp(anchorX, 2, Math.max(2, width - w - 2));
        int y = anchorY + h > height - 2 ? Math.max(2, anchorY - h - 20) : anchorY;
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, slide);
        Paint.panel(graphics, x, y, w, h, theme.color(SumiTheme.PAPER));
        Paint.rect(graphics, x, y + 3, 2, h - 6, theme.color(SumiTheme.LACQUER));
        int ty = y + pad;
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? theme.color(SumiTheme.TEXT) : theme.color(SumiTheme.TEXT_MUTED);
            ty += Paint.wrapped(graphics, font, lines.get(i), x + pad, ty, inner, color);
            if (i == 0 && lines.size() > 1) {
                ty += 3;
            }
        }
        graphics.pose().popMatrix();
    }

    // ------------------------------------------------------------------ pointer input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (root == null) {
            return false;
        }
        ctx.keyboardMode = false;
        UiNode target = root.hit(event.x(), event.y());
        pressButton = event.button();
        pressX = event.x();
        pressY = event.y();
        dragTotalX = 0;
        dragTotalY = 0;
        dragOwner = null;
        pressed = null;
        for (UiNode n = target; n != null; n = n.parent()) {
            double[] local = n.toLocal(event.x(), event.y());
            if (n.mouseDown(ctx, local[0], local[1], event.button())) {
                pressed = n;
                if (n.isFocusable()) {
                    setFocus(n);
                }
                return true;
            }
        }
        // Nothing took the press, but a container may still want to drag from here.
        pressed = target;
        return target != null;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (pressed == null || event.button() != pressButton) {
            return false;
        }
        dragTotalX += dx;
        dragTotalY += dy;
        if (dragOwner == null) {
            if (Math.hypot(dragTotalX, dragTotalY) < CLICK_SLOP) {
                return true;
            }
            UiNode.Gesture gesture = new UiNode.Gesture(pressX, pressY, dragTotalX, dragTotalY, pressButton);
            for (UiNode n = pressed; n != null; n = n.parent()) {
                if (n.claimDrag(ctx, gesture)) {
                    dragOwner = n;
                    break;
                }
            }
            if (dragOwner == null) {
                return true;
            }
            if (dragOwner != pressed) {
                pressed.pressCancelled(ctx);
            }
            double[] local = dragOwner.toLocal(event.x(), event.y());
            dragOwner.drag(ctx, local[0], local[1], dragTotalX, dragTotalY);
            return true;
        }
        double[] local = dragOwner.toLocal(event.x(), event.y());
        dragOwner.drag(ctx, local[0], local[1], dx, dy);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() != pressButton) {
            return false;
        }
        UiNode owner = dragOwner;
        UiNode press = pressed;
        dragOwner = null;
        pressed = null;
        pressButton = -1;
        if (owner != null) {
            owner.dragEnd(ctx);
            return true;
        }
        if (press != null && root != null) {
            double[] local = press.toLocal(event.x(), event.y());
            boolean inside = press.contains(local[0], local[1]) && root.hit(event.x(), event.y()) == press;
            press.mouseUp(ctx, local[0], local[1], event.button(), inside);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (root == null) {
            return false;
        }
        for (UiNode n = root.hit(mx, my); n != null; n = n.parent()) {
            double[] local = n.toLocal(mx, my);
            if (n.scroll(ctx, local[0], local[1], scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (Math.abs(mx - ctx.mouseX) + Math.abs(my - ctx.mouseY) > 2) {
            ctx.keyboardMode = false;
        }
    }

    // ------------------------------------------------------------------ keyboard input

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (root == null) {
            return super.keyPressed(event);
        }
        if (event.key() == GLFW.GLFW_KEY_TAB) {
            ctx.keyboardMode = true;
            moveFocus(!event.hasShiftDown());
            return true;
        }
        for (UiNode n = focusedNode; n != null; n = n.parent()) {
            if (n.key(ctx, event)) {
                ctx.keyboardMode = true;
                return true;
            }
        }
        if (screenKey(event)) {
            return true;
        }
        if (event.isEscape() && shouldCloseOnEsc()) {
            onClose();
            return true;
        }
        return false;
    }

    /** Screen-wide shortcuts, after the focused node passed on the key. */
    protected boolean screenKey(KeyEvent event) {
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        for (UiNode n = focusedNode; n != null; n = n.parent()) {
            if (n.character(ctx, event)) {
                return true;
            }
        }
        return false;
    }

    public void setFocus(@Nullable UiNode node) {
        if (focusedNode == node) {
            return;
        }
        if (focusedNode != null) {
            focusedNode.setFocused(false);
        }
        focusedNode = node;
        if (node != null) {
            node.setFocused(true);
            node.reveal(node);
        }
    }

    public @Nullable UiNode focused() {
        return focusedNode;
    }

    private void moveFocus(boolean forward) {
        if (root == null) {
            return;
        }
        List<UiNode> order = new ArrayList<>();
        root.collectFocusable(order);
        if (order.isEmpty()) {
            return;
        }
        int at = focusedNode == null ? -1 : order.indexOf(focusedNode);
        int next = at < 0 ? (forward ? 0 : order.size() - 1) : Math.floorMod(at + (forward ? 1 : -1), order.size());
        setFocus(order.get(next));
    }

    public void enableKeyboardMode() {
        ctx.keyboardMode = true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
