package com.mcspacewizard.emergentstealth.progression;

import java.util.List;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Applies skills' {@code attribute} effects as attribute modifiers (design doc 26 §1). */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class SkillAttributes {
    private SkillAttributes() {}

    /** Removes every skill modifier and re-adds those of unlocked skills. */
    public static void refresh(ServerPlayer player) {
        var registry = Skills.registry(player);
        PlayerProgression progression = Skills.progression(player);
        for (var entry : registry.entrySet()) {
            Identifier skillId = entry.getKey().identifier();
            List<SkillEffect> effects = entry.getValue().effects();
            for (int i = 0; i < effects.size(); i++) {
                if (!(effects.get(i) instanceof SkillEffect.AttributeBonus bonus)) {
                    continue;
                }
                Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.get(bonus.attribute()).orElse(null);
                AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
                if (instance == null) {
                    continue;
                }
                Identifier modifierId = EmergentStealth.id("skill/" + skillId.getNamespace() + "/" + skillId.getPath() + "/" + i);
                instance.removeModifier(modifierId);
                if (progression.has(skillId)) {
                    instance.addTransientModifier(new AttributeModifier(modifierId, bonus.amount(), operation(bonus.operation())));
                }
            }
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static AttributeModifier.Operation operation(String name) {
        return switch (name) {
            case "add_value" -> AttributeModifier.Operation.ADD_VALUE;
            case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            default -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        };
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
        }
    }
}
