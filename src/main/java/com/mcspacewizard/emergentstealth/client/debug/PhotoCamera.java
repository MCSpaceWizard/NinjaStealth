package com.mcspacewizard.emergentstealth.client.debug;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * {@code /esphoto <yaw> [pitch]}: swings the third-person camera around the player by an angle, for checking
 * animations from the side ({@code /esphoto off} to stop). The camera is pulled back along its own rotation after
 * this event, so turning it here orbits the player without moving the player. Client-only, needs no op.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID, value = Dist.CLIENT)
public final class PhotoCamera {
    private PhotoCamera() {}

    private static boolean enabled;
    private static float yawOffset;
    private static float pitchOffset;

    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("esphoto")
                .then(Commands.literal("off").executes(ctx -> {
                    enabled = false;
                    return 1;
                }))
                .then(Commands.argument("yaw", FloatArgumentType.floatArg(-360.0F, 360.0F))
                        .executes(ctx -> set(FloatArgumentType.getFloat(ctx, "yaw"), 0.0F))
                        .then(Commands.argument("pitch", FloatArgumentType.floatArg(-90.0F, 90.0F))
                                .executes(ctx -> set(FloatArgumentType.getFloat(ctx, "yaw"), FloatArgumentType.getFloat(ctx, "pitch"))))));
    }

    private static int set(float yaw, float pitch) {
        enabled = true;
        yawOffset = yaw;
        pitchOffset = pitch;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("Photo camera: " + yaw + "° around, " + pitch + "° pitch (third person)"));
        }
        return 1;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (enabled && !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            event.setYaw(event.getYaw() + yawOffset);
            event.setPitch(Math.max(-90.0F, Math.min(90.0F, event.getPitch() + pitchOffset)));
        }
    }
}
