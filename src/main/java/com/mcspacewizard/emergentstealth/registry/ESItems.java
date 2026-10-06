package com.mcspacewizard.emergentstealth.registry;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ESItems {
    private ESItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EmergentStealth.MODID);

    /** Developer tool: right-click toggles the AI debug view (client side; the data itself is op-gated). */
    public static final DeferredItem<Item> DEBUG_LENS = ITEMS.registerSimpleItem("debug_lens", p -> p.stacksTo(1));

    /** Spawns a stealth NPC with the default archetype. Use {@code /emergentstealth npc spawn} for others. */
    public static final DeferredItem<SpawnEggItem> STEALTH_NPC_SPAWN_EGG = ITEMS.registerItem("stealth_npc_spawn_egg",
            p -> new SpawnEggItem(p.spawnEgg(ESEntities.STEALTH_NPC.get())));
}
