package com.mcspacewizard.emergentstealth.client.ui.widget;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.ui.Easing;
import com.mcspacewizard.emergentstealth.ui.RadialMenu;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.Tween;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A radial menu of item wedges round a paper disc (design doc 34 §1): the tool wheel. Fills its box and centres
 * the ring in it. Point with the mouse (outside the dead zone), step with the mouse wheel or the arrow keys, pick
 * directly with 1–9, choose with a click or Enter. The wedge maths lives in {@link RadialMenu}.
 * <p>
 * The owner reads {@link #selected()} (for hold-and-release menus) or listens with {@link #onChoose}.
 */
public class UiRadial extends UiNode {
    /** Alpha of a wedge with no icon (an empty slot). */
    private static final float BLANK_ALPHA = 0.55F;

    /**
     * One wedge: an item icon, a live count (hidden when 1 or less) and whether to mark it with a seal. An empty
     * icon draws a faint, empty wedge (it can still be pointed at and chosen).
     */
    public record Entry(ItemStack icon, IntSupplier count, BooleanSupplier marked) {}

    private static final float GAP_PX = 1.2F;
    private static final float POP_PX = 6.0F;
    private static final int UNSET = Integer.MIN_VALUE;

    private final List<Entry> entries;
    private final Tween[] hover;
    private final Tween pointerFade = new Tween(0.0F);
    private IntFunction<List<Component>> centre = i -> List.of();
    private IntFunction<List<Component>> caption = i -> List.of();
    private IntConsumer onChoose = i -> {};

    private int selected = -1;
    private int lastSelected = UNSET;
    private boolean manual;
    private double manualX;
    private double manualY;
    private float pointer;
    private long openedAt = -1;

    public UiRadial(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        this.hover = new Tween[this.entries.size()];
        for (int i = 0; i < hover.length; i++) {
            hover[i] = new Tween(0.0F);
        }
    }

    /** Lines in the centre disc for the selected wedge (-1: none); the first is the heading. */
    public UiRadial centre(IntFunction<List<Component>> lines) {
        this.centre = lines;
        return this;
    }

    /** Lines on a paper strip under the ring for the selected wedge (-1: none). */
    public UiRadial caption(IntFunction<List<Component>> lines) {
        this.caption = lines;
        return this;
    }

    public UiRadial onChoose(IntConsumer onChoose) {
        this.onChoose = onChoose;
        return this;
    }

    /** The wedge pointed at or stepped to, or -1. */
    public int selected() {
        return selected;
    }

    // ------------------------------------------------------------------ geometry

    private float cx() {
        return x + width / 2.0F;
    }

    private float cy() {
        return y + height / 2.0F - 8.0F;
    }

    private float outer() {
        return Math.clamp(Math.min(width, height) * 0.34F, 60.0F, 112.0F);
    }

    private float inner() {
        return outer() * 0.5F;
    }

    private float deadZone() {
        return inner() * 0.55F;
    }

    // ------------------------------------------------------------------ state

    @Override
    public void update(UiContext ctx) {
        long now = ctx.now();
        if (openedAt < 0) {
            openedAt = now;
        }
        int n = entries.size();
        double mx = ctx.mouseX();
        double my = ctx.mouseY();
        if (manual && Math.hypot(mx - manualX, my - manualY) > 4.0) {
            manual = false;
        }
        if (!manual) {
            selected = RadialMenu.indexAt(mx - cx(), my - cy(), n, deadZone());
        }
        if (selected != lastSelected) {
            if (lastSelected != UNSET && selected >= 0) {
                SumiSounds.tick();
            }
            lastSelected = selected;
        }
        int fast = ctx.duration(SumiTheme.FAST);
        for (int i = 0; i < n; i++) {
            hover[i].animate(i == selected ? 1.0F : 0.0F, now, fast, Easing.CUBIC_OUT);
        }
        // The pointer on the disc rim eases towards the mouse (or the stepped-to wedge).
        boolean pointing = selected >= 0;
        pointerFade.animate(pointing ? 1.0F : 0.0F, now, fast, Easing.CUBIC_OUT);
        if (pointing) {
            double target = manual ? RadialMenu.angleOf(selected, n) : Math.atan2(my - cy(), mx - cx());
            float k = Sumi.reducedMotion() || pointerFade.value(now) < 0.05F ? 1.0F : Math.min(1.0F, ctx.dt() * 20.0F);
            pointer += (float) (RadialMenu.turn(pointer, target) * k);
        }
        super.update(ctx);
    }

    /** 0..1+ open progress of wedge {@code i}: wedges unfold clockwise one after another with a slight overshoot. */
    private float appear(UiContext ctx, int i) {
        if (Sumi.reducedMotion()) {
            return 1.0F;
        }
        float stagger = ctx.theme().ms(SumiTheme.STAGGER) * 0.6F;
        float duration = Math.max(1, ctx.duration(SumiTheme.NORMAL));
        float t = Math.clamp((ctx.now() - openedAt - i * stagger) / duration, 0.0F, 1.0F);
        return Easing.BACK_OUT.apply(t);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void draw(UiContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        SumiTheme theme = ctx.theme();
        long now = ctx.now();
        float cx = cx();
        float cy = cy();
        float r1 = outer();
        float r0 = inner();
        int n = entries.size();
        int ink = theme.color(SumiTheme.INK);

        // A soft ink shadow under the whole ring, and the ring's track.
        Paint.ring(g, cx, cy + 2.0F, r0 - 2.0F, r1 + 3.0F, Paint.fade(ink, 0.22F));
        if (n == 0) {
            Paint.paperArc(g, cx, cy, r0, r1, 0.0F, (float) (Math.PI * 2.0), Paint.fade(theme.color(SumiTheme.PAPER_SHADE), 0.85F));
        }

        float sweep = (float) RadialMenu.sweep(n);
        for (int i = 0; i < n; i++) {
            drawWedge(ctx, g, theme, i, n, sweep, cx, cy, r0, r1, now);
        }

        drawDisc(ctx, g, theme, cx, cy, r0, now);
        drawCaption(ctx, g, theme, cx, cy + r1 + 12.0F);
    }

    private void drawWedge(UiContext ctx, GuiGraphicsExtractor g, SumiTheme theme, int i, int n, float sweep,
                           float cx, float cy, float r0, float r1, long now) {
        Entry entry = entries.get(i);
        float h = hover[i].value(now);
        float p = appear(ctx, i);
        if (p <= 0.001F) {
            return;
        }
        float a = (float) RadialMenu.angleOf(i, n);
        float ca = (float) Math.cos(a);
        float sa = (float) Math.sin(a);
        float wx = cx + ca * POP_PX * h;
        float wy = cy + sa * POP_PX * h;
        float w0 = r0 * (0.55F + 0.45F * p);
        float w1 = r1 * p + POP_PX * 0.5F * h;
        float gap = n > 1 ? GAP_PX / r1 : 0.0F;
        float start = a - sweep / 2.0F + gap;
        float span = Math.max(0.01F, sweep - gap * 2.0F);
        boolean blank = entry.icon().isEmpty();
        float previous = Paint.pushAlpha(Math.min(1.0F, p) * (blank ? BLANK_ALPHA : 1.0F));

        Paint.paperArc(g, wx, wy, w0, w1, start, span, Paint.mix(theme.color(SumiTheme.PAPER), theme.color(SumiTheme.PAPER_SHADE), 0.25F * (1.0F - h)));
        if (h > 0.01F) {
            Paint.inkArc(g, wx, wy, w0, w1, start, span, Paint.fade(theme.color(SumiTheme.INK), h));
        }
        // Rims: a fine paper edge outside, an ink line inside.
        Paint.arc(g, wx, wy, w1 - 1.0F, w1, start, span, Paint.fade(theme.color(SumiTheme.PAPER_EDGE), 0.9F));
        Paint.arc(g, wx, wy, w0, w0 + 1.0F, start, span, Paint.fade(theme.color(SumiTheme.INK_SOFT), 0.8F));

        // The icon at the middle of the wedge, a little bigger when pointed at.
        float rm = (w0 + w1) / 2.0F;
        float ix = wx + ca * rm;
        float iy = wy + sa * rm;
        float scale = (1.0F + 0.3F * h) * Math.min(1.0F, p);
        if (blank) {
            // An empty slot: a small ink ring where the icon would sit.
            Paint.ring(g, ix, iy, 3.0F * scale, 4.0F * scale, Paint.fade(theme.color(h > 0.5F ? SumiTheme.TEXT_ON_INK : SumiTheme.INK_SOFT), 0.8F));
        } else {
            g.pose().pushMatrix();
            g.pose().translate(ix, iy);
            g.pose().scale(scale, scale);
            g.item(entry.icon(), -8, -8);
            g.pose().popMatrix();
        }

        Font font = ctx.font();
        int text = h > 0.5F ? theme.color(SumiTheme.TEXT_ON_INK) : theme.color(SumiTheme.TEXT);
        int count = entry.count().getAsInt();
        if (count > 1) {
            Component label = Component.literal(String.valueOf(count));
            Paint.text(g, font, label, ix + 9.0F - font.width(label), iy + 4.0F, text);
        }
        if (i < 9) {
            float dr = w0 + 5.0F;
            Paint.textScaled(g, font, Component.literal(String.valueOf(i + 1)), wx + ca * dr - 2.0F, wy + sa * dr - 3.0F, 0.75F,
                    Paint.fade(text, 0.55F));
        }
        if (entry.marked().getAsBoolean()) {
            float sealA = a + sweep * 0.28F;
            float sr = w1 - 6.0F;
            Paint.regular(g, wx + (float) Math.cos(sealA) * sr, wy + (float) Math.sin(sealA) * sr, 3.5F, 4, 0.0F,
                    theme.color(SumiTheme.LACQUER));
        }
        Paint.popAlpha(previous);
    }

    private void drawDisc(UiContext ctx, GuiGraphicsExtractor g, SumiTheme theme, float cx, float cy, float r0, long now) {
        float r = r0 - 6.0F;
        Paint.circle(g, cx, cy + 2.0F, r + 1.0F, Paint.fade(theme.color(SumiTheme.INK), 0.25F));
        Paint.paperPolygon(g, Paint.regularPoints(cx, cy, r, Paint.segments(r), 0.0F), theme.color(SumiTheme.PAPER));
        Paint.ring(g, cx, cy, r - 1.0F, r, theme.color(SumiTheme.INK_SOFT));

        // The pointer: an ink notch on the disc rim, facing the selection.
        float f = pointerFade.value(now);
        if (f > 0.01F) {
            float pc = (float) Math.cos(pointer);
            float ps = (float) Math.sin(pointer);
            float tip = r + 5.0F * f;
            float base = r - 3.0F;
            float half = 4.0F;
            float[] tri = {
                    cx + pc * tip, cy + ps * tip,
                    cx + pc * base - ps * half, cy + ps * base + pc * half,
                    cx + pc * base + ps * half, cy + ps * base - pc * half};
            Paint.polygon(g, tri, Paint.fade(theme.color(SumiTheme.INK), f));
        }

        List<Component> lines = centre.apply(selected);
        if (lines.isEmpty()) {
            return;
        }
        Font font = ctx.font();
        int wrap = Math.max(24, (int) (r * 1.6F));
        int total = 0;
        for (int i = 0; i < lines.size(); i++) {
            total += Paint.wrappedHeight(font, lines.get(i), wrap) + (i == 0 ? 2 : 0);
        }
        float ty = cy - total / 2.0F;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            int color = theme.color(i == 0 ? SumiTheme.TEXT : SumiTheme.TEXT_MUTED);
            for (var part : font.split(line, wrap)) {
                Paint.text(g, font, part, cx - font.width(part) / 2.0F, ty, color);
                ty += font.lineHeight;
            }
            ty += i == 0 ? 2 : 0;
        }
    }

    private void drawCaption(UiContext ctx, GuiGraphicsExtractor g, SumiTheme theme, float cx, float top) {
        List<Component> lines = caption.apply(selected);
        if (lines.isEmpty()) {
            return;
        }
        Font font = ctx.font();
        int pad = theme.metric(SumiTheme.PADDING) - 2;
        int w = 0;
        for (Component line : lines) {
            w = Math.max(w, font.width(line));
        }
        w = Math.min(w, width - 20);
        int h = 0;
        for (Component line : lines) {
            h += Paint.wrappedHeight(font, line, w);
        }
        float x0 = cx - w / 2.0F - pad;
        Paint.panel(g, x0, top, w + pad * 2, h + pad * 2 - 1, theme.color(SumiTheme.PAPER));
        float ty = top + pad;
        for (int i = 0; i < lines.size(); i++) {
            int color = theme.color(i == 0 ? SumiTheme.TEXT : SumiTheme.TEXT_MUTED);
            ty += Paint.wrapped(g, font, lines.get(i), x0 + pad, ty, w, color);
        }
    }

    // ------------------------------------------------------------------ input

    private void stepTo(UiContext ctx, int index) {
        selected = index;
        manual = true;
        manualX = ctx.mouseX();
        manualY = ctx.mouseY();
    }

    private void choose(int index) {
        if (index >= 0 && index < entries.size()) {
            onChoose.accept(index);
        }
    }

    @Override
    public boolean mouseDown(UiContext ctx, double mx, double my, int button) {
        choose(selected);
        return true;
    }

    @Override
    public boolean scroll(UiContext ctx, double mx, double my, double scrollX, double scrollY) {
        if (scrollY == 0.0 || entries.isEmpty()) {
            return false;
        }
        stepTo(ctx, RadialMenu.step(selected, entries.size(), scrollY < 0 ? 1 : -1));
        return true;
    }

    @Override
    public boolean key(UiContext ctx, KeyEvent event) {
        int key = event.key();
        int n = entries.size();
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            int index = RadialMenu.numberKey(key - GLFW.GLFW_KEY_0, n);
            if (index >= 0) {
                stepTo(ctx, index);
                choose(index);
            }
            return index >= 0;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_UP) {
            if (n > 0) {
                stepTo(ctx, RadialMenu.step(selected, n, key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_DOWN ? 1 : -1));
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            choose(selected);
            return true;
        }
        return false;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
