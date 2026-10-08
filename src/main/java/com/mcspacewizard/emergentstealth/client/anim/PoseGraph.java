package com.mcspacewizard.emergentstealth.client.anim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mcspacewizard.emergentstealth.anim.HumanoidPose;
import com.mcspacewizard.emergentstealth.client.anim.layer.BodyLayer;
import com.mcspacewizard.emergentstealth.client.anim.layer.BreathingLayer;
import com.mcspacewizard.emergentstealth.client.anim.layer.CarrierLayer;
import com.mcspacewizard.emergentstealth.client.anim.layer.CrawlLayer;
import com.mcspacewizard.emergentstealth.client.anim.layer.LeanLayer;

/**
 * The ordered layer stack that turns a {@link PoseContext} into a {@link HumanoidPose} (design doc 17 §8).
 * The same graph drives NPCs ({@code NpcRenderer}) and players (the PAL adapter).
 */
public final class PoseGraph {
    /** Every humanoid: NPCs and players. */
    public static final PoseGraph HUMANOID = new PoseGraph(List.of(
            new CrawlLayer(),
            new CarrierLayer(),
            new BodyLayer(),
            new BreathingLayer(),
            new LeanLayer()));

    private final List<PoseLayer> layers;

    public PoseGraph(List<PoseLayer> layers) {
        List<PoseLayer> sorted = new ArrayList<>(layers);
        sorted.sort(Comparator.comparingInt(PoseLayer::priority));
        this.layers = List.copyOf(sorted);
    }

    /** Evaluates into {@code out} (reset first). Returns whether any layer contributed. */
    public boolean evaluate(PoseContext context, HumanoidPose out) {
        out.reset();
        boolean any = false;
        for (PoseLayer layer : layers) {
            float w = layer.weight(context);
            if (w > 0.0F) {
                layer.apply(context, out, Math.min(1.0F, w));
                any = true;
            }
        }
        return any;
    }

    /** Whether any layer wants this entity this frame (cheap check, no pose written). */
    public boolean wants(PoseContext context) {
        for (PoseLayer layer : layers) {
            if (layer.weight(context) > 0.0F) {
                return true;
            }
        }
        return false;
    }
}
