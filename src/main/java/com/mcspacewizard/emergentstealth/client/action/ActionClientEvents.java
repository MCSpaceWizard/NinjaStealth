package com.mcspacewizard.emergentstealth.client.action;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.action.BodyCarrying;
import com.mcspacewizard.emergentstealth.action.Takedowns;
import com.mcspacewizard.emergentstealth.action.ToggleCrawlPayload;
import com.mcspacewizard.emergentstealth.client.hud.ClientDetection;
import com.mcspacewizard.emergentstealth.client.sound.SoundClientEvents;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client side of player verbs (design doc 17): the crawl key and the takedown / body prompts. */
@EventBusSubscriber(modid = EmergentStealth.MODID, value = Dist.CLIENT)
public final class ActionClientEvents {
    private ActionClientEvents() {}

    /** Toggle crawling (default Z). */
    public static final KeyMapping CRAWL = new KeyMapping("key.emergentstealth.crawl", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z,
            SoundClientEvents.CATEGORY);
    private static final Identifier PROMPT_LAYER = EmergentStealth.id("action_prompt");
    /** Above the light gem. */
    private static final int PROMPT_BOTTOM_OFFSET = 70;

    @SubscribeEvent
    static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(CRAWL);
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, PROMPT_LAYER, new PromptLayer());
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (CRAWL.consumeClick()) {
            if (minecraft.player != null && minecraft.getConnection() != null) {
                ClientPacketDistributor.sendToServer(ToggleCrawlPayload.INSTANCE);
            }
        }
    }

    /** While moving a body, any right-click puts it down (you can't aim at a body on your shoulder). */
    @SubscribeEvent
    static void onInteractionKey(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.isUseItem() && minecraft.player != null && !BodyCarrying.link(minecraft.player).isNone()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
                ClientPacketDistributor.sendToServer(com.mcspacewizard.emergentstealth.action.DropBodyPayload.INSTANCE);
            }
        }
    }

    /** What the player could do right now with what's under the crosshair, or null. */
    static Component prompt(LocalPlayer player) {
        if (!BodyCarrying.link(player).isNone()) {
            return Component.translatable("prompt.emergentstealth.drop");
        }
        if (!(Minecraft.getInstance().crosshairPickEntity instanceof StealthNpc npc)) {
            return null;
        }
        if (npc.isBody()) {
            return Component.translatable("prompt.emergentstealth.body");
        }
        if (Takedowns.rearBlocker(player, npc) != null) {
            return null;
        }
        ClientDetection.View view = ClientDetection.get(npc.getId());
        if (view != null && (view.detected() || view.searching())) {
            return null; // it's onto you
        }
        return player.getMainHandItem().isEmpty()
                ? Component.translatable("prompt.emergentstealth.takedown")
                : Component.translatable("prompt.emergentstealth.takedown_lethal_only");
    }

    private static final class PromptLayer implements GuiLayer {
        @Override
        public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.options.hideGui || minecraft.player.isSpectator()) {
                return;
            }
            Component text = prompt(minecraft.player);
            if (text != null) {
                graphics.centeredText(minecraft.font, text, graphics.guiWidth() / 2, graphics.guiHeight() - PROMPT_BOTTOM_OFFSET, 0xFFE8DCC0);
            }
        }
    }
}
