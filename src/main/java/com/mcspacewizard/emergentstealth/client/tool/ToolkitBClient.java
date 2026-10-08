package com.mcspacewizard.emergentstealth.client.tool;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.client.render.NpcRenderState;
import com.mcspacewizard.emergentstealth.client.render.NpcRenderer;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.network.LockpickPayloads;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.registry.ESEntities;
import com.mcspacewizard.emergentstealth.tool.SpyglassTags;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client wiring for the beta toolkit, part B (design doc 21): arrow renderers, lockpick screen, spyglass tags. */
@EventBusSubscriber(modid = EmergentStealth.MODID, value = Dist.CLIENT)
public final class ToolkitBClient {
    private ToolkitBClient() {}

    /** Outline colour for spyglass-tagged NPCs. */
    public static final int TAG_OUTLINE = 0xFFE8B23A;

    /** A plain arrow renderer with its own texture. */
    static final class TexturedArrowRenderer<T extends AbstractArrow> extends ArrowRenderer<T, ArrowRenderState> {
        private final Identifier texture;

        TexturedArrowRenderer(EntityRendererProvider.Context context, Identifier texture) {
            super(context);
            this.texture = texture;
        }

        @Override
        protected Identifier getTextureLocation(ArrowRenderState state) {
            return texture;
        }

        @Override
        public ArrowRenderState createRenderState() {
            return new ArrowRenderState();
        }
    }

    private static Identifier projectile(String name) {
        return EmergentStealth.id("textures/entity/projectiles/" + name + ".png");
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ESEntities.WATER_ARROW.get(), c -> new TexturedArrowRenderer<>(c, projectile("water_arrow")));
        event.registerEntityRenderer(ESEntities.FIRE_ARROW.get(), c -> new TexturedArrowRenderer<>(c, projectile("fire_arrow")));
        event.registerEntityRenderer(ESEntities.SLEEP_DART.get(), c -> new TexturedArrowRenderer<>(c, projectile("sleep_dart")));
    }

    @SubscribeEvent
    static void onRegisterPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(LockpickPayloads.Open.TYPE, ToolkitBClient::onLockpickOpen);
        event.register(LockpickPayloads.State.TYPE, ToolkitBClient::onLockpickState);
    }

    private static void onLockpickOpen(LockpickPayloads.Open payload, IPayloadContext context) {
        Minecraft.getInstance().setScreen(new LockpickScreen(payload.pos(), payload.difficulty(), payload.pins()));
    }

    private static void onLockpickState(LockpickPayloads.State payload, IPayloadContext context) {
        if (Minecraft.getInstance().screen instanceof LockpickScreen screen) {
            screen.onState(payload);
        }
    }

    /** Tagged NPCs get a glowing outline (drawn through walls), for the tagging player only. */
    @SubscribeEvent
    static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.<StealthNpc, NpcRenderState>registerEntityModifier(NpcRenderer.class, (npc, state) -> {
            if (state.outlineColor == 0 && isTagged(npc)) {
                state.outlineColor = TAG_OUTLINE;
            }
        });
    }

    static boolean isTagged(StealthNpc npc) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return false;
        }
        SpyglassTags tags = minecraft.player.getExistingDataOrNull(ESAttachments.SPYGLASS_TAGS);
        return tags != null && tags.isTagged(npc.getId(), minecraft.level.getGameTime());
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(SpyglassTagHud.LAYER_ID, new SpyglassTagHud());
    }
}
