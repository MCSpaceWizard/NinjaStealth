package com.mcspacewizard.emergentstealth.client.light;

import org.joml.Vector3f;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.LightmapRenderState;

/**
 * "Dark is dark" (L-07 b, doc 30 §5): caps the brightness slider and lowers the ambient floor of the lightmap
 * so unlit places look as dark to the player as they are to guards. Night vision is untouched (the lightmap
 * shader takes {@code max(ambient, nightVision)}), and it steps aside for Iris shader packs unless asked.
 */
public final class DarkIsDark {
    private DarkIsDark() {}

    private static boolean shadersSeen;

    /** Called at the end of {@code LightmapRenderStateExtractor.extract}, i.e. once per lightmap update. */
    public static void apply(LightmapRenderState state) {
        VisualSettings settings = VisualLighting.settings();
        Minecraft minecraft = Minecraft.getInstance();
        // needsUpdate: values were just written this frame (otherwise we'd scale them again every frame).
        if (!state.needsUpdate || !settings.darkIsDark() || minecraft.level == null || minecraft.player == null) {
            return;
        }
        boolean shaders = ShaderPacks.inUse();
        if (shaders != shadersSeen) {
            shadersSeen = shaders;
            EmergentStealth.LOGGER.info("Iris shader pack {}: dark-is-dark {}", shaders ? "active" : "inactive",
                    shaders && !settings.darkIsDarkWithShaders() ? "paused" : "applied");
        }
        if (shaders && !settings.darkIsDarkWithShaders()) {
            return;
        }
        state.brightness = Math.min(state.brightness, settings.darkMaxBrightness());
        state.ambientColor = new Vector3f(state.ambientColor).mul(settings.darkAmbientScale());
    }
}
