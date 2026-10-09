package com.mcspacewizard.emergentstealth.client.ui.widget;

import java.util.function.BooleanSupplier;
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
 * A selectable row in a list (design doc 31 §2 Widgets): a label, an optional muted detail on the right, an ink
 * wash on hover and a lacquer bar while selected. Click or Enter selects; double-click (or Enter on the
 * selected row) activates.
 */
public class UiListRow extends UiNode {
    private final Supplier<Component> label;
    private Supplier<Component> detail = Component::empty;
    private final BooleanSupplier selected;
    private final Runnable onSelect;
    private Runnable onActivate = () -> {};
    private long lastClick;
    private final Tween hover = new Tween(0.0F);
    private final Tween mark = new Tween(0.0F);

    public UiListRow(Supplier<Component> label, BooleanSupplier selected, Runnable onSelect) {
        this.label = label;
        this.selected = selected;
        this.onSelect = onSelect;
    }

    public UiListRow detail(Supplier<Component> detail) {
        this.detail = detail;
        return this;
    }

    public UiListRow onActivate(Runnable onActivate) {
        this.onActivate = onActivate;
        return this;
    }

    @Override
    public int preferredHeight(int forWidth) {
        return prefHeight >= 0 ? prefHeight : 14;
    }

    @Override
    public void update(UiContext ctx) {
        hover.animate(ctx.isHovered(this) || ctx.showFocus(this) ? 1.0F : 0.0F, ctx.now(), ctx.duration(SumiTheme.FAST), Easing.CUBIC_OUT);
        mark.animate(selected.getAsBoolean() ? 1.0F : 0.0F, ctx.now(), ctx.duration(SumiTheme.NORMAL), Easing.BACK_OUT);
        super.update(ctx);
    }

    @Override
    protected void draw(UiContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        SumiTheme theme = ctx.theme();
        float h = hover.value(ctx.now());
        float m = Math.clamp(mark.value(ctx.now()), 0.0F, 1.2F);
        if (h > 0.01F || m > 0.01F) {
            Paint.rect(g, x, y, width, height, Paint.fade(theme.color(SumiTheme.INK), 0.08F * h + 0.12F * Math.min(1.0F, m)));
        }
        if (m > 0.01F) {
            Paint.rect(g, x, y + 1, 2.0F * m, height - 2, theme.color(SumiTheme.LACQUER));
        }
        int ty = y + (height - 8) / 2;
        Component right = detail.get();
        int rightWidth = ctx.font().width(right);
        g.enableScissor(x, y, x + width - rightWidth - 6, y + height);
        Paint.text(g, ctx.font(), label.get(), x + 5 + 2 * m, ty, theme.color(m > 0.5F ? SumiTheme.TEXT : SumiTheme.TEXT_MUTED));
        g.disableScissor();
        if (rightWidth > 0) {
            Paint.text(g, ctx.font(), right, x + width - rightWidth - 3, ty, theme.color(SumiTheme.TEXT_MUTED));
        }
    }

    @Override
    public boolean mouseDown(UiContext ctx, double mx, double my, int button) {
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    @Override
    public void mouseUp(UiContext ctx, double mx, double my, int button, boolean inside) {
        if (!inside) {
            return;
        }
        long now = ctx.now();
        boolean twice = selected.getAsBoolean() && now - lastClick < 350L;
        lastClick = now;
        if (twice) {
            onActivate.run();
        } else {
            SumiSounds.tick();
            onSelect.run();
        }
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public boolean key(UiContext ctx, KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER || event.key() == GLFW.GLFW_KEY_SPACE) {
            if (selected.getAsBoolean()) {
                onActivate.run();
            } else {
                SumiSounds.tick();
                onSelect.run();
            }
            return true;
        }
        return false;
    }
}
