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

    // Beta toolkit, part A (design doc 21 §2). All are in #emergentstealth:tools.
    public static final DeferredItem<com.mcspacewizard.emergentstealth.tool.PebbleItem> PEBBLE = ITEMS.registerItem("pebble",
            com.mcspacewizard.emergentstealth.tool.PebbleItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<com.mcspacewizard.emergentstealth.tool.SmokeBombItem> SMOKE_BOMB = ITEMS.registerItem("smoke_bomb",
            com.mcspacewizard.emergentstealth.tool.SmokeBombItem::new, p -> p.stacksTo(8));
    public static final DeferredItem<com.mcspacewizard.emergentstealth.tool.FirecrackerItem> FIRECRACKER = ITEMS.registerItem("firecracker",
            com.mcspacewizard.emergentstealth.tool.FirecrackerItem::new, p -> p.stacksTo(8));
    public static final DeferredItem<com.mcspacewizard.emergentstealth.tool.BlindingPowderItem> BLINDING_POWDER = ITEMS.registerItem("blinding_powder",
            com.mcspacewizard.emergentstealth.tool.BlindingPowderItem::new, p -> p.stacksTo(8));
    public static final DeferredItem<com.mcspacewizard.emergentstealth.tool.CaltropsItem> CALTROPS = ITEMS.registerItem("caltrops",
            com.mcspacewizard.emergentstealth.tool.CaltropsItem::new, p -> p.stacksTo(8));
}
