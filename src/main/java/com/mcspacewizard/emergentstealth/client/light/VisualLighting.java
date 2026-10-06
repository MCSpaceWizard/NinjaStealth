package com.mcspacewizard.emergentstealth.client.light;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.stealth.light.LightSourceIndex.Source;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Entry point of the visual lighting (Track E, doc 30): makes the block light the player sees follow the
 * server's shadow-casting exposure model, adds dynamic lights for carried lights, and owns the per-tick
 * bookkeeping. The mixins in {@code client.mixin} call in here.
 * <p>
 * <b>Threads:</b> {@link #adjustPackedLight} runs on chunk-meshing worker threads (vanilla and Sodium). The
 * terrain snapshots they mesh from only reach a block or two past the section, but light travels 15 blocks,
 * so rays and source scans read the live {@link ClientLevel}, exactly as vanilla meshing already reads the
 * live light engine. Chunk storage is an atomic array and block reads are snapshot reads; any exception
 * falls back to the vanilla value for that cell, and a block change re-bakes the affected sections anyway.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID, value = Dist.CLIENT)
public final class VisualLighting {
    private VisualLighting() {}

    private static volatile VisualSettings settings = VisualSettings.OFF;

    /** Which level views get our light: the client level and the meshing snapshots of vanilla and Sodium. */
    private static final ClassValue<Boolean> CLIENT_VIEW = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return ClientLevel.class.isAssignableFrom(type)
                    || RenderSectionRegion.class.isAssignableFrom(type)
                    || type.getName().equals("net.caffeinemc.mods.sodium.client.world.LevelSlice");
        }
    };

    static VisualSettings settings() {
        return settings;
    }

    // ------------------------------------------------------------------------------------------------
    // Hooks (called from mixins)

    /**
     * Replaces the block-light part of vanilla's packed brightness at {@code pos} with the shadowed + dynamic
     * value. Hooked into {@code LevelRenderer.BrightnessGetter.DEFAULT}, which vanilla meshing, Sodium
     * meshing, block entities and particles all go through.
     */
    public static int adjustPackedLight(BlockAndLightGetter view, BlockPos pos, int packed) {
        VisualSettings current = settings;
        if (!current.altersTerrain() || !CLIENT_VIEW.get(view.getClass())) {
            return packed;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return packed;
        }
        try {
            // Inside an opaque cube vanilla has 0; keep it, or smooth lighting would pull light through walls.
            if (view.getBlockState(pos).isSolidRender()) {
                return packed;
            }
            int vanilla = LightCoordsUtil.block(packed);
            int light = blockLight(level, pos, vanilla, current);
            return light == vanilla ? packed : LightCoordsUtil.withBlock(packed, light);
        } catch (RuntimeException e) {
            return packed;
        }
    }

    /** Block light for an entity's light probe ({@code EntityRenderer.getBlockLightLevel}). Main thread. */
    public static int entityBlockLight(Entity entity, BlockPos pos, int vanilla) {
        VisualSettings current = settings;
        if (!current.altersTerrain() || entity.isOnFire() || !(entity.level() instanceof ClientLevel level)) {
            return vanilla;
        }
        try {
            if (level.getBlockState(pos).isSolidRender()) {
                return vanilla;
            }
            return blockLight(level, pos, vanilla, current);
        } catch (RuntimeException e) {
            return vanilla;
        }
    }

    private static int blockLight(ClientLevel level, BlockPos pos, int vanilla, VisualSettings current) {
        int cell = ShadowedBlockLight.cell(level, pos, current.shadowedBlockLight(), current.dynamicLights());
        int model = cell & 0xF;
        if (!current.shadowedBlockLight() || (cell & ShadowedBlockLight.DENSE) != 0) {
            // Dynamic lights only (on top of vanilla), or too many static emitters to trace here.
            return Math.max(vanilla, model);
        }
        return Math.max(model, Math.round(vanilla * current.shadowBounce()));
    }

    /** {@code LevelRenderer.blockChanged}: a block can move shadows up to 15 blocks away. Main thread. */
    public static void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        int oldEmission = oldState.getLightEmission();
        int newEmission = newState.getLightEmission();
        if (oldEmission != newEmission) {
            ClientLightSources.invalidate(pos);
        }
        VisualSettings current = settings;
        if (!current.altersTerrain()) {
            return;
        }
        boolean relevant = false;
        if (oldEmission != newEmission && current.shadowedBlockLight()) {
            SectionRebuilds.markSphere(Vec3.atCenterOf(pos), Math.max(oldEmission, newEmission));
            relevant = true;
        }
        if (current.shadowedBlockLight()) {
            for (Source source : ClientLightSources.reaching(level, pos)) {
                SectionRebuilds.markSphere(Vec3.atCenterOf(source.pos()), source.emission());
                relevant = true;
            }
        }
        if (current.dynamicLights()) {
            Vec3 center = Vec3.atCenterOf(pos);
            for (ClientDynamicLights.Light light : ClientDynamicLights.snapshot().lights()) {
                if (light.pos().distanceToSqr(center) < (double) light.emission() * light.emission()) {
                    SectionRebuilds.markSphere(light.pos(), light.emission());
                    relevant = true;
                }
            }
        }
        if (relevant || oldEmission != newEmission) {
            // Cached cell values may have traced through this block.
            ClientLightSources.bumpGeneration();
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Events

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        VisualSettings previous = settings;
        VisualSettings current = VisualSettings.read();
        if (!current.equals(previous)) {
            settings = current;
            if (current.terrainDiffers(previous) && minecraft.level != null) {
                ClientLightSources.bumpGeneration();
                minecraft.levelRenderer.allChanged();
            }
        }
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Entity camera = minecraft.getCameraEntity();
        Vec3 cameraPos = camera != null ? camera.position() : minecraft.gameRenderer.getMainCamera().position();
        ClientDynamicLights.tick(level, cameraPos, current);
        SectionRebuilds.flush(minecraft);
    }

    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ClientLightSources.invalidateChunk(level, event.getChunk().getPos().x(), event.getChunk().getPos().z());
        }
    }

    @SubscribeEvent
    static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ClientLightSources.invalidateChunk(level, event.getChunk().getPos().x(), event.getChunk().getPos().z());
        }
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDynamicLights.clear();
        SectionRebuilds.clear();
        ClientLightSources.reset(null);
    }
}
