package com.mcspacewizard.emergentstealth.client.tool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import com.mcspacewizard.emergentstealth.network.SelectToolPayload;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.tool.ActiveTool;
import com.mcspacewizard.emergentstealth.tool.Toolkit;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The tool wheel (design doc 21 §1): opened while the wheel key (R) is held. Lists the stealth tools in your
 * inventory ({@code #emergentstealth:tools}) round a circle with their counts. Point at one and release the key
 * (or click) to make it your active tool; release in the middle to keep the current one. The choice is only an
 * intent: the server checks it and syncs the active tool back.
 */
public class ToolWheelScreen extends Screen {
    private static final int SLOT = 24;
    private static final int DEAD_ZONE = 16;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFB8B8B8;

    private record Entry(Item item, int count) {}

    private final KeyMapping key;
    private List<Entry> entries = List.of();
    private int hovered = -1;
    private boolean done;

    public ToolWheelScreen(KeyMapping key) {
        super(Component.translatable("gui.emergentstealth.tool_wheel"));
        this.key = key;
    }

    @Override
    protected void init() {
        refresh();
    }

    /** Stealth tools in the inventory, grouped by item, in registry order (stable from one opening to the next). */
    private void refresh() {
        LocalPlayer player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null) {
            entries = List.of();
            return;
        }
        Inventory inventory = player.getInventory();
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(Toolkit.TOOLS) && (i < Inventory.INVENTORY_SIZE || i == Inventory.SLOT_OFFHAND)) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        List<Entry> list = new ArrayList<>();
        counts.forEach((item, count) -> list.add(new Entry(item, count)));
        list.sort(Comparator.comparingInt(e -> BuiltInRegistries.ITEM.getId(e.item())));
        entries = list;
    }

    private int radius() {
        return Math.max(52, entries.size() * 9);
    }

    @Override
    public void tick() {
        refresh();
        // Missed the release event (focus change, key rebinding...): treat as released.
        InputConstants.Key bound = key.getKey();
        if (bound.getType() == InputConstants.Type.KEYSYM && this.minecraft != null
                && !InputConstants.isKeyDown(this.minecraft.getWindow(), bound.getValue())) {
            finish();
        }
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (key.matches(event)) {
            finish();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (key.matchesMouse(event)) {
            return true;
        }
        finish();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (key.matchesMouse(event)) {
            finish();
            return true;
        }
        return super.mouseReleased(event);
    }

    /** Sends the selection (if pointing at a tool) and closes. */
    private void finish() {
        if (done) {
            return;
        }
        done = true;
        if (hovered >= 0 && hovered < entries.size() && this.minecraft != null && this.minecraft.getConnection() != null
                && this.minecraft.getConnection().hasChannel(SelectToolPayload.TYPE)) {
            ClientPacketDistributor.sendToServer(new SelectToolPayload(new ActiveTool(entries.get(hovered).item())));
        }
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** No blur or dimming: the world stays visible behind the wheel. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int r = radius();
        disc(graphics, cx, cy, r + SLOT / 2 + 8, 0x90101018);
        disc(graphics, cx, cy, DEAD_ZONE + 18, 0x70000000);

        int n = entries.size();
        hovered = hoveredIndex(mouseX - cx, mouseY - cy, n);
        ActiveTool active = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.getData(ESAttachments.ACTIVE_TOOL) : ActiveTool.NONE;
        if (n == 0) {
            graphics.centeredText(this.font, Component.translatable("gui.emergentstealth.tool_wheel.empty"), cx, cy - 4, TEXT_DIM);
            return;
        }
        for (int i = 0; i < n; i++) {
            Entry entry = entries.get(i);
            double angle = angleOf(i, n);
            int x = cx + (int) Math.round(Math.cos(angle) * r) - SLOT / 2;
            int y = cy + (int) Math.round(Math.sin(angle) * r) - SLOT / 2;
            boolean isHovered = i == hovered;
            boolean isActive = entry.item() == active.item();
            graphics.fill(x, y, x + SLOT, y + SLOT, isHovered ? 0xE0E8D8A0 : 0xB0303038);
            if (isActive) {
                graphics.outline(x - 1, y - 1, SLOT + 2, SLOT + 2, 0xFFFFC040);
            }
            ItemStack stack = new ItemStack(entry.item());
            graphics.item(stack, x + 4, y + 4);
            graphics.itemDecorations(this.font, stack, x + 4, y + 4, String.valueOf(entry.count()));
        }
        Entry focus = hovered >= 0 ? entries.get(hovered) : null;
        if (focus != null) {
            ItemStack stack = new ItemStack(focus.item());
            graphics.centeredText(this.font, stack.getHoverName(), cx, cy - 9, TEXT);
            graphics.centeredText(this.font, Component.literal("×" + focus.count()), cx, cy + 2, TEXT_DIM);
        } else {
            graphics.centeredText(this.font, Component.translatable("gui.emergentstealth.tool_wheel.hint"), cx, cy - 4, TEXT_DIM);
        }
        graphics.centeredText(this.font, Component.translatable("gui.emergentstealth.tool_wheel.release", key.getTranslatedKeyMessage()),
                cx, cy + r + SLOT / 2 + 14, TEXT_DIM);
    }

    /** Slot i sits at this angle (radians, screen space): the first at the top, then clockwise. */
    private static double angleOf(int i, int n) {
        return -Math.PI / 2.0 + Math.PI * 2.0 * i / n;
    }

    /** The slot whose direction is closest to the mouse, or -1 inside the dead zone. */
    static int hoveredIndex(double dx, double dy, int n) {
        if (n == 0 || dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
            return -1;
        }
        double angle = Math.atan2(dy, dx) + Math.PI / 2.0;
        double step = Math.PI * 2.0 / n;
        int index = (int) Math.round(angle / step);
        return Math.floorMod(index, n);
    }

    /** A filled circle, one row at a time. */
    private static void disc(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
        for (int y = -r; y <= r; y++) {
            int half = (int) Math.sqrt((double) r * r - (double) y * y);
            graphics.fill(cx - half, cy + y, cx + half, cy + y + 1, color);
        }
    }
}
