package com.mcspacewizard.emergentstealth.client.tool;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.client.sound.SoundClientEvents;
import com.mcspacewizard.emergentstealth.network.SmokeDebugPayload;
import com.mcspacewizard.emergentstealth.network.UseToolPayload;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.registry.ESEntities;
import com.mcspacewizard.emergentstealth.registry.ESParticles;
import com.mcspacewizard.emergentstealth.tool.ActiveTool;
import com.mcspacewizard.emergentstealth.tool.ThrowableToolItem;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import com.mcspacewizard.emergentstealth.registry.ESMenus;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDebugRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Client side of the beta toolkit (design doc 21): the tool wheel key (R), quick use (V), the active tool icon,
 * tool particles and entity renderers, and smoke in the debug view. Kept apart from ESClientEvents so features
 * merge cleanly.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID, value = Dist.CLIENT)
public final class ToolClientEvents {
    private ToolClientEvents() {}

    /** Hold to open the tool wheel; release on a tool to make it active (default R). */
    public static final KeyMapping TOOL_WHEEL = new KeyMapping("key.emergentstealth.tool_wheel", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R, SoundClientEvents.CATEGORY);
    /** Use the active tool without changing the held item (default V). Tap = lob; hold = charge a throw. */
    public static final KeyMapping QUICK_USE = new KeyMapping("key.emergentstealth.quick_use", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V, SoundClientEvents.CATEGORY);

    /** Held shorter than this is a tap (a lob). Same feel as the throw key. */
    private static final int TAP_TICKS = 4;
    private static final int FULL_CHARGE_TICKS = 16;

    private static int heldTicks = -1;

    // --- Mod bus ---

    @SubscribeEvent
    static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(TOOL_WHEEL);
        event.register(QUICK_USE);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ESEntities.SMOKE_CLOUD.get(), NoopRenderer::new);
        event.registerEntityRenderer(ESEntities.LIT_FIRECRACKER.get(), context -> new ThrownItemRenderer<>(context, 0.75F, true));
    }

    @SubscribeEvent
    static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ESParticles.SMOKE_CLOUD, ToolParticles.SmokeCloudProvider::new);
        event.registerSpriteSet(ESParticles.BLINDING_PUFF, ToolParticles.BlindingPuffProvider::new);
        event.registerSpriteSet(ESParticles.SMOKE_CORE, ToolEffectParticles::smokeCore);
        event.registerSpriteSet(ESParticles.STEAM, ToolEffectParticles::steam);
        event.registerSpriteSet(ESParticles.EMBER, ToolEffectParticles::ember);
        event.registerSpriteSet(ESParticles.DROWSY, ToolEffectParticles::drowsy);
        event.registerSpriteSet(ESParticles.DIZZY_STAR, ToolEffectParticles::dizzyStar);
        event.registerSpriteSet(ESParticles.GLINT, ToolEffectParticles::glint);
        event.registerSpriteSet(ESParticles.TAG_PING, ToolEffectParticles::tagPing);
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ESMenus.TOOLBELT.get(), ToolbeltScreen::new);
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ActiveToolHud.LAYER_ID, new ActiveToolHud());
    }

    @SubscribeEvent
    static void onRegisterDebugRenderers(RegisterDebugRenderersEvent event) {
        event.register(new SmokeDebugRenderer());
    }

    @SubscribeEvent
    static void onRegisterClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(SmokeDebugPayload.TYPE, SmokeDebugRenderer::handle);
    }

    // --- Game bus ---

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            heldTicks = -1;
            return;
        }
        boolean wheel = false;
        while (TOOL_WHEEL.consumeClick()) {
            wheel = true;
        }
        if (wheel && minecraft.screen == null) {
            minecraft.setScreen(new ToolWheelScreen(TOOL_WHEEL));
            heldTicks = -1;
            return;
        }
        tickQuickUse(minecraft);
    }

    /** Counts how long V is held and sends the quick use when it's released (like the throw key). */
    private static void tickQuickUse(Minecraft minecraft) {
        boolean clicked = false;
        while (QUICK_USE.consumeClick()) {
            clicked = true;
        }
        if (clicked && heldTicks < 0 && !QUICK_USE.isDown()) {
            heldTicks = 0;
        }
        if (QUICK_USE.isDown()) {
            heldTicks++;
            ActiveTool tool = minecraft.player.getData(ESAttachments.ACTIVE_TOOL);
            if (tool.item() instanceof ThrowableToolItem && heldTicks > TAP_TICKS && heldTicks <= TAP_TICKS + FULL_CHARGE_TICKS) {
                int bars = Math.round(10.0F * charge(heldTicks));
                minecraft.player.sendOverlayMessage(Component.literal("|".repeat(bars) + ".".repeat(10 - bars)));
            }
        } else if (heldTicks >= 0) {
            if (minecraft.getConnection().hasChannel(UseToolPayload.TYPE)) {
                ClientPacketDistributor.sendToServer(new UseToolPayload(charge(heldTicks)));
            }
            heldTicks = -1;
        }
    }

    static float charge(int ticks) {
        if (ticks <= TAP_TICKS) {
            return 0.0F;
        }
        return Math.min(1.0F, (ticks - TAP_TICKS) / (float) FULL_CHARGE_TICKS);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SmokeDebugRenderer.clear();
        heldTicks = -1;
    }
}
