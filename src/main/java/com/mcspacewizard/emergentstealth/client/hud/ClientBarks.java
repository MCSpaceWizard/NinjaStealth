package com.mcspacewizard.emergentstealth.client.hud;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.network.BarkPayload;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Barks currently shown above NPC heads (design doc 14 §6). */
public final class ClientBarks {
    private ClientBarks() {}

    private static final long SHOW_MILLIS = 3000;
    private static final int MAX_LINES = 32;

    private record Shown(Component text, long until) {}

    private static final Map<Integer, Shown> SHOWN = new HashMap<>();

    public static void handle(BarkPayload payload, IPayloadContext context) {
        Language language = Language.getInstance();
        String prefix = "bark.emergentstealth." + payload.situation() + ".";
        int count = 0;
        while (count < MAX_LINES && language.has(prefix + count)) {
            count++;
        }
        if (count == 0) {
            return;
        }
        String key = prefix + Math.floorMod(payload.seed(), count);
        SHOWN.put(payload.entityId(), new Shown(Component.translatable(key), Util.getMillis() + SHOW_MILLIS));
    }

    /** The bark to show above this entity right now, if any. */
    public static @Nullable Component get(int entityId) {
        Shown shown = SHOWN.get(entityId);
        if (shown == null) {
            return null;
        }
        if (Util.getMillis() > shown.until()) {
            SHOWN.remove(entityId);
            return null;
        }
        return shown.text();
    }

    public static void clear() {
        SHOWN.clear();
    }
}
