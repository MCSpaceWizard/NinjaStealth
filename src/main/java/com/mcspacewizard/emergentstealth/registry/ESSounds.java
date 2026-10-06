package com.mcspacewizard.emergentstealth.registry;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Sound events. Placeholder sounds map to vanilla events in assets/emergentstealth/sounds.json. */
public final class ESSounds {
    private ESSounds() {}

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, EmergentStealth.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> STINGER_SUSPICIOUS = register("stinger.suspicious");
    public static final DeferredHolder<SoundEvent, SoundEvent> STINGER_DETECTED = register("stinger.detected");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(EmergentStealth.id(name)));
    }
}
