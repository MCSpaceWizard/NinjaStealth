package com.mcspacewizard.emergentstealth.progression;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Stealth stats on worn gear (design doc 26 §4): data component {@code emergentstealth:stealth_stats}, e.g.
 * {@code {"visibility": 0.9, "footstep_loudness": 0.8}}. Vanilla metal armour without the component is louder.
 */
public record GearStats(Map<StealthStat, Float> values) {
    public static final Codec<GearStats> CODEC = Codec.unboundedMap(StealthStat.CODEC, Codec.FLOAT).xmap(GearStats::new, GearStats::values);
    public static final StreamCodec<RegistryFriendlyByteBuf, GearStats> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** Heavy metal armour clanks: footsteps ×1.2 per piece (tag {@code emergentstealth:loud_armor}). */
    private static final GearStats LOUD_ARMOR = new GearStats(Map.of(StealthStat.FOOTSTEP_LOUDNESS, 1.2F));

    public static @Nullable GearStats defaultFor(ItemStack stack) {
        return !stack.isEmpty() && stack.is(com.mcspacewizard.emergentstealth.progression.ProgressionTags.LOUD_ARMOR) ? LOUD_ARMOR : null;
    }
}
