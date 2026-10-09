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

    // --- Stealth gear (design doc 26 §4): leather-like protection, stealth stats in GearStats ---

    public static final net.minecraft.world.item.equipment.ArmorMaterial SHINOBI_MATERIAL = new net.minecraft.world.item.equipment.ArmorMaterial(
            8, java.util.Map.of(net.minecraft.world.item.equipment.ArmorType.BOOTS, 1, net.minecraft.world.item.equipment.ArmorType.LEGGINGS, 2,
                    net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, 3, net.minecraft.world.item.equipment.ArmorType.HELMET, 1,
                    net.minecraft.world.item.equipment.ArmorType.BODY, 3),
            15, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, net.minecraft.tags.ItemTags.WOOL,
            net.minecraft.resources.ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, EmergentStealth.id("shinobi")));

    public static final DeferredItem<Item> SHINOBI_HOOD = ITEMS.registerItem("shinobi_hood", Item::new,
            p -> p.humanoidArmor(SHINOBI_MATERIAL, net.minecraft.world.item.equipment.ArmorType.HELMET));
    public static final DeferredItem<Item> SHINOBI_GARB = ITEMS.registerItem("shinobi_garb", Item::new,
            p -> p.humanoidArmor(SHINOBI_MATERIAL, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE));
    public static final DeferredItem<Item> HAKAMA = ITEMS.registerItem("hakama", Item::new,
            p -> p.humanoidArmor(SHINOBI_MATERIAL, net.minecraft.world.item.equipment.ArmorType.LEGGINGS));
    public static final DeferredItem<Item> TABI = ITEMS.registerItem("tabi", Item::new,
            p -> p.humanoidArmor(SHINOBI_MATERIAL, net.minecraft.world.item.equipment.ArmorType.BOOTS));

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

    // Authoring tools (design doc 32): creative and game masters only, checked on the server.
    /** Browse and place structures with a ghost preview. */
    public static final DeferredItem<Item> SURVEYORS_PLAN = ITEMS.registerItem("surveyors_plan", Item::new, p -> p.stacksTo(1));
    /** Mark zones: click two corners for a box, shift-click the second to add it to the selected zone, use in the air to edit. */
    public static final DeferredItem<com.mcspacewizard.emergentstealth.item.SurveyorsRopeItem> SURVEYORS_ROPE = ITEMS.registerItem("surveyors_rope",
            com.mcspacewizard.emergentstealth.item.SurveyorsRopeItem::new, p -> p.stacksTo(1));
    /** Start a compound from a placed structure; add zones and routes, take markers out, save. */
    public static final DeferredItem<com.mcspacewizard.emergentstealth.item.CompoundLedgerItem> COMPOUND_LEDGER = ITEMS.registerItem("compound_ledger",
            com.mcspacewizard.emergentstealth.item.CompoundLedgerItem::new, p -> p.stacksTo(1));
    /** Spawn NPCs with an archetype, behaviour and schedule; with a compound open they join it. */
    public static final DeferredItem<com.mcspacewizard.emergentstealth.item.MusterRollItem> MUSTER_ROLL = ITEMS.registerItem("muster_roll",
            com.mcspacewizard.emergentstealth.item.MusterRollItem::new, p -> p.stacksTo(1));
}
