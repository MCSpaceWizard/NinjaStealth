package com.mcspacewizard.emergentstealth.client.ui.widget;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.ui.Easing;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.Tween;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A progress bar (design doc 31 §2 Widgets): a paper groove that fills with brushed ink, easing to new
 * values. A fresh gain flashes briefly.
 */
public class UiProgressBar extends UiNode {
    private final DoubleSupplier value;
    private final IntSupplier color;
    private final Tween shown;
    private final Tween flash = new Tween(0.0F);

    public UiProgressBar(DoubleSupplier value, IntSupplier color) {
        this.value = value;
        this.color = color;
        this.shown = new Tween((float) value.getAsDouble());
    }

    @Override
    public int preferredHeight(int forWidth) {
        return prefHeight >= 0 ? prefHeight : 6;
    }

    @Override
    public int preferredWidth() {
        return prefWidth >= 0 ? prefWidth : 80;
    }

    @Override
    public void update(UiContext ctx) {
        float target = (float) Math.clamp(value.getAsDouble(), 0.0, 1.0);
        if (target > shown.target() + 0.001F) {
            flash.restart(1.0F, 0.0F, ctx.now(), ctx.duration(SumiTheme.SLOW) * 2, Easing.QUAD_OUT);
        }
        shown.animate(target, ctx.now(), ctx.duration(SumiTheme.SLOW), Easing.CUBIC_OUT);
        super.update(ctx);
    }

    @Override
    protected void draw(UiContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        SumiTheme theme = ctx.theme();
        float[] groove = Paint.cutRectPoints(x, y, width, height, 2);
        Paint.paperPolygon(g, groove, theme.color(SumiTheme.PAPER_SHADE));
        float t = shown.value(ctx.now());
        if (t > 0.002F) {
            float w = Math.max(2, (width - 2) * t);
            Paint.inkPolygon(g, Paint.cutRectPoints(x + 1, y + 1, w, height - 2, 1.5F), color.getAsInt());
            float f = flash.value(ctx.now());
            if (f > 0.01F) {
                Paint.glow(g, x + 1 + w, y + height / 2.0F, height * 2.2F, Paint.fade(theme.color(SumiTheme.GOLD), 0.7F * f),
                        Paint.fade(theme.color(SumiTheme.GOLD), 0.0F));
            }
        }
        Paint.outline(g, groove, 1.0F, Paint.fade(theme.color(SumiTheme.INK_SOFT), 0.6F));
    }
}
