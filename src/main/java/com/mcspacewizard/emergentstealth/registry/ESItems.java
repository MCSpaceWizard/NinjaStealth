package com.mcspacewizard.emergentstealth.registry;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.item.PatrolBatonItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ESItems {
    private ESItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EmergentStealth.MODID);

    /** Developer tool: right-click toggles the AI debug view (client side; the data itself is op-gated). */
    public static final DeferredItem<Item> DEBUG_LENS = ITEMS.registerSimpleItem("debug_lens", p -> p.stacksTo(1));

    /** Map-maker tool for authoring patrol routes (design doc 15). */
    public static final DeferredItem<PatrolBatonItem> PATROL_BATON = ITEMS.registerItem("patrol_baton",
            PatrolBatonItem::new, p -> p.stacksTo(1));

    /** Spawns a stealth NPC with the default archetype. Use {@code /emergentstealth npc spawn} for others. */
    public static final DeferredItem<SpawnEggItem> STEALTH_NPC_SPAWN_EGG = ITEMS.registerItem("stealth_npc_spawn_egg",
            p -> new SpawnEggItem(p.spawnEgg(ESEntities.STEALTH_NPC.get())));

    // --- Beta toolkit, part B (design doc 21 §2) ---

    /** Puts out lights near the impact (vanilla bow and crossbow). */
    public static final DeferredItem<com.mcspacewizard.emergentstealth.item.SpecialArrowItem> WATER_ARROW = ITEMS.registerItem("water_arrow",
            p -> new com.mcspacewizard.emergentstealth.item.SpecialArrowItem(p, com.mcspacewizard.emergentstealth.entity.WaterArrow::new,
                    com.mcspacewizard.emergentstealth.entity.WaterArrow::new));
    /** Relights unlit lights and ignites flammables at the impact. */
    public static final DeferredItem<com.mcspacewizard.emergentstealth.item.SpecialArrowItem> FIRE_ARROW = ITEMS.registerItem("fire_arrow",
            p -> new com.mcspacewizard.emergentstealth.item.SpecialArrowItem(p, com.mcspacewizard.emergentstealth.entity.FireArrow::new,
                    com.mcspacewizard.emergentstealth.entity.FireArrow::new));
    /** Bow-like, short draw, fires sleep darts. */
    public static final DeferredItem<com.mcspacewizard.emergentstealth.item.BlowgunItem> BLOWGUN = ITEMS.registerItem("blowgun",
            com.mcspacewizard.emergentstealth.item.BlowgunItem::new, p -> p.durability(192));
    /** Blowgun ammo: staggers, then knocks out. */
    public static final DeferredItem<com.mcspacewizard.emergentstealth.item.SpecialArrowItem> SLEEP_DART = ITEMS.registerItem("sleep_dart",
            p -> new com.mcspacewizard.emergentstealth.item.SpecialArrowItem(p, com.mcspacewizard.emergentstealth.entity.SleepDart::new,
                    com.mcspacewizard.emergentstealth.entity.SleepDart::new));
    /** Opens doors and chests locked to its key id. */
    public static final DeferredItem<com.mcspacewizard.emergentstealth.item.KeyItem> KEY = ITEMS.registerItem("key",
            com.mcspacewizard.emergentstealth.item.KeyItem::new, p -> p.stacksTo(1));
    /** Timing-ring lockpicking (durability 8). */
    public static final DeferredItem<com.mcspacewizard.emergentstealth.item.LockpickItem> LOCKPICK = ITEMS.registerItem("lockpick",
            com.mcspacewizard.emergentstealth.item.LockpickItem::new, p -> p.durability(8));
    /** Map-maker tool: locks/unlocks a door or chest and hands out its key. */
    public static final DeferredItem<com.mcspacewizard.emergentstealth.item.LocksmithKitItem> LOCKSMITH_KIT = ITEMS.registerItem("locksmiths_kit",
            com.mcspacewizard.emergentstealth.item.LocksmithKitItem::new, p -> p.stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));
}
