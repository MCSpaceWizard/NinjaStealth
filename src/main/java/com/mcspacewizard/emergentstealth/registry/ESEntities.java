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

    // Beta toolkit, part A (design doc 21).
    private static final ResourceKey<EntityType<?>> SMOKE_CLOUD_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, EmergentStealth.id("smoke_cloud"));

    /** A smoke bomb's cloud: blocks sight while it lasts. Invisible itself; the client draws particles. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.mcspacewizard.emergentstealth.entity.SmokeCloud>> SMOKE_CLOUD =
            ENTITY_TYPES.register("smoke_cloud", () -> EntityType.Builder.<com.mcspacewizard.emergentstealth.entity.SmokeCloud>of(
                            com.mcspacewizard.emergentstealth.entity.SmokeCloud::new, MobCategory.MISC)
                    .noLootTable()
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .updateInterval(Integer.MAX_VALUE)
                    .build(SMOKE_CLOUD_KEY));

    private static final ResourceKey<EntityType<?>> LIT_FIRECRACKER_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, EmergentStealth.id("lit_firecracker"));

    /** A firecracker going off where it landed. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.mcspacewizard.emergentstealth.entity.LitFirecracker>> LIT_FIRECRACKER =
            ENTITY_TYPES.register("lit_firecracker", () -> EntityType.Builder.<com.mcspacewizard.emergentstealth.entity.LitFirecracker>of(
                            com.mcspacewizard.emergentstealth.entity.LitFirecracker::new, MobCategory.MISC)
                    .noLootTable()
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(6)
                    .updateInterval(2)
                    .build(LIT_FIRECRACKER_KEY));

    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(STEALTH_NPC.get(), StealthNpc.createAttributes().build());
    }

    // --- Beta toolkit, part B (design doc 21 §2) ---

    public static final DeferredHolder<EntityType<?>, EntityType<com.mcspacewizard.emergentstealth.entity.WaterArrow>> WATER_ARROW =
            arrow("water_arrow", com.mcspacewizard.emergentstealth.entity.WaterArrow::new);
    public static final DeferredHolder<EntityType<?>, EntityType<com.mcspacewizard.emergentstealth.entity.FireArrow>> FIRE_ARROW =
            arrow("fire_arrow", com.mcspacewizard.emergentstealth.entity.FireArrow::new);
    public static final DeferredHolder<EntityType<?>, EntityType<com.mcspacewizard.emergentstealth.entity.SleepDart>> SLEEP_DART =
            arrow("sleep_dart", com.mcspacewizard.emergentstealth.entity.SleepDart::new);

    /** Arrow-sized projectile, tracked like vanilla arrows. */
    private static <T extends net.minecraft.world.entity.Entity> DeferredHolder<EntityType<?>, EntityType<T>> arrow(String name, EntityType.EntityFactory<T> factory) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, EmergentStealth.id(name));
        return ENTITY_TYPES.register(name, () -> EntityType.Builder.of(factory, MobCategory.MISC)
                .noLootTable()
                .sized(0.5F, 0.5F)
                .eyeHeight(0.13F)
                .clientTrackingRange(4)
                .updateInterval(20)
                .build(key));
    }
}
