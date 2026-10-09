package com.mcspacewizard.emergentstealth.client.tool;

import java.util.ArrayList;
import java.util.List;

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
import com.mcspacewizard.emergentstealth.tool.Toolbelt;
import com.mcspacewizard.emergentstealth.tool.Toolkit;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The tool wheel on Sumi (design doc 21 §1, doc 34 §1 and §3): opened while the wheel key (R) is held. The eight
 * slots of your {@link Toolbelt} sit in paper wedges round a disc, in fixed places, with their counts; the active
 * tool carries a seal. Point at one and release the key (or click, or press its number) to make it your active
 * tool; release in the middle, or press Esc, to keep the current one. The choice is only an intent: the server
 * checks it and syncs the active tool back.
 */
public class ToolWheelScreen extends UiScreen {
    private final KeyMapping key;
    private boolean hasBelt;
    private UiRadial radial;
    private boolean done;

    public ToolWheelScreen(KeyMapping key) {
        super(Component.translatable("gui.emergentstealth.tool_wheel"));
        this.key = key;
    }

    @Override
    protected UiNode build() {
        hasBelt = minecraft != null && minecraft.player != null && Toolbelt.find(minecraft.player.getInventory()) >= 0;
        List<UiRadial.Entry> entries = new ArrayList<>();
        if (hasBelt) {
            for (int i = 0; i < Toolbelt.SIZE; i++) {
                int slot = i;
                entries.add(new UiRadial.Entry(slotStack(slot), () -> slotStack(slot).getCount(),
                        () -> !slotStack(slot).isEmpty() && active().item() == slotStack(slot).getItem()));
            }
        }
        radial = new UiRadial(entries)
                .centre(this::centreLines)
                .caption(this::captionLines)
                .onChoose(this::choose);
        setFocus(radial);
        return radial;
    }

    /** What's in belt slot {@code slot} right now (read live, so counts follow throws). */
    private ItemStack slotStack(int slot) {
        if (minecraft == null || minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        return Toolbelt.contents(Toolbelt.belt(minecraft.player.getInventory())).get(slot);
    }

    private int count(Item item) {
        return minecraft == null || minecraft.player == null ? 0 : Toolkit.count(minecraft.player.getInventory(), item);
    }

    private ActiveTool active() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getData(ESAttachments.ACTIVE_TOOL) : ActiveTool.NONE;
    }

    /** The centre disc: the pointed-at slot, else the active tool, else a prompt. */
    private List<Component> centreLines(int index) {
        if (!hasBelt) {
            return List.of(Component.translatable("gui.emergentstealth.tool_wheel.no_belt"));
        }
        if (index >= 0 && slotStack(index).isEmpty()) {
            return List.of(Component.translatable("gui.emergentstealth.tool_wheel.empty_slot"));
        }
        Item item = index >= 0 ? slotStack(index).getItem() : active().item();
        if (index < 0 && (active().isNone() || count(item) <= 0)) {
            return List.of(Component.translatable("gui.emergentstealth.tool_wheel.hint"));
        }
        ItemStack stack = new ItemStack(item);
        List<Component> lines = new ArrayList<>();
        lines.add(stack.getHoverName());
        lines.add(Component.translatable(index >= 0 ? "gui.emergentstealth.tool_wheel.count" : "gui.emergentstealth.tool_wheel.active", count(item)));
        return lines;
    }

    /** Under the ring: how the pointed-at tool works, and how to choose (or how to get a belt). */
    private List<Component> captionLines(int index) {
        List<Component> lines = new ArrayList<>();
        if (!hasBelt) {
            lines.add(Component.translatable("gui.emergentstealth.tool_wheel.no_belt.hint"));
            return lines;
        }
        if (index >= 0) {
            ItemStack stack = slotStack(index);
            lines.add(stack.isEmpty() ? Component.translatable("gui.emergentstealth.tool_wheel.empty_slot.hint")
                    : Component.translatable(stack.getItem().getDescriptionId() + ".hint"));
        }
        lines.add(Component.translatable("gui.emergentstealth.tool_wheel.release", key.getTranslatedKeyMessage()));
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
        ItemStack chosen = index >= 0 && index < Toolbelt.SIZE ? slotStack(index) : ItemStack.EMPTY;
        if (!chosen.isEmpty() && minecraft != null && minecraft.getConnection() != null
                && minecraft.getConnection().hasChannel(SelectToolPayload.TYPE)) {
            if (active().item() != chosen.getItem()) {
                SumiSounds.stamp();
            }
            ClientPacketDistributor.sendToServer(new SelectToolPayload(new ActiveTool(chosen.getItem())));
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
