package com.mcspacewizard.emergentstealth.client.hud;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.network.LightGemPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The light gem (Thief): how visible you are right now, straight from the server's exposure model
 * (design doc 13 §2). Dark gem = hidden in shadow; glowing gem = lit.
 */
public final class LightGemHud implements GuiLayer {
    public static final Identifier LAYER_ID = EmergentStealth.id("light_gem");

    private static final Identifier FRAME = EmergentStealth.id("hud/light_gem_frame");
    private static final Identifier DARK = EmergentStealth.id("hud/light_gem_dark");
    private static final Identifier LIT = EmergentStealth.id("hud/light_gem_lit");
    private static final int WIDTH = 32;
    private static final int HEIGHT = 16;
    /** Above the health/food rows, centred over the hotbar. */
    private static final int BOTTOM_OFFSET = 58;

    private static volatile float target;
    private static boolean received;
    private float shown;

    public static void handle(LightGemPayload payload, IPayloadContext context) {
        target = Mth.clamp(payload.exposure(), 0.0F, 1.0F);
        received = true;
    }

    public static void reset() {
        target = 0.0F;
        received = false;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!received || !ESConfig.SHOW_LIGHT_GEM.get() || minecraft.options.hideGui
                || minecraft.player == null || minecraft.player.isSpectator()) {
            return;
        }
        // Smooth toward the server value (frame-rate independent enough for a HUD element).
        shown += (target - shown) * Math.min(1.0F, deltaTracker.getRealtimeDeltaTicks() * 0.35F);

        int x = graphics.guiWidth() / 2 - WIDTH / 2;
        int y = graphics.guiHeight() - BOTTOM_OFFSET;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DARK, x, y, WIDTH, HEIGHT);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LIT, x, y, WIDTH, HEIGHT, shown);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, x, y, WIDTH, HEIGHT);
    }
}
