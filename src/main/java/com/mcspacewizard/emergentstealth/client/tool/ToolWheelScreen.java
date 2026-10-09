package com.mcspacewizard.emergentstealth.client.tool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.client.ui.UiScreen;
import com.mcspacewizard.emergentstealth.client.ui.widget.SumiSounds;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiRadial;
import com.mcspacewizard.emergentstealth.network.SelectToolPayload;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.tool.ActiveTool;
import com.mcspacewizard.emergentstealth.tool.Toolkit;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * The tool wheel on Sumi (design doc 21 §1, doc 34 §1): opened while the wheel key (R) is held. The stealth tools in
 * your inventory ({@code #emergentstealth:tools}) sit in paper wedges round a disc, with their counts; the active
 * one carries a seal. Point at one and release the key (or click, or press its number) to make it your active
 * tool; release in the middle, or press Esc, to keep the current one. The choice is only an intent: the server
 * checks it and syncs the active tool back.
 */
public class ToolWheelScreen extends UiScreen {
    private final KeyMapping key;
    private final List<Item> items = new ArrayList<>();
    private UiRadial radial;
    private boolean done;

    public ToolWheelScreen(KeyMapping key) {
        super(Component.translatable("gui.emergentstealth.tool_wheel"));
        this.key = key;
    }

    @Override
    protected UiNode build() {
        refreshItems();
        List<UiRadial.Entry> entries = new ArrayList<>();
        for (Item item : items) {
            entries.add(new UiRadial.Entry(new ItemStack(item), () -> count(item), () -> active().item() == item));
        }
        radial = new UiRadial(entries)
                .centre(this::centreLines)
                .caption(this::captionLines)
                .onChoose(this::choose);
        setFocus(radial);
        return radial;
    }

    /** Stealth tools in the inventory, in registry order (stable from one opening to the next). */
    private void refreshItems() {
        items.clear();
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        Map<Item, Boolean> seen = new LinkedHashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(Toolkit.TOOLS) && (i < Inventory.INVENTORY_SIZE || i == Inventory.SLOT_OFFHAND)) {
                seen.put(stack.getItem(), true);
            }
        }
        items.addAll(seen.keySet());
        items.sort(Comparator.comparingInt(BuiltInRegistries.ITEM::getId));
    }

    private int count(Item item) {
        return minecraft == null || minecraft.player == null ? 0 : Toolkit.count(minecraft.player.getInventory(), item);
    }

    private ActiveTool active() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getData(ESAttachments.ACTIVE_TOOL) : ActiveTool.NONE;
    }

    /** The centre disc: the pointed-at tool, else the active one, else a prompt. */
    private List<Component> centreLines(int index) {
        if (items.isEmpty()) {
            return List.of(Component.translatable("gui.emergentstealth.tool_wheel.empty"));
        }
        Item item = index >= 0 ? items.get(index) : active().item();
        if (index < 0 && (active().isNone() || !items.contains(item))) {
            return List.of(Component.translatable("gui.emergentstealth.tool_wheel.hint"));
        }
        ItemStack stack = new ItemStack(item);
        List<Component> lines = new ArrayList<>();
        lines.add(stack.getHoverName());
        lines.add(Component.translatable(index >= 0 ? "gui.emergentstealth.tool_wheel.count" : "gui.emergentstealth.tool_wheel.active", count(item)));
        return lines;
    }

    /** Under the ring: how the pointed-at tool works, and how to choose. */
    private List<Component> captionLines(int index) {
        List<Component> lines = new ArrayList<>();
        if (index >= 0) {
            lines.add(Component.translatable(items.get(index).getDescriptionId() + ".hint"));
        }
        if (!items.isEmpty()) {
            lines.add(Component.translatable("gui.emergentstealth.tool_wheel.release", key.getTranslatedKeyMessage()));
        }
        return lines;
    }

    private void choose(int index) {
        finish(index);
    }

    /** Sends the selection (if pointing at a tool) and closes. */
    private void finish(int index) {
        if (done) {
            return;
        }
        done = true;
        if (index >= 0 && index < items.size() && minecraft != null && minecraft.getConnection() != null
                && minecraft.getConnection().hasChannel(SelectToolPayload.TYPE)) {
            if (active().item() != items.get(index)) {
                SumiSounds.stamp();
            }
            ClientPacketDistributor.sendToServer(new SelectToolPayload(new ActiveTool(items.get(index))));
        }
        onClose();
    }

    private int pointed() {
        return radial == null ? -1 : radial.selected();
    }

    @Override
    public void tick() {
        // Missed the release event (focus change, key rebinding...): treat as released.
        InputConstants.Key bound = key.getKey();
        if (bound.getType() == InputConstants.Type.KEYSYM && minecraft != null
                && !InputConstants.isKeyDown(minecraft.getWindow(), bound.getValue())) {
            finish(pointed());
        }
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (key.matches(event)) {
            finish(pointed());
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (key.matchesMouse(event)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (key.matchesMouse(event)) {
            finish(pointed());
            return true;
        }
        return super.mouseReleased(event);
    }

    /** No blur and no full backdrop: the world stays visible, with a soft ink vignette round the edges. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float t = openProgress();
        int backdrop = Sumi.theme().color(SumiTheme.BACKDROP);
        float r = (float) Math.hypot(width, height) * 0.62F;
        Paint.glow(graphics, width / 2.0F, height / 2.0F, r, Paint.fade(backdrop, 0.0F), Paint.fade(backdrop, 0.55F * t));
        Paint.glow(graphics, width / 2.0F, height / 2.0F, r * 0.42F, Paint.fade(backdrop, 0.22F * t), Paint.fade(backdrop, 0.0F));
    }
}
