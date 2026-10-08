package com.mcspacewizard.emergentstealth.client.ui.widget;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.ui.Easing;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.Tween;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

/** An on/off switch (design doc 31 §2 Widgets): a pill whose ink fills in as the paper knob slides across. */
public class UiToggle extends UiNode {
    private static final int TRACK_W = 28;
    private static final int TRACK_H = 14;
    private final BooleanSupplier get;
    private final Consumer<Boolean> set;
    private final boolean editable;
    private final Tween knob;
    private final Tween hover = new Tween(0.0F);

    public UiToggle(BooleanSupplier get, Consumer<Boolean> set, boolean editable) {
        this.get = get;
        this.set = set;
        this.editable = editable;
        this.knob = new Tween(get.getAsBoolean() ? 1.0F : 0.0F);
    }

    @Override
    public int preferredWidth() {
        return prefWidth >= 0 ? prefWidth : TRACK_W;
    }

    @Override
    public int preferredHeight(int forWidth) {
        return prefHeight >= 0 ? prefHeight : TRACK_H + 4;
    }

    @Override
    public void update(UiContext ctx) {
        knob.animate(get.getAsBoolean() ? 1.0F : 0.0F, ctx.now(), ctx.duration(SumiTheme.NORMAL), Easing.BACK_OUT);
        hover.animate(editable && (ctx.isHovered(this) || ctx.showFocus(this)) ? 1.0F : 0.0F, ctx.now(), ctx.duration(SumiTheme.FAST), Easing.CUBIC_OUT);
        super.update(ctx);
    }

    @Override
    protected void draw(UiContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        SumiTheme theme = ctx.theme();
        float t = knob.value(ctx.now());
        float h = hover.value(ctx.now());
        float previous = Paint.pushAlpha(editable ? 1.0F : 0.5F);
        float tx = x + width - TRACK_W;
        float ty = y + (height - TRACK_H) / 2.0F;
        float r = TRACK_H / 2.0F;
        float[] pill = pill(tx, ty, TRACK_W, TRACK_H);
        Paint.paperPolygon(g, pill, theme.color(SumiTheme.PAPER_SHADE));
        float fill = Math.clamp(t, 0.0F, 1.0F);
        if (fill > 0.01F) {
            g.enableScissor((int) tx, (int) ty - 1, (int) (tx + r + (TRACK_W - r) * fill) + 1, (int) (ty + TRACK_H) + 1);
            Paint.inkPolygon(g, pill, theme.color(SumiTheme.INK));
            g.disableScissor();
        }
        Paint.outline(g, pill, 1.0F, Paint.fade(theme.color(SumiTheme.INK_SOFT), 0.7F + 0.3F * h));
        float kx = tx + r + (TRACK_W - TRACK_H) * t;
        float ky = ty + r;
        Paint.circle(g, kx, ky + 1, r - 1.5F, Paint.fade(theme.color(SumiTheme.INK), 0.3F));
        Paint.circle(g, kx, ky, r - 2.0F + h * 0.5F, theme.color(SumiTheme.PAPER));
        Paint.circle(g, kx, ky, 2.0F * t, theme.color(SumiTheme.LACQUER));
        if (ctx.showFocus(this)) {
            Paint.outline(g, pill(tx - 2, ty - 2, TRACK_W + 4, TRACK_H + 4), 1.0F, theme.color(SumiTheme.LACQUER));
        }
        Paint.popAlpha(previous);
    }

    /** A rounded capsule as a polygon. */
    static float[] pill(float x, float y, float w, float h) {
        float r = h / 2.0F;
        int seg = 10;
        float[] pts = new float[(seg + 1) * 4];
        int k = 0;
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI / 2 + Math.PI * i / seg;
            pts[k++] = x + r + (float) Math.cos(a) * r;
            pts[k++] = y + r - (float) Math.sin(a) * r;
        }
        for (int i = 0; i <= seg; i++) {
            double a = -Math.PI / 2 + Math.PI * i / seg;
            pts[k++] = x + w - r + (float) Math.cos(a) * r;
            pts[k++] = y + r - (float) Math.sin(a) * r;
        }
        return pts;
    }

    @Override
    public boolean mouseDown(UiContext ctx, double mx, double my, int button) {
        return editable && button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    @Override
    public void mouseUp(UiContext ctx, double mx, double my, int button, boolean inside) {
        if (inside) {
            flip();
        }
    }

    private void flip() {
        SumiSounds.tick();
        set.accept(!get.getAsBoolean());
    }

    @Override
    public boolean isFocusable() {
        return editable;
    }

    @Override
    public boolean key(UiContext ctx, KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_SPACE) {
            flip();
            return true;
        }
        return false;
    }
}
