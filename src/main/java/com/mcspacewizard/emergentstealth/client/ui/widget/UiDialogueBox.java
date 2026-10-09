package com.mcspacewizard.emergentstealth.client.ui.widget;

import java.util.List;
import java.util.function.IntConsumer;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.ui.Easing;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.Typewriter;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The dialogue box (design doc 31 §2 Dialogue): a portrait slot, the speaker's name on a vermilion seal,
 * typewriter text (click, Space or Enter shows the rest at once), and up to four choices with number-key
 * hints. Lines come from outside ({@link #show}); this widget only presents them and reports choices.
 */
public class UiDialogueBox extends UiNode {
    public static final int MAX_CHOICES = 4;
    private static final int PORTRAIT = 56;

    /** Draws a portrait in a square. */
    @FunctionalInterface
    public interface Portrait {
        void draw(GuiGraphicsExtractor g, float x, float y, float size, long now);
    }

    /** One line: who says what, and the choices offered after it (empty = click to continue). */
    public record Line(Component speaker, Component text, List<Component> choices, @Nullable Portrait portrait) {}

    private @Nullable Line line;
    private String plain = "";
    private long lineStart;
    private boolean revealed;
    private int selected;
    private final IntConsumer onChoice;
    private final Runnable onContinue;
    private List<FormattedCharSequence> wrapped = List.of();

    /**
     * @param onChoice   called with the chosen index (0-based)
     * @param onContinue called when a line without choices is dismissed
     */
    public UiDialogueBox(IntConsumer onChoice, Runnable onContinue) {
        this.onChoice = onChoice;
        this.onContinue = onContinue;
    }

    public void show(Line line, long now) {
        this.line = line;
        this.plain = line.text().getString();
        this.lineStart = now;
        this.revealed = speed() <= 0;
        this.selected = 0;
        this.wrapped = List.of();
    }

    private static float speed() {
        return ESConfig.CLIENT_SPEC.isLoaded() ? ESConfig.UI_TYPEWRITER_SPEED.get() : 45.0F;
    }

    private int textX() {
        return x + Sumi.metric(SumiTheme.PADDING) * 2 + PORTRAIT;
    }

    private int textWidth() {
        return x + width - Sumi.metric(SumiTheme.PADDING) * 2 - textX();
    }

    /** Height this box wants for the current line at {@code forWidth}. */
    @Override
    public int preferredHeight(int forWidth) {
        int pad = Sumi.metric(SumiTheme.PADDING);
        int textW = forWidth - pad * 4 - PORTRAIT;
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int textH = line == null ? 20 : font.split(line.text(), Math.max(20, textW)).size() * (font.lineHeight + 2);
        int choices = line == null ? 0 : Math.min(MAX_CHOICES, line.choices().size());
        int body = 8 + textH + (choices > 0 ? 8 + choices * 15 : 12); // text starts 8 below the padding
        return Math.max(PORTRAIT + pad * 2, body + pad * 2) + 6;
    }

    private boolean typing(long now) {
        return !revealed && Typewriter.visible(plain, now - lineStart, speed()) < plain.length();
    }

    private int visibleChars(long now) {
        return revealed ? plain.length() : Typewriter.visible(plain, now - lineStart, speed());
    }

    private long revealedAt(long now) {
        return revealed && speed() <= 0 ? lineStart : lineStart + Typewriter.durationMs(plain, speed());
    }

    @Override
    protected void draw(UiContext ctx) {
        if (line == null) {
            return;
        }
        GuiGraphicsExtractor g = ctx.graphics();
        SumiTheme theme = ctx.theme();
        long now = ctx.now();
        int pad = theme.metric(SumiTheme.PADDING);
        Font font = ctx.font();
        float top = y + 6;
        float h = height - 6;
        Paint.panel(g, x, top, width, h, theme.color(SumiTheme.PAPER));
        Paint.brushLine(g, x + 10, top + h - 3, x + width - 10, top + h - 3, 2.0F, Paint.fade(theme.color(SumiTheme.INK), 0.5F), 63L);

        // Portrait.
        float px = x + pad;
        float py = top + pad;
        float[] frame = Paint.cutRectPoints(px, py, PORTRAIT, PORTRAIT, 4);
        Paint.inkPolygon(g, frame, theme.color(SumiTheme.INK_SOFT));
        if (line.portrait() != null) {
            g.enableScissor((int) px, (int) py, (int) px + PORTRAIT, (int) py + PORTRAIT);
            line.portrait().draw(g, px, py, PORTRAIT, now);
            g.disableScissor();
        }
        Paint.outline(g, frame, 1.5F, theme.color(SumiTheme.INK));

        // Speaker seal, stamped in when the line starts.
        int slow = Sumi.duration(SumiTheme.NORMAL);
        float stamp = slow <= 0 ? 1.0F : Math.clamp((now - lineStart) / (float) slow, 0.0F, 1.0F);
        float sealScale = 1.0F + 0.35F * (1.0F - Easing.BACK_OUT.apply(stamp));
        int nameW = font.width(line.speaker()) + 14;
        float sx = textX();
        float sy = y - 2;
        g.pose().pushMatrix();
        g.pose().translate(sx + nameW / 2.0F, sy + 7);
        g.pose().scale(sealScale, sealScale);
        g.pose().rotate((float) Math.toRadians(-2));
        float previous = Paint.pushAlpha(Math.min(1.0F, stamp * 2.0F));
        Paint.cutRect(g, -nameW / 2.0F + 1, -6, nameW, 15, 3, Paint.fade(theme.color(SumiTheme.INK), 0.3F));
        Paint.inkPolygon(g, Paint.cutRectPoints(-nameW / 2.0F, -7, nameW, 15, 3), theme.color(SumiTheme.LACQUER));
        Paint.textCentered(g, font, line.speaker(), 0.5F, -3, theme.color(SumiTheme.TEXT_ON_INK));
        Paint.popAlpha(previous);
        g.pose().popMatrix();

        // Typewriter text.
        int tw = textWidth();
        if (wrapped.isEmpty()) {
            wrapped = font.split(line.text(), Math.max(20, tw));
        }
        // While typing, count characters through the wrapped lines (approximate at wraps); once done, show all.
        int remaining = typing(now) ? visibleChars(now) : Integer.MAX_VALUE;
        float ty = top + pad + 8;
        for (FormattedCharSequence seq : wrapped) {
            int len = lengthOf(seq);
            if (remaining <= 0) {
                break;
            }
            FormattedCharSequence shown = remaining >= len ? seq : truncate(seq, remaining);
            Paint.text(g, font, shown, textX(), ty, theme.color(SumiTheme.TEXT));
            remaining -= len + 1;
            ty += font.lineHeight + 2;
        }

        // Choices or the continue mark, once the text is out.
        if (!typing(now)) {
            long since = now - revealedAt(now);
            List<Component> choices = line.choices();
            if (choices.isEmpty()) {
                float bob = Sumi.reducedMotion() ? 0 : (float) Math.sin(now / 200.0) * 1.5F;
                float cx = x + width - pad - 6;
                float cy = top + h - pad - 6 + bob;
                Paint.polygon(g, new float[] {cx - 4, cy - 3, cx + 4, cy - 3, cx, cy + 3}, theme.color(SumiTheme.LACQUER));
            } else {
                float cy = ty + 4;
                double[] mouse = ctx.mouseIn(this);
                for (int i = 0; i < Math.min(MAX_CHOICES, choices.size()); i++) {
                    int stagger = Sumi.duration(SumiTheme.STAGGER);
                    int dur = Sumi.duration(SumiTheme.NORMAL);
                    float t = dur <= 0 ? 1.0F : Easing.CUBIC_OUT.apply(Math.clamp((since - (long) i * stagger * 2) / (float) dur, 0.0F, 1.0F));
                    boolean over = ctx.isHovered(this) && mouse[1] >= cy - 2 && mouse[1] < cy + 13 && mouse[0] >= textX() - 4;
                    if (over) {
                        selected = i;
                    }
                    boolean sel = i == selected;
                    float cx = textX() + (1.0F - t) * 12.0F;
                    float a = Paint.pushAlpha(t);
                    if (sel) {
                        Paint.gradientH(g, cx - 4, cy - 2, tw + 4, 14, Paint.fade(theme.color(SumiTheme.INK), 0.12F),
                                Paint.fade(theme.color(SumiTheme.INK), 0.0F));
                    }
                    int keyColor = sel ? theme.color(SumiTheme.LACQUER) : theme.color(SumiTheme.INK_SOFT);
                    Paint.inkPolygon(g, Paint.cutRectPoints(cx, cy - 1, 11, 11, 2), keyColor);
                    Paint.textCentered(g, font, Component.literal(String.valueOf(i + 1)), cx + 6, cy + 1, theme.color(SumiTheme.TEXT_ON_INK));
                    Paint.text(g, font, choices.get(i), cx + 16, cy + 1, sel ? theme.color(SumiTheme.TEXT) : theme.color(SumiTheme.TEXT_MUTED));
                    if (sel) {
                        Paint.brushLine(g, cx + 16, cy + 11, cx + 16 + font.width(choices.get(i)), cy + 11, 1.5F, theme.color(SumiTheme.LACQUER), 5L + i);
                    }
                    Paint.popAlpha(a);
                    cy += 15;
                }
            }
        }
    }

    private static int lengthOf(FormattedCharSequence seq) {
        int[] n = {0};
        seq.accept((index, style, codepoint) -> {
            n[0] += Character.charCount(codepoint);
            return true;
        });
        return n[0];
    }

    private static FormattedCharSequence truncate(FormattedCharSequence seq, int chars) {
        return sink -> {
            int[] n = {0};
            return seq.accept((index, style, codepoint) -> {
                if (n[0] >= chars) {
                    return false;
                }
                n[0] += Character.charCount(codepoint);
                return sink.accept(index, style, codepoint);
            });
        };
    }

    // ------------------------------------------------------------------ input

    /** Click / Space / Enter: finish the line, then continue or choose. */
    private void advance(UiContext ctx) {
        if (line == null) {
            return;
        }
        if (typing(ctx.now())) {
            revealed = true;
            return;
        }
        if (ctx.now() - revealedAt(ctx.now()) < 120L) {
            return; // don't skip choices by accident
        }
        if (line.choices().isEmpty()) {
            SumiSounds.tick();
            onContinue.run();
        } else {
            choose(selected);
        }
    }

    private void choose(int index) {
        if (line != null && index >= 0 && index < Math.min(MAX_CHOICES, line.choices().size())) {
            SumiSounds.click();
            onChoice.accept(index);
        }
    }

    @Override
    public boolean mouseDown(UiContext ctx, double mx, double my, int button) {
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    @Override
    public void mouseUp(UiContext ctx, double mx, double my, int button, boolean inside) {
        if (inside) {
            advance(ctx);
        }
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public boolean key(UiContext ctx, KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            advance(ctx);
            return true;
        }
        if (line != null && !typing(ctx.now()) && !line.choices().isEmpty()) {
            int count = Math.min(MAX_CHOICES, line.choices().size());
            if (key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + count) {
                choose(key - GLFW.GLFW_KEY_1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_DOWN) {
                selected = (selected + 1) % count;
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP) {
                selected = (selected + count - 1) % count;
                return true;
            }
        }
        return false;
    }
}
