package com.mcspacewizard.emergentstealth.progression;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * A skill from the datapack registry {@code emergentstealth/skill} (design doc 26 §1), synced to clients for
 * the skill tree screen. Name and description are lang keys {@code skill.<ns>.<path>} and {@code ...desc}.
 *
 * @param path      which tree
 * @param column    grid column in the tree screen
 * @param row       grid row (0 = root)
 * @param cost      skill points
 * @param requires  prerequisite skills
 * @param requireAll all prerequisites (true) or any one of them (false)
 * @param effects   what it does
 * @param icon      item id shown on the node
 * @param capstone  needs a mastery challenge (not yet obtainable)
 */
public record SkillDefinition(SkillPath path, int column, int row, int cost, List<Identifier> requires, boolean requireAll,
                              List<SkillEffect> effects, Identifier icon, boolean capstone) {
    public static final Codec<SkillDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            SkillPath.CODEC.fieldOf("path").forGetter(SkillDefinition::path),
            Codec.INT.fieldOf("column").forGetter(SkillDefinition::column),
            Codec.INT.fieldOf("row").forGetter(SkillDefinition::row),
            Codec.intRange(0, 100).optionalFieldOf("cost", 1).forGetter(SkillDefinition::cost),
            Identifier.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(SkillDefinition::requires),
            Codec.BOOL.optionalFieldOf("require_all", true).forGetter(SkillDefinition::requireAll),
            SkillEffect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(SkillDefinition::effects),
            Identifier.CODEC.optionalFieldOf("icon", Identifier.withDefaultNamespace("paper")).forGetter(SkillDefinition::icon),
            Codec.BOOL.optionalFieldOf("capstone", false).forGetter(SkillDefinition::capstone)
    ).apply(i, SkillDefinition::new));

    public static String nameKey(Identifier id) {
        return "skill." + id.getNamespace() + "." + id.getPath();
    }

    public static String descriptionKey(Identifier id) {
        return nameKey(id) + ".desc";
    }

}
