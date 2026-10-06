package com.mcspacewizard.emergentstealth.client.hud;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.ai.brain.AlertState;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.network.DetectionSyncPayload;
import com.mcspacewizard.emergentstealth.registry.ESSounds;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The local player's view of how aware nearby NPCs are of them. Fed by {@link DetectionSyncPayload}. */
public final class ClientDetection {
    private ClientDetection() {}

    /** One NPC's detection of the local player. */
    public record View(float awareness, AlertState state, boolean focusYou) {
        public boolean detected() {
            return focusYou && (state == AlertState.COMBAT || state == AlertState.HUNTING);
        }

        public boolean searching() {
            return focusYou && state == AlertState.SEARCHING;
        }
    }

    private static Int2ObjectMap<View> views = new Int2ObjectOpenHashMap<>();

    public static @Nullable View get(int entityId) {
        return views.get(entityId);
    }

    public static void handle(DetectionSyncPayload payload, IPayloadContext context) {
        Int2ObjectMap<View> next = new Int2ObjectOpenHashMap<>();
        float suspicious = ESConfig.SUSPICIOUS_THRESHOLD.get().floatValue();
        SoundEvent stinger = null;
        for (DetectionSyncPayload.Entry entry : payload.entries()) {
            View view = new View(entry.awareness(), AlertState.byId(entry.state()), entry.focusYou());
            View previous = views.get(entry.entityId());
            if (view.detected() && (previous == null || !previous.detected())) {
                stinger = ESSounds.STINGER_DETECTED.get();
            } else if (stinger == null && view.awareness() >= suspicious
                    && (previous == null || previous.awareness() < suspicious) && !view.detected()) {
                stinger = ESSounds.STINGER_SUSPICIOUS.get();
            }
            next.put(entry.entityId(), view);
        }
        views = next;
        if (stinger != null && ESConfig.PLAY_ALERT_SOUNDS.get()) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(stinger, 1.0F, 1.0F));
        }
    }

    public static void clear() {
        views = new Int2ObjectOpenHashMap<>();
    }
}
