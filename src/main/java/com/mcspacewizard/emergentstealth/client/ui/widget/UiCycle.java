package com.mcspacewizard.emergentstealth.client.ui.widget;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.ui.Easing;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.Tween;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Cycles through a list of values (design doc 31 §2 Widgets): ‹ value ›. Click the left half for the
 * previous value and the right half for the next, or use the arrow keys. The new value slides in.
 */
public class UiCycle<T> extends UiNode {
    private final List<T> values;
    private final Supplier<T> get;
    private final Consumer<T> set;
    private final Function<T, Component> name;
    private final boolean editable;
    private final Tween slide = new Tween(0.0F);
    private final Tween hover = new Tween(0.0F);

    public UiCycle(List<T> values, Supplier<T> get, Consumer<T> set, Function<T, Component> name, boolean editable) {
        this.values = values;
        this.get = get;
        this.set = set;
        this.name = name;
        this.editable = editable;
    }

    @Override
    public int preferredWidth() {
        return prefWidth >= 0 ? prefWidth : 96;
    }

    @Override
    public int preferredHeight(int forWidth) {
        return prefHeight >= 0 ? prefHeight : 16;
    }

    @Override
    public void update(UiContext ctx) {
        hover.animate(editable && (ctx.isHovered(this) || ctx.showFocus(this)) ? 1.0F : 0.0F, ctx.now(), ctx.duration(SumiTheme.FAST),
                Easing.CUBIC_OUT);
        super.update(ctx);
    }

    @Override
    protected void draw(UiContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        SumiTheme theme = ctx.theme();
        float previous = Paint.pushAlpha(editable ? 1.0F : 0.5F);
        float h = hover.value(ctx.now());
        float cy = y + height / 2.0F;
        int chevron = Paint.mix(theme.color(SumiTheme.INK_FAINT), theme.color(SumiTheme.LACQUER), h);
        Paint.polygon(g, new float[] {x + 6, cy - 4, x + 6, cy + 4, x + 1, cy}, chevron);
        Paint.polygon(g, new float[] {x + width - 6, cy - 4, x + width - 1, cy, x + width - 6, cy + 4}, chevron);
        float s = slide.value(ctx.now());
        Component text = name.apply(get.get());
        g.enableScissor(x + 8, y - 2, x + width - 8, y + height + 2);
        float previousSlide = Paint.pushAlpha(1.0F - Math.abs(s) / 12.0F);
        Paint.textCentered(g, ctx.font(), text, x + width / 2.0F + s, cy - 4, theme.color(SumiTheme.TEXT));
        Paint.popAlpha(previousSlide);
        g.disableScissor();
        Paint.line(g, x + 10, y + height - 1, x + width - 10, y + height - 1, 1.0F,
                Paint.fade(theme.color(SumiTheme.INK_FAINT), 0.35F + 0.4F * h));
        if (ctx.showFocus(this)) {
            Paint.brushLine(g, x + 8, y + height + 1, x + width - 8, y + height + 1, 1.5F, theme.color(SumiTheme.LACQUER), 21L);
        }
        Paint.popAlpha(previous);
    }

    private void step(UiContext ctx, int dir) {
        int at = Math.max(0, values.indexOf(get.get()));
        T next = values.get(Math.floorMod(at + dir, values.size()));
        if (!Objects.equals(next, get.get())) {
            set.accept(next);
            slide.restart(dir * 12.0F, 0.0F, ctx.now(), ctx.duration(SumiTheme.NORMAL), Easing.CUBIC_OUT);
            SumiSounds.tick();
        }
    }

    @Override
    public boolean mouseDown(UiContext ctx, double mx, double my, int button) {
        return editable && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !values.isEmpty();
    }

    @Override
    public void mouseUp(UiContext ctx, double mx, double my, int button, boolean inside) {
        if (inside) {
            step(ctx, mx < x + width / 2.0 ? -1 : 1);
        }
    }

    @Override
    public boolean isFocusable() {
        return editable;
    }

    @Override
    public boolean key(UiContext ctx, KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_LEFT) {
            step(ctx, -1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_RIGHT || event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_SPACE) {
            step(ctx, 1);
            return true;
        }
        return false;
    }
}
