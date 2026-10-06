package com.mcspacewizard.emergentstealth.registry;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.entity.ThrownItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ESEntities {
    private ESEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, EmergentStealth.MODID);

    private static final ResourceKey<EntityType<?>> STEALTH_NPC_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, EmergentStealth.id("stealth_npc"));

    public static final DeferredHolder<EntityType<?>, EntityType<StealthNpc>> STEALTH_NPC =
            ENTITY_TYPES.register("stealth_npc", () -> EntityType.Builder.of(StealthNpc::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build(STEALTH_NPC_KEY));

    private static final ResourceKey<EntityType<?>> THROWN_ITEM_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, EmergentStealth.id("thrown_item"));

    /** Any item thrown with the throw key (design doc 16 §5). */
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownItem>> THROWN_ITEM =
            ENTITY_TYPES.register("thrown_item", () -> EntityType.Builder.<ThrownItem>of(ThrownItem::new, MobCategory.MISC)
                    .noLootTable()
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build(THROWN_ITEM_KEY));

    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(STEALTH_NPC.get(), StealthNpc.createAttributes().build());
    }
}
