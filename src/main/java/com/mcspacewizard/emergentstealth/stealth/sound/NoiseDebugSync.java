package com.mcspacewizard.emergentstealth.stealth.sound;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mcspacewizard.emergentstealth.network.NoiseDebugPayload;
import com.mcspacewizard.emergentstealth.registry.ESDebugSubscriptions;
import com.mcspacewizard.emergentstealth.registry.ESNetwork;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends resolved noises to players with the AI debug view on (design doc 16 §6). Rides on the
 * {@link ESDebugSubscriptions#NPC} subscription, which vanilla only honours for ops and the singleplayer host,
 * so normal players never get noise rings (S-06). Batched once per tick.
 */
public final class NoiseDebugSync {
    private NoiseDebugSync() {}

    /** Players this far from a noise get it. */
    private static final double RANGE = 96.0;
    private static final int MAX_PER_TICK = 256;
    private static final int MAX_HEARD = 64;

    private static final Map<ServerLevel, List<NoiseDebugPayload.Noise>> PENDING = new WeakHashMap<>();

    static void record(ServerLevel level, NoiseEvent noise, List<Noises.Hearer> hearers) {
        if (!anyWatcher(level)) {
            return;
        }
        List<NoiseDebugPayload.Noise> pending = PENDING.computeIfAbsent(level, l -> new ArrayList<>());
        if (pending.size() >= MAX_PER_TICK) {
            return;
        }
        List<NoiseDebugPayload.Heard> heard = new ArrayList<>(Math.min(hearers.size(), MAX_HEARD));
        for (Noises.Hearer hearer : hearers) {
            if (heard.size() == MAX_HEARD) {
                break;
            }
            heard.add(new NoiseDebugPayload.Heard(hearer.npc().getEyePosition(), hearer.heard().intensity(), hearer.heard().cost()));
        }
        pending.add(new NoiseDebugPayload.Noise(noise.pos(), noise.loudness(), noise.kind().getSerializedName(),
                noise.cause() != null, heard));
    }

    static void flush(ServerLevel level) {
        List<NoiseDebugPayload.Noise> pending = PENDING.remove(level);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (!watching(player)) {
                continue;
            }
            List<NoiseDebugPayload.Noise> near = new ArrayList<>();
            for (NoiseDebugPayload.Noise noise : pending) {
                if (noise.pos().distanceToSqr(player.position()) <= RANGE * RANGE) {
                    near.add(noise);
                }
            }
            if (!near.isEmpty()) {
                ESNetwork.sendIfSupported(player, new NoiseDebugPayload(near));
            }
        }
    }

    private static boolean anyWatcher(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (watching(player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean watching(ServerPlayer player) {
        return player.debugSubscriptions().contains(ESDebugSubscriptions.NPC.get());
    }
}
