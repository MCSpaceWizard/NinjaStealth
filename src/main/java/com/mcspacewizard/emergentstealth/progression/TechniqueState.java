package com.mcspacewizard.emergentstealth.progression;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** Technique cooldowns and active effects (game-time timestamps). Saved; synced to its owner for the HUD. */
public record TechniqueState(Map<Identifier, Long> cooldownUntil, Map<Identifier, Long> activeUntil) {
    public static final TechniqueState EMPTY = new TechniqueState(Map.of(), Map.of());

    private static final Codec<Map<Identifier, Long>> TIMES = Codec.unboundedMap(Identifier.CODEC, Codec.LONG);
    public static final Codec<TechniqueState> CODEC = RecordCodecBuilder.create(i -> i.group(
            TIMES.optionalFieldOf("cooldowns", Map.of()).forGetter(TechniqueState::cooldownUntil),
            TIMES.optionalFieldOf("active", Map.of()).forGetter(TechniqueState::activeUntil)
    ).apply(i, TechniqueState::new));

    private static final StreamCodec<ByteBuf, Map<Identifier, Long>> TIMES_STREAM =
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_LONG);
    public static final StreamCodec<ByteBuf, TechniqueState> STREAM_CODEC = StreamCodec.composite(
            TIMES_STREAM, TechniqueState::cooldownUntil,
            TIMES_STREAM, TechniqueState::activeUntil,
            TechniqueState::new);

    public boolean active(Identifier technique, long now) {
        return now < activeUntil.getOrDefault(technique, Long.MIN_VALUE);
    }

    public long cooldownLeft(Identifier technique, long now) {
        Long until = cooldownUntil.get(technique);
        return until == null ? 0L : Math.max(0L, until - now);
    }

    public TechniqueState used(Identifier technique, long now, long activeTicks, long cooldownTicks) {
        Map<Identifier, Long> cooldowns = new HashMap<>(cooldownUntil);
        cooldowns.put(technique, now + cooldownTicks);
        Map<Identifier, Long> active = new HashMap<>(activeUntil);
        if (activeTicks > 0) {
            active.put(technique, now + activeTicks);
        }
        return new TechniqueState(cooldowns, active);
    }
}
