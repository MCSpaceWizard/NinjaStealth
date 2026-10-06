package com.mcspacewizard.emergentstealth.registry;

import com.mcspacewizard.emergentstealth.network.DetectionSyncPayload;
import com.mcspacewizard.emergentstealth.network.LightDebugPayload;
import com.mcspacewizard.emergentstealth.network.LightGemPayload;
import com.mcspacewizard.emergentstealth.network.RouteSyncPayload;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payload registration. Client-bound handlers are registered on the client (ESClientEvents). */
public final class ESNetwork {
    private ESNetwork() {}

    public static final String PROTOCOL_VERSION = "1";

    /**
     * Sends a payload to a player only if their connection negotiated our channel. A player without the mod
     * (or a GameTest player) would otherwise throw.
     */
    public static void sendIfSupported(net.minecraft.server.level.ServerPlayer player,
                                       net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(DetectionSyncPayload.TYPE, DetectionSyncPayload.STREAM_CODEC);
        registrar.playToClient(LightGemPayload.TYPE, LightGemPayload.STREAM_CODEC);
        registrar.playToClient(LightDebugPayload.TYPE, LightDebugPayload.STREAM_CODEC);
        registrar.playToClient(RouteSyncPayload.TYPE, RouteSyncPayload.STREAM_CODEC);
        registrar.playToClient(com.mcspacewizard.emergentstealth.network.BarkPayload.TYPE,
                com.mcspacewizard.emergentstealth.network.BarkPayload.STREAM_CODEC);
    }
}
