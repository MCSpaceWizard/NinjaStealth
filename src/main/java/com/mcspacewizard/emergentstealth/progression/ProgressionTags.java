package com.mcspacewizard.emergentstealth.progression;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ProgressionTags {
    private ProgressionTags() {}

    /** Armour that makes footsteps louder unless it has its own stealth stats. */
    public static final TagKey<Item> LOUD_ARMOR = TagKey.create(Registries.ITEM, EmergentStealth.id("loud_armor"));
}
