package com.mcspacewizard.emergentstealth.data;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * Datapack registry entry {@code emergentstealth:outfit}: what an NPC (and later a disguise) looks like.
 *
 * @param layers         64x64 player-skin-layout textures drawn in order over the body texture
 * @param disguiseGroup  reserved for social stealth (S11): enforcers recognise colleagues by group
 */
public record Outfit(List<Identifier> layers, Optional<Identifier> disguiseGroup) {
    public static final Codec<Outfit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.listOf().optionalFieldOf("layers", List.of()).forGetter(Outfit::layers),
            Identifier.CODEC.optionalFieldOf("disguise_group").forGetter(Outfit::disguiseGroup)
    ).apply(instance, Outfit::new));
}
