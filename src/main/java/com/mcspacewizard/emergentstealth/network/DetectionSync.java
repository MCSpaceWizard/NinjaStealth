package com.mcspacewizard.emergentstealth.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.mcspacewizard.emergentstealth.ai.brain.AlertState;
import com.mcspacewizard.emergentstealth.ai.perception.TargetAwareness;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Builds and sends {@link DetectionSyncPayload}s. */
public final class DetectionSync {
    private DetectionSync() {}

    /** Indicators are only shown for NPCs this close. */
    private static final double RANGE = 64.0;
    /** Players that got a non-empty update last time, so they get one empty update to clear indicators. */
    private static final Map<ServerPlayer, Boolean> HAD_ENTRIES = new WeakHashMap<>();

    public static void sendToPlayers(ServerLevel level, Collection<StealthNpc> npcs, List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            List<DetectionSyncPayload.Entry> entries = new ArrayList<>();
            for (StealthNpc npc : npcs) {
                if (!npc.isAlive() || npc.distanceToSqr(player) > RANGE * RANGE) {
                    continue;
                }
                TargetAwareness awareness = npc.perception().get(id);
                float value = awareness == null ? 0.0F : awareness.awareness();
                UUID focus = npc.stealthBrain().alertTarget() != null ? npc.stealthBrain().alertTarget() : npc.perception().focus();
                boolean focusYou = id.equals(focus);
                AlertState state = npc.stealthBrain().state();
                if (value > 0.005F || (focusYou && state.isActive())) {
                    entries.add(new DetectionSyncPayload.Entry(npc.getId(), value, state.ordinal(), focusYou));
                }
            }
            boolean had = HAD_ENTRIES.getOrDefault(player, false);
            if (!entries.isEmpty() || had) {
                com.mcspacewizard.emergentstealth.registry.ESNetwork.sendIfSupported(player, new DetectionSyncPayload(entries));
            }
            HAD_ENTRIES.put(player, !entries.isEmpty());
        }
    }
}
