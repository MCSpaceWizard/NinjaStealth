package com.mcspacewizard.emergentstealth.registry;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.perception.PerceptionProfile;
import com.mcspacewizard.emergentstealth.data.Archetype;
import com.mcspacewizard.emergentstealth.data.Outfit;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * Datapack registries. Files live at {@code data/<namespace>/emergentstealth/<registry>/<name>.json}.
 * Archetypes and outfits are synced to clients because rendering needs them; perception profiles are
 * server-only.
 */
public final class ESRegistries {
    private ESRegistries() {}

    public static final ResourceKey<Registry<Outfit>> OUTFIT = ResourceKey.createRegistryKey(EmergentStealth.id("outfit"));
    public static final ResourceKey<Registry<Archetype>> ARCHETYPE = ResourceKey.createRegistryKey(EmergentStealth.id("archetype"));
    public static final ResourceKey<Registry<PerceptionProfile>> PERCEPTION_PROFILE = ResourceKey.createRegistryKey(EmergentStealth.id("perception_profile"));

    public static void onNewDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(OUTFIT, Outfit.CODEC, Outfit.CODEC);
        event.dataPackRegistry(ARCHETYPE, Archetype.CODEC, Archetype.CODEC);
        event.dataPackRegistry(PERCEPTION_PROFILE, PerceptionProfile.CODEC);
    }
}
