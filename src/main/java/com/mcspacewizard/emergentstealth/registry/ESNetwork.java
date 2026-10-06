package com.mcspacewizard.emergentstealth.registry;

import com.mcspacewizard.emergentstealth.network.DetectionSyncPayload;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payload registration. Client-bound handlers are registered on the client (ESClientEvents). */
public final class ESNetwork {
    private ESNetwork() {}

    public static final String PROTOCOL_VERSION = "1";

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(DetectionSyncPayload.TYPE, DetectionSyncPayload.STREAM_CODEC);
    }
}
