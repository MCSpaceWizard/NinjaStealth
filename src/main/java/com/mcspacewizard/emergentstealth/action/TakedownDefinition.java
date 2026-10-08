package com.mcspacewizard.emergentstealth.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.StringRepresentable;

/**
 * A takedown (design doc 17 §3), from the datapack registry {@code emergentstealth/takedown}. The entry
 * {@code ns:name} plays the action {@code ns:takedown/name} on both participants.
 *
 * @param kind           rear (from behind) or air (landing on the victim)
 * @param lethal         corpse or knocked out
 * @param duration       ticks both are locked in the action
 * @param impactTick     tick (from the start) when the outcome happens
 * @param noise          loudness of the struggle at the impact (S6)
 * @param attackerOffset how far behind the victim the attacker is placed
 */
public record TakedownDefinition(Kind kind, boolean lethal, int duration, int impactTick, float noise, float attackerOffset) {
    public enum Kind implements StringRepresentable {
        REAR("rear"),
        AIR("air");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final Codec<TakedownDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.fieldOf("kind").forGetter(TakedownDefinition::kind),
            Codec.BOOL.fieldOf("lethal").forGetter(TakedownDefinition::lethal),
            Codec.intRange(1, 400).fieldOf("duration").forGetter(TakedownDefinition::duration),
            Codec.intRange(0, 400).fieldOf("impact_tick").forGetter(TakedownDefinition::impactTick),
            Codec.floatRange(0, 64).optionalFieldOf("noise", 4.0F).forGetter(TakedownDefinition::noise),
            Codec.floatRange(0, 3).optionalFieldOf("attacker_offset", 0.8F).forGetter(TakedownDefinition::attackerOffset)
    ).apply(i, TakedownDefinition::new));
}
