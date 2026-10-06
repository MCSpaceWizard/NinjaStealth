package com.mcspacewizard.emergentstealth.client.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Draws an NPC's outfit layers over its body. Layers whose texture doesn't exist are skipped, so a
 * resource pack can ship partial art without purple-checkerboard NPCs.
 */
public class NpcOutfitLayer extends RenderLayer<NpcRenderState, HumanoidModel<NpcRenderState>> {
    private final ResourceManager resourceManager;
    // Renderers are rebuilt on resource reload, so caching per instance stays correct.
    private final Map<Identifier, Boolean> textureExists = new HashMap<>();

    public NpcOutfitLayer(RenderLayerParent<NpcRenderState, HumanoidModel<NpcRenderState>> parent, ResourceManager resourceManager) {
        super(parent);
        this.resourceManager = resourceManager;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, NpcRenderState state, float yRot, float xRot) {
        List<Identifier> layers = state.outfitLayers;
        for (int i = 0; i < layers.size(); i++) {
            Identifier texture = layers.get(i);
            if (exists(texture)) {
                renderColoredCutoutModel(this.getParentModel(), texture, poseStack, nodeCollector, packedLight, state, -1, i + 1);
            }
        }
    }

    boolean exists(Identifier texture) {
        return textureExists.computeIfAbsent(texture, id -> resourceManager.getResource(id).isPresent());
    }
}
