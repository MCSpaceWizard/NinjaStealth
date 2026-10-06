package com.mcspacewizard.emergentstealth.client;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.client.debug.ClientDebugState;
import com.mcspacewizard.emergentstealth.client.debug.LightDebugRenderer;
import com.mcspacewizard.emergentstealth.client.debug.NpcDebugRenderer;
import com.mcspacewizard.emergentstealth.client.debug.RouteRenderer;
import com.mcspacewizard.emergentstealth.client.hud.ClientDetection;
import com.mcspacewizard.emergentstealth.client.hud.LightGemHud;
import com.mcspacewizard.emergentstealth.client.render.ESModelLayers;
import com.mcspacewizard.emergentstealth.client.render.NpcRenderer;
import com.mcspacewizard.emergentstealth.registry.ESDebugSubscriptions;
import com.mcspacewizard.emergentstealth.registry.ESEntities;
import com.mcspacewizard.emergentstealth.network.DetectionSyncPayload;
import com.mcspacewizard.emergentstealth.network.LightDebugPayload;
import com.mcspacewizard.emergentstealth.network.LightGemPayload;
import com.mcspacewizard.emergentstealth.network.RouteSyncPayload;
import com.mcspacewizard.emergentstealth.registry.ESItems;

import net.minecraft.commands.Commands;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddDebugSubscriptionFlagsEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterDebugRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Client-only event handlers (both mod-bus and game-bus events; the bus is picked per event type). */
@EventBusSubscriber(modid = EmergentStealth.MODID, value = Dist.CLIENT)
public final class ESClientEvents {
    private ESClientEvents() {}

    // --- Mod bus ---

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ESEntities.STEALTH_NPC.get(), NpcRenderer::new);
    }

    @SubscribeEvent
    static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        ESModelLayers.onRegisterLayerDefinitions(event);
    }

    @SubscribeEvent
    static void onRegisterDebugRenderers(RegisterDebugRenderersEvent event) {
        event.register(new NpcDebugRenderer());
        event.register(new LightDebugRenderer());
        event.register(new RouteRenderer());
    }

    @SubscribeEvent
    static void onRegisterClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(DetectionSyncPayload.TYPE, ClientDetection::handle);
        event.register(LightGemPayload.TYPE, LightGemHud::handle);
        event.register(LightDebugPayload.TYPE, LightDebugRenderer::handle);
        event.register(RouteSyncPayload.TYPE, RouteRenderer::handle);
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, LightGemHud.LAYER_ID, new LightGemHud());
    }

    // --- Game bus ---

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDetection.clear();
        LightGemHud.reset();
        LightDebugRenderer.clear();
        RouteRenderer.clear();
    }

    /** Request our debug data from the server only while the debug view is on. */
    @SubscribeEvent
    static void onAddDebugSubscriptionFlags(AddDebugSubscriptionFlagsEvent event) {
        event.addFlag(ESDebugSubscriptions.NPC.get(), ClientDebugState.isEnabled());
        event.addFlag(ESDebugSubscriptions.LIGHT.get(), ClientDebugState.isLightEnabled());
    }

    /** {@code /esdebug [on|off]}: toggles the debug view. Client-side, so it works without op. */
    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("esdebug")
                .executes(ctx -> {
                    ClientDebugState.toggle();
                    return 1;
                })
                .then(Commands.literal("on").executes(ctx -> {
                    ClientDebugState.setEnabled(true);
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    ClientDebugState.setEnabled(false);
                    return 1;
                }))
                .then(Commands.literal("light")
                        .executes(ctx -> {
                            ClientDebugState.toggleLight();
                            return 1;
                        })
                        .then(Commands.literal("on").executes(ctx -> {
                            ClientDebugState.setLightEnabled(true);
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            ClientDebugState.setLightEnabled(false);
                            return 1;
                        }))));
    }

    /** Right-clicking the Debug Lens toggles the AI debug view; sneak + right-click toggles the light view. */
    @SubscribeEvent
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide() && event.getItemStack().is(ESItems.DEBUG_LENS.get())) {
            if (event.getEntity().isShiftKeyDown()) {
                ClientDebugState.toggleLight();
            } else {
                ClientDebugState.toggle();
            }
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
