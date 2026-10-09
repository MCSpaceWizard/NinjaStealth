package com.mcspacewizard.emergentstealth.authoring;

import java.util.List;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.registry.ESNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Server side of the structure viewer (design doc 32 §1): answers the browser's requests and places or undoes
 * structures. Everything needs creative mode and game-master permission, like a structure block.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class StructureViewer {
    private StructureViewer() {}

    /** How far from the author a structure may be placed, in blocks. */
    public static final double MAX_PLACE_DISTANCE = 256.0;

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ESNetwork.PROTOCOL_VERSION);
        registrar.playToServer(StructurePayloads.RequestList.TYPE, StructurePayloads.RequestList.STREAM_CODEC, StructureViewer::handleList);
        registrar.playToServer(StructurePayloads.RequestPreview.TYPE, StructurePayloads.RequestPreview.STREAM_CODEC, StructureViewer::handlePreview);
        registrar.playToServer(StructurePayloads.Place.TYPE, StructurePayloads.Place.STREAM_CODEC, StructureViewer::handlePlace);
        registrar.playToServer(StructurePayloads.Undo.TYPE, StructurePayloads.Undo.STREAM_CODEC, StructureViewer::handleUndo);
        registrar.playToClient(StructurePayloads.StructureList.TYPE, StructurePayloads.StructureList.STREAM_CODEC);
        registrar.playToClient(StructurePayloads.PreviewPart.TYPE, StructurePayloads.PreviewPart.STREAM_CODEC);
    }

    /** Creative and game-master permission (the structure block rule). Tells the player why not. */
    public static boolean mayAuthor(ServerPlayer player) {
        if (player.canUseGameMasterBlocks()) {
            return true;
        }
        player.sendSystemMessage(Component.translatable("message.emergentstealth.authoring.not_allowed"));
        return false;
    }

    /** Sends the structure list; {@code open} opens the browser when it arrives. */
    public static void sendList(ServerPlayer player, boolean open) {
        List<net.minecraft.resources.Identifier> ids = StructureCatalog.ids(player.level().getServer());
        ESNetwork.sendIfSupported(player, new StructurePayloads.StructureList(ids, open));
    }

    static void handleList(StructurePayloads.RequestList payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && mayAuthor(player)) {
            sendList(player, true);
        }
    }

    static void handlePreview(StructurePayloads.RequestPreview payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !mayAuthor(player)) {
            return;
        }
        byte[] bytes = StructureCatalog.previewBytes(player.level().getServer(), payload.id());
        if (bytes == null) {
            player.sendSystemMessage(Component.translatable("message.emergentstealth.structure.unknown", payload.id().toString()));
            return;
        }
        List<byte[]> parts = StructureCatalog.split(bytes, StructureCatalog.PART_BYTES);
        for (int i = 0; i < parts.size(); i++) {
            ESNetwork.sendIfSupported(player, new StructurePayloads.PreviewPart(payload.id(), i, parts.size(), parts.get(i)));
        }
    }

    static void handlePlace(StructurePayloads.Place payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !mayAuthor(player)) {
            return;
        }
        BlockPos origin = payload.origin();
        if (player.position().distanceTo(origin.getCenter()) > MAX_PLACE_DISTANCE) {
            return;
        }
        BoundingBox box = StructurePlacement.place(player.level(), player.getUUID(), payload.id(), origin, payload.rotation(), payload.mirror());
        if (box == null) {
            player.sendSystemMessage(Component.translatable("message.emergentstealth.structure.unknown", payload.id().toString()));
            return;
        }
        player.sendSystemMessage(Component.translatable("message.emergentstealth.structure.placed", payload.id().toString(),
                box.minX(), box.minY(), box.minZ()));
    }

    static void handleUndo(StructurePayloads.Undo payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && mayAuthor(player)) {
            undo(player);
        }
    }

    public static void undo(ServerPlayer player) {
        BoundingBox box = StructurePlacement.undo(player.getUUID());
        player.sendSystemMessage(box == null ? Component.translatable("message.emergentstealth.structure.nothing_to_undo")
                : Component.translatable("message.emergentstealth.structure.undone", box.minX(), box.minY(), box.minZ()));
    }

    /** Datapacks reloaded: templates may have changed. */
    @SubscribeEvent
    static void onDatapackSync(OnDatapackSyncEvent event) {
        StructureCatalog.clearCache();
    }
}
