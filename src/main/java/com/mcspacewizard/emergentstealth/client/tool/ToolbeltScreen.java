package com.mcspacewizard.emergentstealth.client.tool;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.tool.Toolbelt;
import com.mcspacewizard.emergentstealth.tool.ToolbeltMenu;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The toolbelt's panel (design doc 34 §3), drawn in Sumi's paper and ink: the eight belt slots in an ink band
 * along the top, numbered like the tool wheel's keys, over the player's inventory.
 */
public class ToolbeltScreen extends AbstractContainerScreen<ToolbeltMenu> {
    public ToolbeltScreen(ToolbeltMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 133);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        SumiTheme theme = Sumi.theme();
        int x = leftPos;
        int y = topPos;
        Paint.panel(graphics, x, y, imageWidth, imageHeight, theme.color(SumiTheme.PAPER));

        // The belt: an ink band with a stitched edge, its slots as paper wells.
        float bandX = x + ToolbeltMenu.BELT_X - 6;
        float bandY = y + ToolbeltMenu.BELT_Y - 4;
        float bandW = Toolbelt.SIZE * 18 + 10;
        Paint.cutRect(graphics, bandX, bandY, bandW, 24, 3, theme.color(SumiTheme.INK_SOFT));
        Paint.dashed(graphics, new float[] {bandX + 3, bandY + 2, bandX + bandW - 3, bandY + 2}, 1.0F, 2.0F, 2.0F,
                Paint.fade(theme.color(SumiTheme.GOLD), 0.7F));
        Paint.dashed(graphics, new float[] {bandX + 3, bandY + 22, bandX + bandW - 3, bandY + 22}, 1.0F, 2.0F, 2.0F,
                Paint.fade(theme.color(SumiTheme.GOLD), 0.7F));

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            float sx = x + slot.x - 1;
            float sy = y + slot.y - 1;
            if (i < Toolbelt.SIZE) {
                Paint.rect(graphics, sx + 1, sy + 1, 16, 16, theme.color(SumiTheme.PAPER_SHADE));
                Paint.rect(graphics, sx + 1, sy + 1, 16, 1, Paint.fade(theme.color(SumiTheme.INK), 0.45F));
            } else {
                boolean locked = slot.getContainerSlot() == menu.beltSlot() && slot.container == playerInventoryContainer();
                Paint.rect(graphics, sx, sy, 18, 18, Paint.fade(theme.color(SumiTheme.PAPER_EDGE), 0.55F));
                Paint.rect(graphics, sx + 1, sy + 1, 16, 16, theme.color(SumiTheme.PAPER_SHADE));
                if (locked) {
                    Paint.rect(graphics, sx + 1, sy + 1, 16, 16, Paint.fade(theme.color(SumiTheme.LACQUER), 0.35F));
                }
            }
        }
    }

    private Object playerInventoryContainer() {
        return minecraft == null || minecraft.player == null ? null : minecraft.player.getInventory();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        SumiTheme theme = Sumi.theme();
        Paint.text(graphics, font, title, titleLabelX, titleLabelY, theme.color(SumiTheme.TEXT));
        Paint.text(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, theme.color(SumiTheme.TEXT_MUTED));
        for (int i = 0; i < Toolbelt.SIZE; i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem()) {
                Paint.textScaled(graphics, font, Component.literal(String.valueOf(i + 1)), slot.x + 6, slot.y + 5, 0.75F,
                        Paint.fade(theme.color(SumiTheme.TEXT_MUTED), 0.7F));
            }
        }
        // Under the panel: what the belt is for.
        Paint.wrapped(graphics, font, Component.translatable("container.emergentstealth.toolbelt.hint"), 0, imageHeight + 6, imageWidth,
                theme.color(SumiTheme.TEXT_ON_INK));
    }
}
