package com.mcspacewizard.emergentstealth.client.tool;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.tool.ActiveTool;
import com.mcspacewizard.emergentstealth.tool.Toolkit;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * The active tool's icon, just right of the light gem (design doc 21 §1), with how many you carry. Greyed out when
 * you have none left. Positioned from the light gem's layout (centred, {@code 58} px above the bottom, 32 px wide).
 */
public final class ActiveToolHud implements GuiLayer {
    public static final Identifier LAYER_ID = EmergentStealth.id("active_tool");

    /** Mirrors LightGemHud's layout: the gem is 32 px wide, centred, 58 px above the bottom edge. */
    private static final int GEM_HALF_WIDTH = 16;
    private static final int BOTTOM_OFFSET = 58;
    private static final int GAP = 7;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) {
            return;
        }
        ActiveTool tool = player.getData(ESAttachments.ACTIVE_TOOL);
        if (tool.isNone()) {
            return;
        }
        int count = Toolkit.count(player.getInventory(), tool.item());
        int x = graphics.guiWidth() / 2 + GEM_HALF_WIDTH + GAP;
        int y = graphics.guiHeight() - BOTTOM_OFFSET;
        // A small paper diamond behind the icon (Sumi), the tool sitting on it.
        SumiTheme theme = Sumi.theme();
        Paint.regular(graphics, x + 8, y + 9, 12.5F, 4, 0.0F, Paint.fade(theme.color(SumiTheme.INK), 0.35F));
        Paint.paperPolygon(graphics, Paint.regularPoints(x + 8, y + 8, 12.0F, 4, 0.0F), Paint.fade(theme.color(SumiTheme.PAPER), 0.92F));
        Paint.regularRing(graphics, x + 8, y + 8, 12.0F, 4, 0.0F, 1.0F, theme.color(SumiTheme.INK_SOFT));
        ItemStack stack = new ItemStack(tool.item());
        graphics.item(stack, x, y);
        // Out of the belt: dimmed, unless it's the hand tool you just drew from it.
        boolean inHand = count == 0 && player.getMainHandItem().is(tool.item());
        if (count == 0 && !inHand) {
            Paint.regular(graphics, x + 8, y + 8, 11.0F, 4, 0.0F, Paint.fade(theme.color(SumiTheme.INK), 0.55F));
        }
        graphics.itemDecorations(minecraft.font, stack, x, y, count == 1 || inHand ? "" : String.valueOf(count));
    }
}
