package com.mcspacewizard.emergentstealth.registry;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.data.Archetype;
import com.mcspacewizard.emergentstealth.data.Outfit;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * Datapack registries. Files live at {@code data/<namespace>/emergentstealth/<registry>/<name>.json}.
 * Both are synced to clients because rendering needs them.
 */
public final class ESRegistries {
    private ESRegistries() {}

    public static final ResourceKey<Registry<Outfit>> OUTFIT = ResourceKey.createRegistryKey(EmergentStealth.id("outfit"));
    public static final ResourceKey<Registry<Archetype>> ARCHETYPE = ResourceKey.createRegistryKey(EmergentStealth.id("archetype"));

    public static void onNewDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(OUTFIT, Outfit.CODEC, Outfit.CODEC);
        event.dataPackRegistry(ARCHETYPE, Archetype.CODEC, Archetype.CODEC);
    }
}
