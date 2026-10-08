package com.mcspacewizard.emergentstealth.client.anim;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import it.unimi.dsi.fastutil.Pair;
import com.zigythebird.bendable_cuboids.api.BendableCube;
import com.zigythebird.bendable_cuboids.api.BendableModelPart;
import com.zigythebird.bendable_cuboids.api.ILayerDefinition;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.core.Direction;

/**
 * Elbows and knees (design doc 17 §8) through Bendable Cuboids, a required client dependency. Players' models are
 * baked with bends by Bendable Cuboids itself; NPC body and armour models are baked here.
 */
public final class Bends {
    private Bends() {}

    /** Head and torso stay rigid; limbs bend at the middle of their cube (Bendable Cuboids' default, UP / -1). */
    private static final Set<String> RIGID = Set.of("head", "hat", "body", "jacket");

    /** Bakes a humanoid layer with bendable arms and legs. */
    public static ModelPart bake(LayerDefinition definition) {
        Map<String, Pair<Direction, Integer>> cuboids = new HashMap<>();
        return ((ILayerDefinition) definition).bakeRootWithBends(cuboids, RIGID);
    }

    /** Bends a part's first cube (radians, same sense as xRot). No-op for parts baked without bends. */
    public static void bend(ModelPart part, float radians) {
        BendableCube cube = ((BendableModelPart) (Object) part).bc$getCuboid(0);
        if (cube != null) {
            cube.applyBend(radians);
        }
    }
}
