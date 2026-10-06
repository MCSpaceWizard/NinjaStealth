package com.mcspacewizard.emergentstealth.stealth;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Block tags that tune stealth behaviour. Packs can retune any block (including other mods') with these. */
public final class StealthTags {
    private StealthTags() {}

    /** Fully transparent to sight even though solid (glass, panes). */
    public static final TagKey<Block> SEE_THROUGH = block("see_through");
    /** Lets roughly a third of sight through (leaves, fences, bars). */
    public static final TagKey<Block> PARTIAL_COVER = block("partial_cover");
    /** Lets most sight through (chains, cobwebs). */
    public static final TagKey<Block> LIGHT_COVER = block("light_cover");
    /** Blocks sight completely, but only to a crawling target inside or behind it (tall grass, crops). */
    public static final TagKey<Block> CONCEALING_FOLIAGE = block("concealing_foliage");

    private static TagKey<Block> block(String name) {
        return TagKey.create(Registries.BLOCK, EmergentStealth.id(name));
    }
}
