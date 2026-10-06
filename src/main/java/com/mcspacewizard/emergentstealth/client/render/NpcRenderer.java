package com.mcspacewizard.emergentstealth.client.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.client.hud.ClientDetection;
import com.mcspacewizard.emergentstealth.client.hud.DetectionIndicator;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.data.Archetype;
import com.mcspacewizard.emergentstealth.data.Outfit;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESRegistries;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityAttachment;

/**
 * Player-shaped NPC renderer: body skin from the archetype, outfit layers on top, then vanilla armour
 * and held items (HumanoidMobRenderer adds the item-in-hand layer itself).
 */
public class NpcRenderer extends HumanoidMobRenderer<StealthNpc, NpcRenderState, HumanoidModel<NpcRenderState>> {
    public static final Identifier DEFAULT_BODY = EmergentStealth.id("textures/entity/npc/body/body_1.png");

    private final NpcOutfitLayer outfitLayer;

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ESModelLayers.NPC)), 0.5F);
        this.outfitLayer = new NpcOutfitLayer(this, context.getResourceManager());
        this.addLayer(this.outfitLayer);
        ArmorModelSet<HumanoidModel<NpcRenderState>> armorModels =
                ArmorModelSet.bake(ESModelLayers.NPC_ARMOR, context.getModelSet(), HumanoidModel::new);
        this.addLayer(new HumanoidArmorLayer<>(this, armorModels, context.getEquipmentRenderer()));
    }

    @Override
    public NpcRenderState createRenderState() {
        return new NpcRenderState();
    }

    @Override
    public void extractRenderState(StealthNpc npc, NpcRenderState state, float partialTick) {
        super.extractRenderState(npc, state, partialTick);
        state.outfitLayers.clear();
        state.body = DEFAULT_BODY;
        state.indicator = ESConfig.SHOW_DETECTION_INDICATORS.get() ? DetectionIndicator.build(ClientDetection.get(npc.getId())) : null;
        state.bark = ESConfig.SHOW_BARKS.get() ? com.mcspacewizard.emergentstealth.client.hud.ClientBarks.get(npc.getId()) : null;
        state.indicatorAttachment = state.indicator == null && state.bark == null ? null
                : npc.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, npc.getYRot(partialTick));

        Archetype archetype = npc.getArchetype().orElse(null);
        if (archetype == null) {
            return;
        }
        List<Identifier> bodies = archetype.bodies();
        if (!bodies.isEmpty()) {
            Identifier body = bodies.get(Math.floorMod(npc.getBodyVariant(), bodies.size()));
            state.body = outfitLayer.exists(body) ? body : DEFAULT_BODY;
        }
        archetype.outfit()
                .map(id -> npc.level().registryAccess().lookupOrThrow(ESRegistries.OUTFIT).getValue(id))
                .map(Outfit::layers)
                .ifPresent(state.outfitLayers::addAll);
    }

    @Override
    protected void submitNameDisplay(NpcRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submitNameDisplay(state, poseStack, collector, camera);
        // Stacked above the name tag when there is one (negative offset = higher): indicator, then the bark.
        int offset = state.nameTag != null ? -12 : 0;
        if (state.indicator != null) {
            collector.submitNameTag(poseStack, state.indicatorAttachment, offset, state.indicator, true,
                    state.lightCoords, state.distanceToCameraSq, camera);
            offset -= 12;
        }
        if (state.bark != null) {
            collector.submitNameTag(poseStack, state.indicatorAttachment, offset, state.bark, false,
                    state.lightCoords, state.distanceToCameraSq, camera);
        }
    }

    @Override
    public Identifier getTextureLocation(NpcRenderState state) {
        return state.body;
    }
}
