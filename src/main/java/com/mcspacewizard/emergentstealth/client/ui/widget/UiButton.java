package com.mcspacewizard.emergentstealth.client.ui.widget;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.ui.Easing;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.Tween;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * A button (design doc 31 §2 Widgets) with an ink-stamp hover: ink sweeps across from the left and the
 * label turns to paper where the ink has reached. Pressing squashes it slightly, like a stamp.
 * <ul>
 *   <li>{@link Style#PRIMARY}: a vermilion seal, for the main action;</li>
 *   <li>{@link Style#SECONDARY}: paper with an ink edge;</li>
 *   <li>{@link Style#GHOST}: text only, with a brush underline on hover.</li>
 * </ul>
 */
public class UiButton extends UiNode {
    public enum Style { PRIMARY, SECONDARY, GHOST }

    private final Supplier<Component> label;
    private final Runnable action;
    private final Style style;
    private BooleanSupplier enabled = () -> true;
    private final Tween hover = new Tween(0.0F);
    private final Tween press = new Tween(0.0F);

    public UiButton(Component label, Style style, Runnable action) {
        this(() -> label, style, action);
    }

    public UiButton(Supplier<Component> label, Style style, Runnable action) {
        this.label = label;
        this.style = style;
        this.action = action;
    }

    public UiButton enabledWhen(BooleanSupplier enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public int preferredWidth() {
        return prefWidth >= 0 ? prefWidth : Minecraft.getInstance().font.width(label.get()) + 24;
    }

    @Override
    public int preferredHeight(int forWidth) {
        return prefHeight >= 0 ? prefHeight : 20;
    }

    @Override
    public void update(UiContext ctx) {
        boolean active = enabled.getAsBoolean() && (ctx.isHovered(this) || ctx.showFocus(this));
        hover.animate(active ? 1.0F : 0.0F, ctx.now(), ctx.duration(SumiTheme.NORMAL), Easing.CUBIC_OUT);
        press.animate(ctx.isPressed(this) && ctx.isHovered(this) ? 1.0F : 0.0F, ctx.now(), ctx.duration(SumiTheme.FAST), Easing.QUAD_OUT);
        super.update(ctx);
    }

    @Override
    protected void draw(UiContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        SumiTheme theme = ctx.theme();
        float h = hover.value(ctx.now());
        float p = press.value(ctx.now());
        boolean on = enabled.getAsBoolean();
        float previous = Paint.pushAlpha(on ? 1.0F : 0.45F);
        float s = 1.0F - 0.05F * p;
        float cx = x + width / 2.0F;
        float cy = y + height / 2.0F;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(s, s);
        g.pose().translate(-cx, -cy);

        float cut = theme.metricF(SumiTheme.CUT) - 1;
        float[] shape = Paint.cutRectPoints(x, y, width, height, cut);
        Component text = label.get();
        float tx = cx - ctx.font().width(text) / 2.0F;
        float ty = cy - 4;
        int ink = theme.color(SumiTheme.INK);
        int paper = theme.color(SumiTheme.PAPER);
        switch (style) {
            case PRIMARY -> {
                int lacquer = Paint.mix(theme.color(SumiTheme.LACQUER), theme.color(SumiTheme.LACQUER_DARK), 0.35F * (1.0F - h));
                Paint.cutRect(g, x + 1, y + 2, width, height, cut, Paint.fade(ink, 0.3F));
                Paint.inkPolygon(g, shape, lacquer);
                Paint.outline(g, Paint.cutRectPoints(x + 2, y + 2, width - 4, height - 4, cut - 1), 1.0F, Paint.fade(paper, 0.25F + 0.3F * h));
                Paint.text(g, ctx.font(), text, tx, ty, paper);
            }
            case SECONDARY -> {
                Paint.paperPolygon(g, shape, theme.color(SumiTheme.PAPER_SHADE));
                Paint.outline(g, shape, 1.0F, Paint.fade(theme.color(SumiTheme.INK_SOFT), 0.8F));
                Paint.text(g, ctx.font(), text, tx, ty, theme.color(SumiTheme.TEXT));
                if (h > 0.01F) {
                    int sweep = Math.round(width * h);
                    g.enableScissor(x, y - 2, x + sweep, y + height + 2);
                    Paint.inkPolygon(g, shape, ink);
                    Paint.text(g, ctx.font(), text, tx, ty, theme.color(SumiTheme.TEXT_ON_INK));
                    g.disableScissor();
                }
            }
            case GHOST -> {
                int color = Paint.mix(theme.color(SumiTheme.TEXT_MUTED), theme.color(SumiTheme.TEXT), h);
                Paint.text(g, ctx.font(), text, tx, ty, color);
                if (h > 0.01F) {
                    float w = ctx.font().width(text) * h;
                    Paint.brushLine(g, tx, y + height - 4, tx + w, y + height - 4, 2.0F, theme.color(SumiTheme.LACQUER), 3L);
                }
            }
        }
        if (ctx.showFocus(this)) {
            Paint.brushLine(g, x + 4, y + height + 2, x + width - 4, y + height + 2, 2.0F, theme.color(SumiTheme.LACQUER), 7L);
        }
        g.pose().popMatrix();
        Paint.popAlpha(previous);
    }

    @Override
    public boolean mouseDown(UiContext ctx, double mx, double my, int button) {
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT && enabled.getAsBoolean();
    }

    @Override
    public void mouseUp(UiContext ctx, double mx, double my, int button, boolean inside) {
        if (inside && enabled.getAsBoolean()) {
            activate();
        }
    }

    private void activate() {
        SumiSounds.click();
        action.run();
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public boolean key(UiContext ctx, KeyEvent event) {
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_SPACE || event.key() == GLFW.GLFW_KEY_KP_ENTER)
                && enabled.getAsBoolean()) {
            press.restart(1.0F, 0.0F, ctx.now(), Sumi.duration(SumiTheme.NORMAL), Easing.QUAD_OUT);
            activate();
            return true;
        }
        return false;
    }
}
