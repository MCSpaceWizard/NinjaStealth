package com.mcspacewizard.emergentstealth.client.tool;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.tool.SpyglassTagging;
import com.mcspacewizard.emergentstealth.tool.SpyglassTags;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;

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
            // A smooth ink arc filling clockwise from the top (Sumi), gold once tagged.
            SumiTheme theme = Sumi.theme();
            Paint.ring(graphics, cx, cy, RADIUS - 1.5F, RADIUS + 1.5F, Paint.fade(theme.color(SumiTheme.INK), 0.45F));
            int fill = tagged ? ToolkitBClient.TAG_OUTLINE : theme.color(SumiTheme.PAPER);
            if (progress > 0.0F) {
                Paint.arc(graphics, cx, cy, RADIUS - 1.0F, RADIUS + 1.0F, (float) (-Math.PI / 2.0), (float) (Math.PI * 2.0 * progress), fill);
            }
            if (tagged) {
                graphics.centeredText(minecraft.font, Component.translatable("hud.emergentstealth.spyglass.tagged"), cx, cy + RADIUS + 4, ToolkitBClient.TAG_OUTLINE);
            }
        }
        Component counter = Component.translatable("hud.emergentstealth.spyglass.count", count, SpyglassTagging.maxTags(minecraft.player));
        graphics.centeredText(minecraft.font, counter, cx, graphics.guiHeight() - 40, 0xFFE8DCC0);
    }
}
