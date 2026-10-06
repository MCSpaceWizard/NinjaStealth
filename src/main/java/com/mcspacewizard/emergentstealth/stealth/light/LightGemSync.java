package com.mcspacewizard.emergentstealth.stealth.light;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mcspacewizard.emergentstealth.network.LightGemPayload;
import com.mcspacewizard.emergentstealth.stealth.BodySample;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends each player their own exposure for the light gem (design doc 13 §2). */
public final class LightGemSync {
    private LightGemSync() {}

    private static final int INTERVAL = 5;
    private static final float MIN_CHANGE = 0.01F;
    private static final Map<ServerPlayer, Float> LAST_SENT = new WeakHashMap<>();

    public static void tick(ServerLevel level, List<ServerPlayer> players, long now) {
        if (now % INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : players) {
            if (player.isSpectator()) {
                continue;
            }
            float exposure = playerExposure(level, player);
            Float last = LAST_SENT.get(player);
            if (last == null || Math.abs(last - exposure) >= MIN_CHANGE) {
                LAST_SENT.put(player, exposure);
                PacketDistributor.sendToPlayer(player, new LightGemPayload(exposure));
            }
        }
    }

    /** Body-weighted exposure: the same sample points observers look at. */
    public static float playerExposure(ServerLevel level, ServerPlayer player) {
        float total = 0.0F;
        float weights = 0.0F;
        for (BodySample sample : BodySample.of(player, true)) {
            total += sample.weight() * ExposureModel.INSTANCE.exposure(level, sample.position());
            weights += sample.weight();
        }
        return weights > 0.0F ? total / weights : 0.0F;
    }
}
