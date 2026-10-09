package com.mcspacewizard.emergentstealth.client.authoring;

import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.authoring.StructurePayloads;
import com.mcspacewizard.emergentstealth.registry.ESItems;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Mirror;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterDebugRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Client side of the authoring tools (design doc 32): the Surveyor's Plan opens the structure browser, and while
 * a ghost preview is active, the scroll wheel rotates it, M mirrors, Page Up/Down raise or lower it, use places
 * it and attack cancels.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID, value = Dist.CLIENT)
public final class AuthoringClientEvents {
    private AuthoringClientEvents() {}

    private static final Identifier HINT_LAYER = EmergentStealth.id("structure_preview_hint");

    // --- Mod bus ---

    @SubscribeEvent
    static void onRegisterClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(StructurePayloads.StructureList.TYPE, ClientStructures::handleList);
        event.register(StructurePayloads.PreviewPart.TYPE, ClientStructures::handlePart);
    }

    @SubscribeEvent
    static void onRegisterDebugRenderers(RegisterDebugRenderersEvent event) {
        event.register(new GhostPreview());
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, HINT_LAYER, new HintLayer());
    }

    // --- Game bus ---

    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (GhostPreview.isActive()) {
            if (event.isUseItem()) {
                GhostPreview.place();
            } else if (event.isAttack()) {
                GhostPreview.stop();
            } else {
                return;
            }
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (event.isUseItem() && event.getHand() == InteractionHand.MAIN_HAND && player.getMainHandItem().is(ESItems.SURVEYORS_PLAN.get())) {
            event.setCanceled(true);
            event.setSwingHand(false);
            ClientPacketDistributor.sendToServer(new StructurePayloads.RequestList());
        }
    }

    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (GhostPreview.isActive() && Minecraft.getInstance().screen == null && event.getScrollDeltaY() != 0) {
            GhostPreview.rotate(event.getScrollDeltaY() > 0 ? 1 : -1);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onKey(InputEvent.Key event) {
        if (!GhostPreview.isActive() || Minecraft.getInstance().screen != null || event.getAction() == GLFW.GLFW_RELEASE) {
            return;
        }
        int step = (event.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 5 : 1;
        switch (event.getKey()) {
            case GLFW.GLFW_KEY_M -> {
                if (event.getAction() == GLFW.GLFW_PRESS) {
                    GhostPreview.toggleMirror();
                }
            }
            case GLFW.GLFW_KEY_PAGE_UP -> GhostPreview.raise(step);
            case GLFW.GLFW_KEY_PAGE_DOWN -> GhostPreview.raise(-step);
            default -> {}
        }
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        GhostPreview.stop();
        ClientStructures.clear();
    }

    /** What the preview is and how to drive it, above the hotbar. */
    private static final class HintLayer implements GuiLayer {
        @Override
        public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
            Minecraft minecraft = Minecraft.getInstance();
            ClientStructures.Preview preview = GhostPreview.active();
            if (preview == null || minecraft.options.hideGui) {
                return;
            }
            BlockPos origin = GhostPreview.origin();
            Component status = Component.translatable("hud.emergentstealth.structure_preview", preview.id().getPath(),
                    GhostPreview.rotation().ordinal() * 90, GhostPreview.mirror() == Mirror.NONE ? "-" : "M",
                    String.format("%+d", GhostPreview.raise()),
                    origin == null ? "—" : origin.getX() + " " + origin.getY() + " " + origin.getZ());
            Component keys = Component.translatable("hud.emergentstealth.structure_preview.keys");
            int y = graphics.guiHeight() - 84;
            graphics.centeredText(minecraft.font, status, graphics.guiWidth() / 2, y, 0xFFE8DCC0);
            graphics.centeredText(minecraft.font, keys, graphics.guiWidth() / 2, y + 11, 0xFFB8AC90);
        }
    }
}
