package com.mcspacewizard.emergentstealth.stealth.sound;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Block and item tags that tune hearing (design doc 16). Packs can retune any block or item with these. */
public final class SoundTags {
    private SoundTags() {}

    /** Solid blocks that let sound through fairly well: glass, panes, doors, trapdoors, leaves, fences (+3). */
    public static final TagKey<Block> MUFFLES_SOUND_LIGHT = block("muffles_sound_light");
    /** Dampening blocks: wool, hay, moss... (+16). */
    public static final TagKey<Block> MUFFLES_SOUND_HEAVY = block("muffles_sound_heavy");
    /** Footsteps on these are 25% louder (gravel, metal, glass...). */
    public static final TagKey<Block> LOUD_SURFACES = block("loud_surfaces");
    /** Footsteps on these are 20% quieter (wool, carpet, moss, snow, grass...). */
    public static final TagKey<Block> QUIET_SURFACES = block("quiet_surfaces");

    /** Thrown, these shatter on impact and leave nothing behind (glass, panes, bottles). */
    public static final TagKey<Item> SHATTERS_ON_IMPACT = item("shatters_on_impact");
    /** Thrown, these deal 1 damage to a mob they hit (swords, axes, shears). */
    public static final TagKey<Item> SHARP_THROWABLES = item("sharp_throwables");

    private static TagKey<Block> block(String name) {
        return TagKey.create(Registries.BLOCK, EmergentStealth.id(name));
    }

    private static TagKey<Item> item(String name) {
        return TagKey.create(Registries.ITEM, EmergentStealth.id(name));
    }
}
