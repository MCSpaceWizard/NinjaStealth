package com.mcspacewizard.emergentstealth.client.ui.widget;

import java.util.function.Supplier;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Text (design doc 31 §2 Paint): one line or wrapped, in a theme colour, optionally scaled for titles. */
public class UiLabel extends UiNode {
    public enum Align { LEFT, CENTER, RIGHT }

    private final Supplier<Component> text;
    private String colorToken = SumiTheme.TEXT;
    private float scale = 1.0F;
    private boolean wrap;
    private Align align = Align.LEFT;

    public UiLabel(Supplier<Component> text) {
        this.text = text;
    }

    public UiLabel(Component text) {
        this(() -> text);
    }

    public UiLabel color(String token) {
        this.colorToken = token;
        return this;
    }

    public UiLabel scale(float scale) {
        this.scale = scale;
        return this;
    }

    public UiLabel wrap() {
        this.wrap = true;
        return this;
    }

    public UiLabel align(Align align) {
        this.align = align;
        return this;
    }

    @Override
    public int preferredWidth() {
        return prefWidth >= 0 ? prefWidth : Math.round(Minecraft.getInstance().font.width(text.get()) * scale);
    }

    @Override
    public int preferredHeight(int forWidth) {
        if (prefHeight >= 0) {
            return prefHeight;
        }
        var font = Minecraft.getInstance().font;
        if (wrap) {
            return Math.round(Paint.wrappedHeight(font, text.get(), Math.round(forWidth / scale)) * scale);
        }
        return Math.round((font.lineHeight + 1) * scale);
    }

    @Override
    protected void draw(UiContext ctx) {
        Component value = text.get();
        int color = ctx.theme().color(colorToken);
        var font = ctx.font();
        if (wrap) {
            ctx.graphics().pose().pushMatrix();
            ctx.graphics().pose().translate(x, y);
            ctx.graphics().pose().scale(scale, scale);
            Paint.wrapped(ctx.graphics(), font, value, 0, 0, Math.round(width / scale), color);
            ctx.graphics().pose().popMatrix();
            return;
        }
        if (width > 0 && font.width(value) * scale > width) {
            // Too long for one line: cut it with an ellipsis (the full text belongs in a tooltip).
            String cut = font.plainSubstrByWidth(value.getString(), Math.max(0, Math.round(width / scale) - font.width("...")));
            value = Component.literal(cut + "...").withStyle(value.getStyle());
        }
        float w = font.width(value) * scale;
        float tx = switch (align) {
            case LEFT -> x;
            case CENTER -> x + (width - w) / 2.0F;
            case RIGHT -> x + width - w;
        };
        float ty = y + (height - font.lineHeight * scale) / 2.0F + scale * 0.5F;
        Paint.textScaled(ctx.graphics(), font, value, tx, ty, scale, color);
    }
}
