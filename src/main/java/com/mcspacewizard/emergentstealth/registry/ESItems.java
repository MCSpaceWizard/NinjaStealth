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
}
