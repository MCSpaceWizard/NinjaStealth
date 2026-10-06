package com.mcspacewizard.emergentstealth.client.hud;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Builds the above-head detection indicator (D-04): a "?" with a filling meter while an NPC grows
 * suspicious of you, an orange "?" while it searches for you, a red "!" once you're detected.
 * Text-based for now; a sprite indicator replaces it in the HUD polish pass.
 */
public final class DetectionIndicator {
    private DetectionIndicator() {}

    private static final int CELLS = 5;
    private static final int COLOR_LOW = 0xFFFFFF;
    private static final int COLOR_MID = 0xFFE14A;
    private static final int COLOR_HIGH = 0xFF8A1E;
    private static final int COLOR_EMPTY = 0x555555;

    public static @Nullable Component build(ClientDetection.@Nullable View view) {
        if (view == null) {
            return null;
        }
        if (view.detected()) {
            return Component.literal("!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        }
        if (view.searching()) {
            return Component.literal("?").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        }
        float a = view.awareness();
        if (a <= 0.01F) {
            return null;
        }
        int color = a < 0.5F
                ? ARGB.srgbLerp(a * 2.0F, COLOR_LOW, COLOR_MID)
                : ARGB.srgbLerp((a - 0.5F) * 2.0F, COLOR_MID, COLOR_HIGH);
        MutableComponent text = Component.literal("? ").withColor(color).withStyle(ChatFormatting.BOLD);
        int filled = Mth.clamp(Math.round(a * CELLS), 0, CELLS);
        text.append(Component.literal("■".repeat(filled)).withColor(color));
        text.append(Component.literal("■".repeat(CELLS - filled)).withColor(COLOR_EMPTY));
        return text;
    }
}
