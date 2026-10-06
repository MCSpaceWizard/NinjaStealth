package com.mcspacewizard.emergentstealth.data;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Datapack registry entry {@code emergentstealth:archetype}: who an NPC is.
 * One entity type serves every human NPC; the archetype decides role, looks, stats and gear.
 *
 * @param role      behaviour role
 * @param faction   faction id (a faction registry arrives in S9; until then this is just an id)
 * @param bodies    base skin textures, one picked per NPC at spawn
 * @param outfit    outfit registry id drawn over the body
 * @param stats     base attribute values
 * @param equipment starting equipment per slot
 */
public record Archetype(
        NpcRole role,
        Identifier faction,
        List<Identifier> bodies,
        Optional<Identifier> outfit,
        Stats stats,
        Map<EquipmentSlot, ItemStackTemplate> equipment) {

    public static final Codec<Archetype> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcRole.CODEC.fieldOf("role").forGetter(Archetype::role),
            Identifier.CODEC.fieldOf("faction").forGetter(Archetype::faction),
            Identifier.CODEC.listOf().optionalFieldOf("bodies", List.of()).forGetter(Archetype::bodies),
            Identifier.CODEC.optionalFieldOf("outfit").forGetter(Archetype::outfit),
            Stats.CODEC.optionalFieldOf("stats", Stats.DEFAULT).forGetter(Archetype::stats),
            Codec.unboundedMap(EquipmentSlot.CODEC, ItemStackTemplate.CODEC)
                    .optionalFieldOf("equipment", Map.of()).forGetter(Archetype::equipment)
    ).apply(instance, Archetype::new));

    /** Base attribute values applied when an NPC takes this archetype. */
    public record Stats(double maxHealth, double movementSpeed, double attackDamage) {
        public static final Stats DEFAULT = new Stats(20.0, 0.3, 2.0);

        public static final Codec<Stats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("max_health", DEFAULT.maxHealth).forGetter(Stats::maxHealth),
                Codec.DOUBLE.optionalFieldOf("movement_speed", DEFAULT.movementSpeed).forGetter(Stats::movementSpeed),
                Codec.DOUBLE.optionalFieldOf("attack_damage", DEFAULT.attackDamage).forGetter(Stats::attackDamage)
        ).apply(instance, Stats::new));
    }
}
