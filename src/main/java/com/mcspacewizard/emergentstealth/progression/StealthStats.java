package com.mcspacewizard.emergentstealth.progression;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.mcspacewizard.emergentstealth.registry.ESDataComponents;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The single place stealth numbers come from (design doc 26 §1): base × skills × gear (× disguise later).
 * Results are cached per player per tick.
 */
public final class StealthStats {
    private StealthStats() {}

    private record Cached(long tick, Map<StealthStat, Float> values) {}

    private static final Map<Player, Cached> CACHE = new WeakHashMap<>();

    public static float get(Player player, StealthStat stat) {
        long tick = player.level().getGameTime();
        Cached cached = CACHE.get(player);
        if (cached == null || cached.tick() != tick) {
            cached = new Cached(tick, compute(player));
            CACHE.put(player, cached);
        }
        return cached.values().getOrDefault(stat, stat.base());
    }

    public static void invalidate(Player player) {
        CACHE.remove(player);
    }

    private static Map<StealthStat, Float> compute(Player player) {
        Map<StealthStat, Float> values = new EnumMap<>(StealthStat.class);
        for (StealthStat stat : StealthStat.values()) {
            values.put(stat, stat.base());
        }
        Registry<SkillDefinition> skills = Skills.registry(player);
        for (Identifier id : Skills.progression(player).unlocked()) {
            SkillDefinition skill = skills.getValue(id);
            if (skill == null) {
                continue;
            }
            for (SkillEffect effect : skill.effects()) {
                if (effect instanceof SkillEffect.StatModifier modifier) {
                    apply(values, modifier.stat(), modifier.value());
                }
            }
        }
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (!slot.isArmor()) {
                continue;
            }
            ItemStack stack = player.getItemBySlot(slot);
            GearStats gear = stack.get(ESDataComponents.STEALTH_STATS.get());
            if (gear == null) {
                gear = GearStats.defaultFor(stack);
            }
            if (gear != null) {
                gear.values().forEach((stat, value) -> apply(values, stat, value));
            }
        }
        // Active techniques (design doc 26 §3).
        if (Techniques.stillBreathing(player)) {
            apply(values, StealthStat.VISIBILITY, Techniques.STILL_BREATH_VISIBILITY);
        }
        if (Techniques.active(player, Techniques.LIGHT_STEP)) {
            apply(values, StealthStat.FOOTSTEP_LOUDNESS, 0.0F);
        }
        return values;
    }

    private static void apply(Map<StealthStat, Float> values, StealthStat stat, float value) {
        values.put(stat, stat.additive() ? values.get(stat) + value : values.get(stat) * value);
    }
}
