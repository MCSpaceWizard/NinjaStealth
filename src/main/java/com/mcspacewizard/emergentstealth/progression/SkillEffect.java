package com.mcspacewizard.emergentstealth.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/** What a skill does (design doc 26 §1). Dispatched on {@code "type"} in skill JSON. */
public sealed interface SkillEffect {
    Codec<SkillEffect> CODEC = Codec.STRING.dispatch("type", SkillEffect::typeName, SkillEffect::codecFor);

    String typeName();

    private static MapCodec<? extends SkillEffect> codecFor(String type) {
        return switch (type) {
            case "stealth_stat" -> StatModifier.CODEC;
            case "attribute" -> AttributeBonus.CODEC;
            case "unlock_technique" -> UnlockTechnique.CODEC;
            case "unlock_recipe" -> UnlockRecipe.CODEC;
            default -> throw new IllegalArgumentException("Unknown skill effect type: " + type);
        };
    }

    /** Modifies a {@link StealthStat}: multiplies (factors) or adds (counts) {@code value}. */
    record StatModifier(StealthStat stat, float value) implements SkillEffect {
        static final MapCodec<StatModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                StealthStat.CODEC.fieldOf("stat").forGetter(StatModifier::stat),
                Codec.FLOAT.fieldOf("value").forGetter(StatModifier::value)
        ).apply(i, StatModifier::new));

        @Override
        public String typeName() {
            return "stealth_stat";
        }
    }

    /** A vanilla attribute modifier while the skill is unlocked ({@code operation}: add_value, add_multiplied_base, add_multiplied_total). */
    record AttributeBonus(Identifier attribute, double amount, String operation) implements SkillEffect {
        static final MapCodec<AttributeBonus> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("attribute").forGetter(AttributeBonus::attribute),
                Codec.DOUBLE.fieldOf("amount").forGetter(AttributeBonus::amount),
                Codec.STRING.optionalFieldOf("operation", "add_multiplied_total").forGetter(AttributeBonus::operation)
        ).apply(i, AttributeBonus::new));

        @Override
        public String typeName() {
            return "attribute";
        }
    }

    record UnlockTechnique(Identifier technique) implements SkillEffect {
        static final MapCodec<UnlockTechnique> CODEC = Identifier.CODEC.fieldOf("technique").xmap(UnlockTechnique::new, UnlockTechnique::technique);

        @Override
        public String typeName() {
            return "unlock_technique";
        }
    }

    record UnlockRecipe(Identifier recipe) implements SkillEffect {
        static final MapCodec<UnlockRecipe> CODEC = Identifier.CODEC.fieldOf("recipe").xmap(UnlockRecipe::new, UnlockRecipe::recipe);

        @Override
        public String typeName() {
            return "unlock_recipe";
        }
    }
}
