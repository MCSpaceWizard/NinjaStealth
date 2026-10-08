package com.mcspacewizard.emergentstealth.client.action;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.client.sound.SoundClientEvents;
import com.mcspacewizard.emergentstealth.progression.TechniqueState;
import com.mcspacewizard.emergentstealth.progression.Techniques;
import com.mcspacewizard.emergentstealth.progression.UseTechniquePayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

/** Techniques on the client (design doc 26 §3): the X key and the small name/cooldown readout by the light gem. */
@EventBusSubscriber(modid = EmergentStealth.MODID, value = Dist.CLIENT)
public final class TechniqueHud {
    private TechniqueHud() {}

    /** X uses the selected technique; sneak + X selects the next one. */
    public static final KeyMapping TECHNIQUE = new KeyMapping("key.emergentstealth.technique", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X,
            SoundClientEvents.CATEGORY);
    private static final Identifier LAYER = EmergentStealth.id("technique");

    @SubscribeEvent
    static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(TECHNIQUE);
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, LAYER, new Layer());
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (TECHNIQUE.consumeClick()) {
            if (minecraft.player != null && minecraft.getConnection() != null) {
                ClientPacketDistributor.sendToServer(new UseTechniquePayload(minecraft.player.isShiftKeyDown()));
            }
        }
    }

    private static final class Layer implements GuiLayer {
        @Override
        public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.options.hideGui || minecraft.player.isSpectator()) {
                return;
            }
            Techniques.Technique technique = Techniques.selected(minecraft.player);
            if (technique == null) {
                return;
            }
            long now = minecraft.player.level().getGameTime();
            TechniqueState state = Techniques.state(minecraft.player);
            Component name = Component.translatable("technique." + technique.id().getNamespace() + "." + technique.id().getPath());
            long cooldown = state.cooldownLeft(technique.id(), now);
            int color = 0xFFE8DCC0;
            Component text = name;
            if (state.active(technique.id(), now)) {
                color = 0xFF8FD18A;
            } else if (cooldown > 0) {
                color = 0xFF8A8478;
                text = Component.translatable("hud.emergentstealth.technique_cooldown", name, (cooldown + 19) / 20);
            }
            // To the right of the light gem (which is centred above the hotbar).
            graphics.text(minecraft.font, text, graphics.guiWidth() / 2 + 22, graphics.guiHeight() - 54, color, true);
        }
    }
}
