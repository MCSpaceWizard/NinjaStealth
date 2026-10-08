package com.mcspacewizard.emergentstealth.progression;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * A player's progression (design doc 26 §2): Insight and unspent points per path, unlocked skills, and the
 * selected technique. Immutable: change it with the {@code with...} methods and set the attachment again
 * (which syncs it to that player).
 */
public record PlayerProgression(Map<SkillPath, Integer> insight, Map<SkillPath, Integer> points, Set<Identifier> unlocked,
                                Identifier selectedTechnique) {
    /** Insight per skill point. */
    public static final int INSIGHT_PER_POINT = 100;
    public static final Identifier NO_TECHNIQUE = Identifier.withDefaultNamespace("empty");
    public static final PlayerProgression EMPTY = new PlayerProgression(Map.of(), Map.of(), Set.of(), NO_TECHNIQUE);

    private static final Codec<Map<SkillPath, Integer>> PER_PATH = Codec.unboundedMap(SkillPath.CODEC, Codec.INT);

    public static final Codec<PlayerProgression> CODEC = RecordCodecBuilder.create(i -> i.group(
            PER_PATH.optionalFieldOf("insight", Map.of()).forGetter(PlayerProgression::insight),
            PER_PATH.optionalFieldOf("points", Map.of()).forGetter(PlayerProgression::points),
            Identifier.CODEC.listOf().xmap(l -> (Set<Identifier>) new LinkedHashSet<>(l), s -> List.copyOf(s))
                    .optionalFieldOf("unlocked", Set.of()).forGetter(PlayerProgression::unlocked),
            Identifier.CODEC.optionalFieldOf("technique", NO_TECHNIQUE).forGetter(PlayerProgression::selectedTechnique)
    ).apply(i, PlayerProgression::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, Map<SkillPath, Integer>> PER_PATH_STREAM =
            ByteBufCodecs.map(i -> new EnumMap<>(SkillPath.class), SkillPath.STREAM_CODEC, ByteBufCodecs.VAR_INT);

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerProgression> STREAM_CODEC = StreamCodec.composite(
            PER_PATH_STREAM, PlayerProgression::insight,
            PER_PATH_STREAM, PlayerProgression::points,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()).map(l -> (Set<Identifier>) new LinkedHashSet<>(l), s -> List.copyOf(s)),
            PlayerProgression::unlocked,
            Identifier.STREAM_CODEC, PlayerProgression::selectedTechnique,
            PlayerProgression::new);

    public int insight(SkillPath path) {
        return insight.getOrDefault(path, 0);
    }

    public int points(SkillPath path) {
        return points.getOrDefault(path, 0);
    }

    public boolean has(Identifier skill) {
        return unlocked.contains(skill);
    }

    /** Adds Insight; every {@link #INSIGHT_PER_POINT} becomes a point. */
    public PlayerProgression withInsight(SkillPath path, int amount) {
        int total = insight(path) + amount;
        int earned = total / INSIGHT_PER_POINT;
        Map<SkillPath, Integer> newInsight = new EnumMap<>(SkillPath.class);
        newInsight.putAll(insight);
        newInsight.put(path, total % INSIGHT_PER_POINT);
        Map<SkillPath, Integer> newPoints = new EnumMap<>(SkillPath.class);
        newPoints.putAll(points);
        newPoints.put(path, points(path) + earned);
        return new PlayerProgression(newInsight, newPoints, unlocked, selectedTechnique);
    }

    public PlayerProgression withPoints(SkillPath path, int newValue) {
        Map<SkillPath, Integer> newPoints = new EnumMap<>(SkillPath.class);
        newPoints.putAll(points);
        newPoints.put(path, Math.max(0, newValue));
        return new PlayerProgression(insight, newPoints, unlocked, selectedTechnique);
    }

    public PlayerProgression withUnlocked(Identifier skill) {
        Set<Identifier> set = new LinkedHashSet<>(unlocked);
        set.add(skill);
        return new PlayerProgression(insight, points, set, selectedTechnique);
    }

    public PlayerProgression withTechnique(Identifier technique) {
        return new PlayerProgression(insight, points, unlocked, technique);
    }
}
