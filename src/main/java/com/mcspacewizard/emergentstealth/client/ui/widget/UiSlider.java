package com.mcspacewizard.emergentstealth.client.ui.widget;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.ui.ConfigMapping;
import com.mcspacewizard.emergentstealth.ui.Easing;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.Tween;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * A slider (design doc 31 §2 Widgets): a hairline track, a brush stroke of ink up to the value, and a
 * vermilion diamond knob. Drag or click to set; arrow keys step (Shift for ten steps). The value is shown
 * to the right.
 */
public class UiSlider extends UiNode {
    private static final int VALUE_W = 30;
    private final DoubleSupplier get;
    private final DoubleConsumer set;
    private final double min;
    private final double max;
    private final boolean integer;
    private final DoubleFunction<String> format;
    private final boolean editable;
    private final Tween shown;
    private final Tween hover = new Tween(0.0F);
    private boolean dragging;

    public UiSlider(DoubleSupplier get, DoubleConsumer set, double min, double max, boolean integer, DoubleFunction<String> format,
                    boolean editable) {
        this.get = get;
        this.set = set;
        this.min = min;
        this.max = max;
        this.integer = integer;
        this.format = format;
        this.editable = editable;
        this.shown = new Tween((float) ConfigMapping.fraction(get.getAsDouble(), min, max));
    }

    @Override
    public int preferredWidth() {
        return prefWidth >= 0 ? prefWidth : 96;
    }

    @Override
    public int preferredHeight(int forWidth) {
        return prefHeight >= 0 ? prefHeight : 16;
    }

    private float trackStart() {
        return x + 5;
    }

    private float trackEnd() {
        return x + width - VALUE_W - 5;
    }

    @Override
    public void update(UiContext ctx) {
        float target = (float) ConfigMapping.fraction(get.getAsDouble(), min, max);
        if (dragging) {
            shown.snap(target);
        } else {
            shown.animate(target, ctx.now(), ctx.duration(SumiTheme.FAST), Easing.CUBIC_OUT);
        }
        hover.animate(editable && (ctx.isHovered(this) || ctx.showFocus(this) || dragging) ? 1.0F : 0.0F, ctx.now(),
                ctx.duration(SumiTheme.FAST), Easing.CUBIC_OUT);
        super.update(ctx);
    }

    @Override
    protected void draw(UiContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        SumiTheme theme = ctx.theme();
        float previous = Paint.pushAlpha(editable ? 1.0F : 0.5F);
        float t = shown.value(ctx.now());
        float h = hover.value(ctx.now());
        float cy = y + height / 2.0F;
        float x0 = trackStart();
        float x1 = trackEnd();
        float kx = x0 + (x1 - x0) * t;
        Paint.line(g, x0, cy, x1, cy, 1.0F, Paint.fade(theme.color(SumiTheme.INK_FAINT), 0.8F));
        for (int i = 0; i <= 4; i++) {
            float tickX = x0 + (x1 - x0) * i / 4.0F;
            Paint.line(g, tickX, cy - 2, tickX, cy + 2, 1.0F, Paint.fade(theme.color(SumiTheme.INK_FAINT), 0.5F));
        }
        if (kx - x0 > 1.5F) {
            Paint.brushLine(g, x0 - 1, cy, kx, cy, 4.0F, theme.color(SumiTheme.INK), 17L);
        }
        float r = 4.5F + h * 1.0F;
        Paint.regular(g, kx, cy + 1, r, 4, (float) (Math.PI / 4) * 0 + (float) Math.PI / 2, Paint.fade(theme.color(SumiTheme.INK), 0.35F));
        Paint.regular(g, kx, cy, r, 4, (float) Math.PI / 2, theme.color(SumiTheme.LACQUER));
        Paint.regular(g, kx, cy, r * 0.4F, 4, (float) Math.PI / 2, theme.color(SumiTheme.PAPER));
        String text = format.apply(get.getAsDouble());
        Component value = Component.literal(text);
        Paint.text(g, ctx.font(), value, x + width - ctx.font().width(value), cy - 4, theme.color(SumiTheme.TEXT));
        if (ctx.showFocus(this)) {
            Paint.brushLine(g, x0, y + height + 1, x1, y + height + 1, 1.5F, theme.color(SumiTheme.LACQUER), 9L);
        }
        Paint.popAlpha(previous);
    }

    private void setFromMouse(double mx) {
        double t = (mx - trackStart()) / Math.max(1.0, trackEnd() - trackStart());
        double value = ConfigMapping.valueAt(t, min, max, integer);
        if (value != get.getAsDouble()) {
            set.accept(value);
        }
    }

    @Override
    public boolean mouseDown(UiContext ctx, double mx, double my, int button) {
        if (!editable || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        setFromMouse(mx);
        return true;
    }

    @Override
    public boolean claimDrag(UiContext ctx, Gesture gesture) {
        if (!editable) {
            return false;
        }
        dragging = true;
        return true;
    }

    @Override
    public void drag(UiContext ctx, double mx, double my, double dx, double dy) {
        setFromMouse(mx);
    }

    @Override
    public void dragEnd(UiContext ctx) {
        dragging = false;
    }

    @Override
    public boolean isFocusable() {
        return editable;
    }

    @Override
    public boolean key(UiContext ctx, KeyEvent event) {
        int dir = event.key() == GLFW.GLFW_KEY_RIGHT ? 1 : event.key() == GLFW.GLFW_KEY_LEFT ? -1 : 0;
        if (dir == 0) {
            return false;
        }
        double step = integer ? 1.0 : ConfigMapping.step(min, max);
        if (event.hasShiftDown()) {
            step *= 10;
        }
        double value = Math.clamp(get.getAsDouble() + dir * step, min, max);
        value = integer ? Math.round(value) : Math.round(value / ConfigMapping.step(min, max)) * ConfigMapping.step(min, max);
        set.accept(value);
        return true;
    }
}
