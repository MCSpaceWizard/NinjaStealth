package com.mcspacewizard.emergentstealth.stealth.sound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * Datapack registry entry {@code emergentstealth:noise_source} (design doc 16 §1): turns a vanilla game event
 * into a noise. Files live at {@code data/<namespace>/emergentstealth/noise_source/<name>.json}.
 *
 * @param gameEvent    the vanilla game event id, e.g. {@code minecraft:block_open}
 * @param loudness     how far it carries in open air, in blocks (0 = silent; useful to mute a default)
 * @param kind         what NPCs think made it
 * @param attributable whether it gives away the player who caused it (doors, combat) or only points at a spot
 * @param fromNpcs     whether the event also counts when a stealth NPC caused it. Off by default so guards
 *                     don't get curious about each other opening doors; on for combat
 */
public record NoiseSource(Identifier gameEvent, float loudness, NoiseKind kind, boolean attributable, boolean fromNpcs) {
    public static final Codec<NoiseSource> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("game_event").forGetter(NoiseSource::gameEvent),
            Codec.floatRange(0, 256).fieldOf("loudness").forGetter(NoiseSource::loudness),
            NoiseKind.CODEC.optionalFieldOf("kind", NoiseKind.OTHER).forGetter(NoiseSource::kind),
            Codec.BOOL.optionalFieldOf("attributable", false).forGetter(NoiseSource::attributable),
            Codec.BOOL.optionalFieldOf("from_npcs", false).forGetter(NoiseSource::fromNpcs)
    ).apply(i, NoiseSource::new));
}
