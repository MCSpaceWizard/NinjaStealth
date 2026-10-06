package com.mcspacewizard.emergentstealth.client.render;

import java.util.List;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.data.Archetype;
import com.mcspacewizard.emergentstealth.data.Outfit;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESRegistries;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.Identifier;

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
    public Identifier getTextureLocation(NpcRenderState state) {
        return state.body;
    }
}
