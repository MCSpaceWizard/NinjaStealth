package com.mcspacewizard.emergentstealth.client.anim.bend;

import java.util.function.Supplier;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.config.ESConfig;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.neoforged.fml.ModList;

/**
 * Elbows and knees (design doc 17 §8). The only entry point to Bendable Cuboids, which is optional: without
 * it (or with {@code animation.bendLimbs = false}) models bake as plain six-part bodies and every bend is a
 * no-op. {@link BendCompat} holds the actual Bendable Cuboids calls and is only class-loaded when the mod is.
 */
public final class BendBridge {
    private BendBridge() {}

    public static final String MOD_ID = "bendable_cuboids";
    private static Boolean loaded;

    /** Bendable Cuboids is installed (regardless of the config switch). */
    public static boolean installed() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(MOD_ID);
            EmergentStealth.LOGGER.info("Animation: Bendable Cuboids {}", loaded ? "found, elbows and knees on" : "not installed, six-part limbs");
        }
        return loaded;
    }

    /** Whether new models should bake with bendable limbs. */
    public static boolean enabled() {
        return installed() && ESConfig.BEND_LIMBS.get();
    }

    /** Bakes a layer with bendable arms and legs when enabled, otherwise the plain way. */
    public static ModelPart bake(LayerDefinition definition, Supplier<ModelPart> plain) {
        if (enabled()) {
            try {
                return BendCompat.bake(definition);
            } catch (Throwable e) {
                EmergentStealth.LOGGER.warn("Animation: baking bendable limbs failed, using six-part limbs", e);
            }
        }
        return plain.get();
    }

    /** Bends a part's first cube (radians, same sense as xRot). No-op for plain parts. */
    public static void bend(ModelPart part, float radians) {
        if (installed()) {
            BendCompat.bend(part, radians);
        }
    }
}
