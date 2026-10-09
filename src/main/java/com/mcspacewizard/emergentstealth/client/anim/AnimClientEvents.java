package com.mcspacewizard.emergentstealth.client.anim;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.action.ActionPlayback;
import com.mcspacewizard.emergentstealth.anim.ActionClock;
import com.mcspacewizard.emergentstealth.client.anim.pal.PalAnimations;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client side of animation (design doc 17 §8): registers the PAL layers, steps the procedural sims at 20 Hz,
 * damps mouse look and turns the first-person camera during actions. Kept apart from ESClientEvents so features
 * merge cleanly.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID, value = Dist.CLIENT)
public final class AnimClientEvents {
    private AnimClientEvents() {}

    // --- Mod bus ---

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(PalAnimations::register);
    }

    // --- Game bus ---

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && !minecraft.isPaused()) {
            ProceduralStates.tick(minecraft.level);
        }
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ProceduralStates.clear();
    }

    /** Mouse look is damped (or locked) while you're in an action. */
    @SubscribeEvent
    static void onPlayerTurn(CalculatePlayerTurnEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !inAction(player, 0.0F)) {
            return;
        }
        // Vanilla turns by (0.6 s + 0.2)^3: solve for the sensitivity that scales the turn by the factor.
        double factor = ESConfig.ACTION_MOUSE_SCALE.get();
        double base = event.getMouseSensitivity() * 0.6 + 0.2;
        event.setMouseSensitivity((Math.cbrt(factor) * base - 0.2) / 0.6);
    }

    /**
     * The server holds you still during an action by zeroing your movement speed, which vanilla would read as a
     * slowdown and zoom the view in. Keep the field of view steady instead.
     */
    @SubscribeEvent
    static void onFovModifier(ComputeFovModifierEvent event) {
        if (event.getPlayer() instanceof LocalPlayer player && inAction(player, 0.0F)) {
            event.setNewFovModifier(1.0F);
        }
    }

    /** In first person the camera turns with a takedown (angles only). */
    @SubscribeEvent
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !minecraft.options.getCameraType().isFirstPerson() || !ESConfig.TAKEDOWN_CAMERA.get()) {
            return;
        }
        float[] offsets = new float[2];
        TakedownCamera.offsets(player.getData(ESAttachments.ACTION), player.level().getGameTime(), (float) event.getPartialTick(), offsets);
        if (offsets[0] != 0.0F || offsets[1] != 0.0F) {
            event.setPitch(Math.max(-90.0F, Math.min(90.0F, event.getPitch() + offsets[0])));
            event.setYaw(event.getYaw() + offsets[1]);
        }
    }

    private static boolean inAction(LocalPlayer player, float partialTick) {
        ActionPlayback action = player.getData(ESAttachments.ACTION);
        return ActionClock.playing(action, player.level().getGameTime(), partialTick);
    }
}
