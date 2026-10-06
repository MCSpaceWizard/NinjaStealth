package com.mcspacewizard.emergentstealth.client.sound;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.client.debug.NoiseDebugRenderer;
import com.mcspacewizard.emergentstealth.network.NoiseDebugPayload;
import com.mcspacewizard.emergentstealth.network.ThrowItemPayload;
import com.mcspacewizard.emergentstealth.registry.ESEntities;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDebugRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Client side of hearing and distractions (design doc 16): the throw key, the thrown item's renderer and the
 * noise debug view. Kept apart from ESClientEvents so stages merge cleanly.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID, value = Dist.CLIENT)
public final class SoundClientEvents {
    private SoundClientEvents() {}

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(EmergentStealth.id("main"));
    /** Throw one of the held item (default G). Tap = lob; hold = charge a longer throw. */
    public static final KeyMapping THROW = new KeyMapping("key.emergentstealth.throw", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    /** Held shorter than this is a tap (a lob). */
    private static final int TAP_TICKS = 4;
    /** Ticks of holding (after the tap window) for a full-power throw. */
    private static final int FULL_CHARGE_TICKS = 16;

    private static int heldTicks = -1;

    // --- Mod bus ---

    @SubscribeEvent
    static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(THROW);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ESEntities.THROWN_ITEM.get(), ThrownItemRenderer::new);
    }

    @SubscribeEvent
    static void onRegisterDebugRenderers(RegisterDebugRenderersEvent event) {
        event.register(new NoiseDebugRenderer());
    }

    @SubscribeEvent
    static void onRegisterClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(NoiseDebugPayload.TYPE, NoiseDebugRenderer::handle);
    }

    // --- Game bus ---

    /** Counts how long the throw key is held and sends the throw when it's released. */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            heldTicks = -1;
            return;
        }
        // A tap shorter than a tick never shows up as "down": catch it from the click queue.
        boolean clicked = false;
        while (THROW.consumeClick()) {
            clicked = true;
        }
        if (clicked && heldTicks < 0 && !THROW.isDown()) {
            heldTicks = 0;
        }
        if (THROW.isDown()) {
            heldTicks++;
            if (heldTicks > TAP_TICKS && heldTicks <= TAP_TICKS + FULL_CHARGE_TICKS) {
                int bars = Math.round(10.0F * charge(heldTicks));
                minecraft.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                        "|".repeat(bars) + ".".repeat(10 - bars)));
            }
        } else if (heldTicks >= 0) {
            if (minecraft.getConnection().hasChannel(ThrowItemPayload.TYPE)) {
                ClientPacketDistributor.sendToServer(new ThrowItemPayload(charge(heldTicks)));
            }
            heldTicks = -1;
        }
    }

    /** 0 for a tap, rising to 1 over {@link #FULL_CHARGE_TICKS}. */
    static float charge(int ticks) {
        if (ticks <= TAP_TICKS) {
            return 0.0F;
        }
        return Math.min(1.0F, (ticks - TAP_TICKS) / (float) FULL_CHARGE_TICKS);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        NoiseDebugRenderer.clear();
        heldTicks = -1;
    }
}
