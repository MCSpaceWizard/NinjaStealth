package com.mcspacewizard.emergentstealth.registry;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.perception.PerceptionProfile;
import com.mcspacewizard.emergentstealth.data.Archetype;
import com.mcspacewizard.emergentstealth.data.Outfit;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseSource;

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
    public static final ResourceKey<Registry<com.mcspacewizard.emergentstealth.ai.behaviour.BehaviourTree>> BEHAVIOUR =
            ResourceKey.createRegistryKey(EmergentStealth.id("behaviour"));
    public static final ResourceKey<Registry<com.mcspacewizard.emergentstealth.action.TakedownDefinition>> TAKEDOWN =
            ResourceKey.createRegistryKey(EmergentStealth.id("takedown"));
    public static final ResourceKey<Registry<com.mcspacewizard.emergentstealth.progression.SkillDefinition>> SKILL =
            ResourceKey.createRegistryKey(EmergentStealth.id("skill"));
    public static final ResourceKey<Registry<PerceptionProfile>> PERCEPTION_PROFILE = ResourceKey.createRegistryKey(EmergentStealth.id("perception_profile"));
    /** Vanilla game event → noise mapping (design doc 16 §1). Server-only. */
    public static final ResourceKey<Registry<NoiseSource>> NOISE_SOURCE = ResourceKey.createRegistryKey(EmergentStealth.id("noise_source"));

    public static void onNewDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(OUTFIT, Outfit.CODEC, Outfit.CODEC);
        event.dataPackRegistry(ARCHETYPE, Archetype.CODEC, Archetype.CODEC);
        event.dataPackRegistry(PERCEPTION_PROFILE, PerceptionProfile.CODEC);
        // Synced: the skill tree screen needs it.
        event.dataPackRegistry(SKILL, com.mcspacewizard.emergentstealth.progression.SkillDefinition.CODEC,
                com.mcspacewizard.emergentstealth.progression.SkillDefinition.CODEC);
        event.dataPackRegistry(TAKEDOWN, com.mcspacewizard.emergentstealth.action.TakedownDefinition.CODEC);
        event.dataPackRegistry(BEHAVIOUR, com.mcspacewizard.emergentstealth.ai.behaviour.BehaviourTree.CODEC);
        event.dataPackRegistry(NOISE_SOURCE, NoiseSource.CODEC);
    }
}
