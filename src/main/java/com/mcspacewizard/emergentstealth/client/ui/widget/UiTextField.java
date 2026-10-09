package com.mcspacewizard.emergentstealth.client.ui.widget;

import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.ui.Easing;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.Tween;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * A one-line text field (design doc 31 §2 Widgets): text on a paper strip over an ink underline that thickens in
 * lacquer while focused, with a blinking brush caret. Typing appends, Backspace deletes (Ctrl: the whole field),
 * Enter submits. Shows a muted placeholder while empty.
 */
public class UiTextField extends UiNode {
    private final Component placeholder;
    private String text = "";
    private int maxLength = 128;
    private Consumer<String> onChange = s -> {};
    private Consumer<String> onSubmit = s -> {};
    private final Tween focus = new Tween(0.0F);

    public UiTextField(Component placeholder) {
        this.placeholder = placeholder;
    }

    public UiTextField onChange(Consumer<String> onChange) {
        this.onChange = onChange;
        return this;
    }

    public UiTextField onSubmit(Consumer<String> onSubmit) {
        this.onSubmit = onSubmit;
        return this;
    }

    public UiTextField maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public String text() {
        return text;
    }

    public void setText(String value) {
        String clipped = value.length() > maxLength ? value.substring(0, maxLength) : value;
        if (!clipped.equals(text)) {
            text = clipped;
            onChange.accept(text);
        }
    }

    @Override
    public int preferredHeight(int forWidth) {
        return prefHeight >= 0 ? prefHeight : 18;
    }

    @Override
    public void update(UiContext ctx) {
        focus.animate(isFocused() ? 1.0F : 0.0F, ctx.now(), ctx.duration(SumiTheme.FAST), Easing.CUBIC_OUT);
        super.update(ctx);
    }

    @Override
    protected void draw(UiContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        SumiTheme theme = ctx.theme();
        float f = focus.value(ctx.now());
        Paint.paperPolygon(g, Paint.cutRectPoints(x, y, width, height, 3.0F), theme.color(SumiTheme.PAPER_SHADE));
        int line = Paint.mix(theme.color(SumiTheme.INK_SOFT), theme.color(SumiTheme.LACQUER), f);
        Paint.brushLine(g, x + 2, y + height - 2, x + width - 2, y + height - 2, 1.5F + f, line, 11L);
        int tx = x + 5;
        int ty = y + (height - 8) / 2;
        g.enableScissor(x + 2, y, x + width - 2, y + height);
        if (text.isEmpty()) {
            Paint.text(g, ctx.font(), placeholder, tx, ty, theme.color(SumiTheme.TEXT_MUTED));
        } else {
            // Keep the end of long text in view.
            int textWidth = ctx.font().width(text);
            int shift = Math.max(0, textWidth - (width - 14));
            Paint.text(g, ctx.font(), Component.literal(text), tx - shift, ty, theme.color(SumiTheme.TEXT));
            tx += textWidth - shift;
        }
        if (isFocused() && (ctx.now() / 500L) % 2L == 0L) {
            Paint.rect(g, tx + 1, ty - 1, 1, 10, theme.color(SumiTheme.LACQUER));
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseDown(UiContext ctx, double mx, double my, int button) {
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public boolean character(UiContext ctx, CharacterEvent event) {
        if (!event.isAllowedChatCharacter()) {
            return false;
        }
        setText(text + event.codepointAsString());
        return true;
    }

    @Override
    public boolean key(UiContext ctx, KeyEvent event) {
        switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!text.isEmpty()) {
                    setText(event.hasControlDown() ? "" : text.substring(0, text.offsetByCodePoints(text.length(), -1)));
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                onSubmit.accept(text);
                return true;
            }
            default -> {
                // Letters arrive as characters; swallow their key presses so screen shortcuts don't fire while typing.
                return event.key() >= GLFW.GLFW_KEY_SPACE && event.key() <= GLFW.GLFW_KEY_GRAVE_ACCENT && !event.hasControlDown();
            }
        }
    }
}
