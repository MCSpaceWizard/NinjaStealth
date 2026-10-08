package com.mcspacewizard.emergentstealth.client.ui;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.client.sound.SoundClientEvents;
import com.mcspacewizard.emergentstealth.client.ui.screen.DialogueScreen;
import com.mcspacewizard.emergentstealth.client.ui.screen.SkillTreeScreen;
import com.mcspacewizard.emergentstealth.client.ui.screen.SumiConfigScreen;
import com.mcspacewizard.emergentstealth.network.OpenDialoguePreviewPayload;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client wiring for the Sumi screens (design doc 31 §3): the skill tree key (K), the {@code /esui} client
 * command, and the server's "open the dialogue preview" message.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID, value = Dist.CLIENT)
public final class SumiClientEvents {
    private SumiClientEvents() {}

    /** Opens the skill tree (default K). */
    public static final KeyMapping SKILLS = new KeyMapping("key.emergentstealth.skills", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
            SoundClientEvents.CATEGORY);

    /** A screen to open on the next tick (commands run while the chat screen is still closing). */
    private static @Nullable Supplier<Screen> pending;

    @SubscribeEvent
    static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(SKILLS);
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(OpenDialoguePreviewPayload.TYPE, SumiClientEvents::handleDialogue);
    }

    private static void handleDialogue(OpenDialoguePreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new DialogueScreen()));
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (pending != null && minecraft.screen == null) {
            Supplier<Screen> open = pending;
            pending = null;
            minecraft.setScreen(open.get());
        }
        while (SKILLS.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new SkillTreeScreen(SKILLS));
            }
        }
    }

    /** {@code /esui config|skills|dialogue}: opens a Sumi screen directly (also used by the screenshot automation). */
    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("esui")
                .then(Commands.literal("config").executes(ctx -> open(() -> new SumiConfigScreen(null,
                        ModList.get().getModContainerById(EmergentStealth.MODID).orElse(null)))))
                .then(Commands.literal("skills").executes(ctx -> open(() -> new SkillTreeScreen(SKILLS))))
                .then(Commands.literal("dialogue").executes(ctx -> open(DialogueScreen::new))));
    }

    private static int open(Supplier<Screen> screen) {
        pending = screen;
        return 1;
    }
}
