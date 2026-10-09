package com.mcspacewizard.emergentstealth.client.tool;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.tool.SpyglassTagging;
import com.mcspacewizard.emergentstealth.tool.SpyglassTags;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * While scoping: a small progress ring under the crosshair as the focus builds up to a tag, and the tag count.
 * Drawn from the synced {@link SpyglassTags} attachment (focus start is a game time, so it animates locally).
 */
public class SpyglassTagHud implements GuiLayer {
    public static final Identifier LAYER_ID = EmergentStealth.id("spyglass_tags");
    private static final int RADIUS = 9;
    private static final int SEGMENTS = 24;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !minecraft.player.isScoping()) {
            return;
        }
        SpyglassTags tags = minecraft.player.getExistingDataOrNull(ESAttachments.SPYGLASS_TAGS);
        if (tags == null) {
            return;
        }
        long now = minecraft.level.getGameTime();
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2 + 22;
        int count = tags.active(now).size();
        if (tags.focusEntity() >= 0) {
            boolean tagged = tags.isTagged(tags.focusEntity(), now);
            float progress = tagged ? 1.0F
                    : Mth.clamp((now - tags.focusStart() + deltaTracker.getGameTimeDeltaPartialTick(false)) / SpyglassTagging.FOCUS_TICKS, 0.0F, 1.0F);
            int filled = Math.round(progress * SEGMENTS);
            for (int i = 0; i < SEGMENTS; i++) {
                double rad = Math.toRadians(i * 360.0 / SEGMENTS);
                int x = cx + (int) Math.round(Math.sin(rad) * RADIUS);
                int y = cy - (int) Math.round(Math.cos(rad) * RADIUS);
                int color = i < filled ? (tagged ? ToolkitBClient.TAG_OUTLINE : 0xFFF2EEE6) : 0x80403A48;
                graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
            }
            if (tagged) {
                graphics.centeredText(minecraft.font, Component.translatable("hud.emergentstealth.spyglass.tagged"), cx, cy + RADIUS + 4, ToolkitBClient.TAG_OUTLINE);
            }
        }
        Component counter = Component.translatable("hud.emergentstealth.spyglass.count", count, SpyglassTagging.maxTags(minecraft.player));
        graphics.centeredText(minecraft.font, counter, cx, graphics.guiHeight() - 40, 0xFFE8DCC0);
    }
}
