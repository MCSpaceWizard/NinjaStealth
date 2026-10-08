package com.mcspacewizard.emergentstealth.client.tool;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.tool.ActiveTool;
import com.mcspacewizard.emergentstealth.tool.Toolkit;

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
    private static final int GAP = 3;

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
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x60000000);
        ItemStack stack = new ItemStack(tool.item());
        graphics.item(stack, x, y);
        if (count == 0) {
            graphics.fill(x, y, x + 16, y + 16, 0xA0303030);
        }
        graphics.itemDecorations(minecraft.font, stack, x, y, count == 1 ? "" : String.valueOf(count));
    }
}
