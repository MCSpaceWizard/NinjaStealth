package com.mcspacewizard.emergentstealth.client.render;

import java.util.List;

import org.joml.Quaternionf;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.action.ActionPlayback;
import com.mcspacewizard.emergentstealth.action.BodyCarrying;
import com.mcspacewizard.emergentstealth.action.CarryLink;
import com.mcspacewizard.emergentstealth.anim.ActionClock;
import com.mcspacewizard.emergentstealth.anim.Bone;
import com.mcspacewizard.emergentstealth.anim.HumanoidPose;
import com.mcspacewizard.emergentstealth.client.anim.Bends;
import com.mcspacewizard.emergentstealth.client.anim.BodyPlacement;
import com.mcspacewizard.emergentstealth.client.anim.PoseContext;
import com.mcspacewizard.emergentstealth.client.anim.PoseGraph;
import com.mcspacewizard.emergentstealth.client.anim.ProceduralState;
import com.mcspacewizard.emergentstealth.client.anim.ProceduralStates;
import com.mcspacewizard.emergentstealth.client.hud.ClientDetection;
import com.mcspacewizard.emergentstealth.client.hud.DetectionIndicator;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.data.Archetype;
import com.mcspacewizard.emergentstealth.data.Outfit;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.registry.ESRegistries;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.entity.animation.json.AnimationLoader;

/**
 * Player-shaped NPC renderer: body skin from the archetype, outfit layers on top, then vanilla armour
 * and held items (HumanoidMobRenderer adds the item-in-hand layer itself).
 */
public class NpcRenderer extends HumanoidMobRenderer<StealthNpc, NpcRenderState, NpcModel> {
    /** Hip height on the model, blocks: the pivot for whole-body lean. */
    private static final float HIPS = 0.75F;
    public static final Identifier DEFAULT_BODY = EmergentStealth.id("textures/entity/npc/body/body_1.png");

    private final NpcOutfitLayer outfitLayer;

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcModel(Bends.bake(ESModelLayers.npc())), 0.5F);
        this.outfitLayer = new NpcOutfitLayer(this, context.getResourceManager());
        this.addLayer(this.outfitLayer);
        // Armour is an NpcModel with elbows and knees too, so it follows every pose.
        ArmorModelSet<NpcModel> armorModels = ESModelLayers.npcArmor().map(definition -> new NpcModel(Bends.bake(definition)));
        this.addLayer(new HumanoidArmorLayer<>(this, armorModels, context.getEquipmentRenderer()));
    }

    @Override
    public NpcRenderState createRenderState() {
        return new NpcRenderState();
    }

    @Override
    public void extractRenderState(StealthNpc npc, NpcRenderState state, float partialTick) {
        super.extractRenderState(npc, state, partialTick);
        extractAnimation(npc, state, partialTick);
        state.outfitLayers.clear();
        state.body = DEFAULT_BODY;
        state.indicator = ESConfig.SHOW_DETECTION_INDICATORS.get() && !npc.isBody()
                ? DetectionIndicator.build(ClientDetection.get(npc.getId())) : null;
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

    /** The procedural pose, where the body lies, and a takedown victim clip (design doc 17 §8). */
    private static void extractAnimation(StealthNpc npc, NpcRenderState state, float partialTick) {
        ProceduralState sim = ProceduralStates.get(npc);
        PoseGraph.HUMANOID.evaluate(PoseContext.of(npc, partialTick, sim), state.pose);
        state.placement.compute(npc, sim, partialTick);
        state.victimClip = null;
        ActionPlayback action = npc.getData(ESAttachments.ACTION);
        long gameTime = npc.level().getGameTime();
        if (action.role() == ActionPlayback.Role.VICTIM && ActionClock.playing(action, gameTime, partialTick)) {
            AnimationDefinition clip = AnimationLoader.INSTANCE.getAnimation(ActionClock.clipId(action, "victim"));
            if (clip != null && clip.lengthInSeconds() > 0.0F) {
                // The clip is stretched over the action, which takedown-speed skills shorten.
                float progress = ActionClock.progress(action, gameTime, partialTick);
                state.victimClip = clip;
                state.victimClipMillis = (long) (Math.min(progress, 0.999F) * clip.lengthInSeconds() * 1000.0F);
            }
        }
    }

    /** Lays bodies down (design doc 17 §2): pivots about the middle, onto the ground slope, or along the drag chain. */
    @Override
    protected void setupRotations(NpcRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        BodyPlacement placement = state.placement;
        if (placement.fall <= 0.0F) {
            super.setupRotations(state, poseStack, bodyRot, entityScale);
            return;
        }
        poseStack.translate(placement.dx / entityScale, placement.dy / entityScale, placement.dz / entityScale);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - placement.yaw));
        // Face up, the head lies behind (+z here); face down, in front. Raise whichever end the slope says.
        poseStack.mulPose(Axis.XP.rotationDegrees((placement.faceUp ? -1.0F : 1.0F) * placement.pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(placement.roll));
        poseStack.mulPose(Axis.XP.rotationDegrees((placement.faceUp ? 90.0F : -90.0F) * placement.fall));
        poseStack.translate(0.0F, -BodyPlacement.PIVOT_HEIGHT, 0.0F);
    }

    /** The pose's whole-body channels (lean): rotation about the hips, then an offset. Model space, y down. */
    @Override
    protected void scale(NpcRenderState state, PoseStack poseStack) {
        super.scale(state, poseStack);
        HumanoidPose pose = state.pose;
        if (!pose.touched(Bone.ROOT)) {
            return;
        }
        poseStack.translate(pose.get(Bone.ROOT, HumanoidPose.POS_X) / 16.0F, pose.get(Bone.ROOT, HumanoidPose.POS_Y) / 16.0F,
                pose.get(Bone.ROOT, HumanoidPose.POS_Z) / 16.0F);
        poseStack.translate(0.0F, -HIPS, 0.0F);
        poseStack.mulPose(new Quaternionf().rotationZYX(pose.get(Bone.ROOT, HumanoidPose.ROT_Z),
                pose.get(Bone.ROOT, HumanoidPose.ROT_Y), pose.get(Bone.ROOT, HumanoidPose.ROT_X)));
        poseStack.translate(0.0F, HIPS, 0.0F);
    }

    /** A body on your own shoulders would fill the first-person view. */
    @Override
    public boolean shouldRender(StealthNpc npc, Frustum frustum, double camX, double camY, double camZ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.getCameraType().isFirstPerson() && minecraft.player != null
                && BodyCarrying.carriedBody(minecraft.player) == npc && BodyCarrying.link(minecraft.player).mode() == CarryLink.Mode.CARRY) {
            return false;
        }
        return super.shouldRender(npc, frustum, camX, camY, camZ);
    }

    /** Dragged bodies trail their chain outside the (small, flat) hitbox. */
    @Override
    protected AABB getBoundingBoxForCulling(StealthNpc npc) {
        AABB box = super.getBoundingBoxForCulling(npc);
        return npc.isBody() ? box.inflate(1.2, 0.8, 1.2) : box;
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
