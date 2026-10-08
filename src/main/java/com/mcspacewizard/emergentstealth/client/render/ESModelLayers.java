package com.mcspacewizard.emergentstealth.client.render;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Model layer locations and definitions for our entities. */
public final class ESModelLayers {
    private ESModelLayers() {}

    /** Player-shaped (wide arms) NPC body, 64x64 skin layout including the outer layer. */
    public static final ModelLayerLocation NPC = new ModelLayerLocation(EmergentStealth.id("stealth_npc"), "main");

    public static final ArmorModelSet<ModelLayerLocation> NPC_ARMOR = new ArmorModelSet<>(
            new ModelLayerLocation(EmergentStealth.id("stealth_npc_armor/head"), "main"),
            new ModelLayerLocation(EmergentStealth.id("stealth_npc_armor/chest"), "main"),
            new ModelLayerLocation(EmergentStealth.id("stealth_npc_armor/legs"), "main"),
            new ModelLayerLocation(EmergentStealth.id("stealth_npc_armor/feet"), "main"));

    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NPC, ESModelLayers::npc);
        ArmorModelSet<LayerDefinition> armor = npcArmor();
        event.registerLayerDefinition(NPC_ARMOR.head(), armor::head);
        event.registerLayerDefinition(NPC_ARMOR.chest(), armor::chest);
        event.registerLayerDefinition(NPC_ARMOR.legs(), armor::legs);
        event.registerLayerDefinition(NPC_ARMOR.feet(), armor::feet);
    }

    /** The NPC body. The renderer bakes it itself, with elbows and knees ({@code Bends}). */
    public static LayerDefinition npc() {
        return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
    }

    /** NPC armour, baked with bends too so it follows the limbs. */
    public static ArmorModelSet<LayerDefinition> npcArmor() {
        ArmorModelSet<MeshDefinition> armor = HumanoidModel.createArmorMeshSet(new CubeDeformation(0.5F), new CubeDeformation(1.0F));
        return armor.map(mesh -> LayerDefinition.create(mesh, 64, 32));
    }
}
