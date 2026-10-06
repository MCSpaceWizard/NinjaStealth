package com.mcspacewizard.emergentstealth.registry;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.debug.NpcDebugInfo;

import net.minecraft.core.registries.Registries;
import net.minecraft.util.debug.DebugSubscription;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Vanilla debug subscriptions. The server only sends these to operators or the singleplayer host,
 * and only while the client requests them (see the client's debug toggle).
 */
public final class ESDebugSubscriptions {
    private ESDebugSubscriptions() {}

    public static final DeferredRegister<DebugSubscription<?>> DEBUG_SUBSCRIPTIONS =
            DeferredRegister.create(Registries.DEBUG_SUBSCRIPTION, EmergentStealth.MODID);

    /** Per-NPC snapshot. Expiry 0: values live as long as the entity is tracked. */
    public static final DeferredHolder<DebugSubscription<?>, DebugSubscription<NpcDebugInfo>> NPC =
            DEBUG_SUBSCRIPTIONS.register("npc", () -> new DebugSubscription<>(NpcDebugInfo.STREAM_CODEC, 0));
}
