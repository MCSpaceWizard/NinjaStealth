package com.mcspacewizard.emergentstealth.ai.perception;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Datapack registry entry {@code emergentstealth:perception_profile}: how an NPC sees.
 * There is deliberately no rear cone: anything outside the peripheral cone is invisible to sight (D-02).
 */
public record PerceptionProfile(
        Cone central,
        Cone peripheral,
        float peripheralRate,
        float verticalHalfAngle,
        float gainPerSecond,
        float decayPerSecond,
        float decayDelaySeconds,
        float minDetectionSeconds) {

    /** Fallback when a profile is missing. Matches data/emergentstealth/emergentstealth/perception_profile/default.json. */
    public static final PerceptionProfile DEFAULT = new PerceptionProfile(
            new Cone(30, 16), new Cone(70, 8), 0.3F, 50, 1.0F, 0.3F, 3.0F, 0.8F);

    public static final Codec<PerceptionProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Cone.CODEC.fieldOf("central").forGetter(PerceptionProfile::central),
            Cone.CODEC.fieldOf("peripheral").forGetter(PerceptionProfile::peripheral),
            Codec.floatRange(0, 1).optionalFieldOf("peripheral_rate", 0.3F).forGetter(PerceptionProfile::peripheralRate),
            Codec.floatRange(1, 90).optionalFieldOf("vertical_half_angle", 50F).forGetter(PerceptionProfile::verticalHalfAngle),
            Codec.floatRange(0, 100).optionalFieldOf("gain_per_second", 1.6F).forGetter(PerceptionProfile::gainPerSecond),
            Codec.floatRange(0, 100).optionalFieldOf("decay_per_second", 0.25F).forGetter(PerceptionProfile::decayPerSecond),
            Codec.floatRange(0, 600).optionalFieldOf("decay_delay_seconds", 3.0F).forGetter(PerceptionProfile::decayDelaySeconds),
            Codec.floatRange(0.05F, 60).optionalFieldOf("min_detection_seconds", 0.6F).forGetter(PerceptionProfile::minDetectionSeconds)
    ).apply(i, PerceptionProfile::new));

    /** Furthest anything can be seen by this profile. */
    public float maxRange() {
        return Math.max(central.range(), peripheral.range());
    }

    /** A vision cone: half-angle in degrees (horizontal), range in blocks. */
    public record Cone(float halfAngle, float range) {
        public static final Codec<Cone> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 180).fieldOf("half_angle").forGetter(Cone::halfAngle),
                Codec.floatRange(0, 256).fieldOf("range").forGetter(Cone::range)
        ).apply(i, Cone::new));
    }
}
