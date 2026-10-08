package com.mcspacewizard.emergentstealth.client.ui;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.ui.SumiTheme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * What a Sumi node sees while updating, rendering or handling input: the graphics (render only), the
 * animation clock, the pointer, and which nodes are hovered and focused.
 */
public final class UiContext {
    private final UiScreen screen;
    private @Nullable GuiGraphicsExtractor graphics;
    long now;
    float dt;
    double mouseX;
    double mouseY;
    @Nullable UiNode hovered;
    boolean keyboardMode;

    UiContext(UiScreen screen) {
        this.screen = screen;
    }

    void setGraphics(@Nullable GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
    }

    public GuiGraphicsExtractor graphics() {
        if (graphics == null) {
            throw new IllegalStateException("No graphics outside rendering");
        }
        return graphics;
    }

    public UiScreen screen() {
        return screen;
    }

    public Font font() {
        return screen.font();
    }

    public SumiTheme theme() {
        return Sumi.theme();
    }

    /** Animation clock (ms). */
    public long now() {
        return now;
    }

    /** Seconds since the previous frame. */
    public float dt() {
        return dt;
    }

    public double mouseX() {
        return mouseX;
    }

    public double mouseY() {
        return mouseY;
    }

    /** The pointer in {@code node}'s own space. */
    public double[] mouseIn(UiNode node) {
        return node.toLocal(mouseX, mouseY);
    }

    public boolean isHovered(UiNode node) {
        return hovered == node;
    }

    /** Hovered, or the hovered node is inside {@code node}. */
    public boolean isHoveredWithin(UiNode node) {
        for (UiNode n = hovered; n != null; n = n.parent()) {
            if (n == node) {
                return true;
            }
        }
        return false;
    }

    /** Focused and the player is navigating with the keyboard, so a focus mark should show. */
    public boolean showFocus(UiNode node) {
        return node.isFocused() && keyboardMode;
    }

    public boolean isPressed(UiNode node) {
        return screen.pressed() == node;
    }

    public int duration(String token) {
        return Sumi.duration(token);
    }
}
