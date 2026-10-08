package com.mcspacewizard.emergentstealth.client.anim.bend;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.zigythebird.bendable_cuboids.api.BendableCube;
import com.zigythebird.bendable_cuboids.api.BendableModelPart;
import com.zigythebird.bendable_cuboids.api.ILayerDefinition;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.core.Direction;

/** Bendable Cuboids calls. Only loaded through {@link BendBridge} once the mod is known to be present. */
final class BendCompat {
    private BendCompat() {}

    /** Head and torso stay rigid; limbs bend at the middle of their cube (Bendable Cuboids' default, UP / -1). */
    private static final Set<String> RIGID = Set.of("head", "hat", "body", "jacket");

    static ModelPart bake(LayerDefinition definition) {
        Map<String, Pair<Direction, Integer>> cuboids = new HashMap<>();
        return ((ILayerDefinition) definition).bakeRootWithBends(cuboids, RIGID);
    }

    static void bend(ModelPart part, float radians) {
        BendableCube cube = ((BendableModelPart) (Object) part).bc$getCuboid(0);
        if (cube != null) {
            cube.applyBend(radians);
        }
    }
}
