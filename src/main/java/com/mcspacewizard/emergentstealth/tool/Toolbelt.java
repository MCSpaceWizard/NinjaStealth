package com.mcspacewizard.emergentstealth.tool;

import java.util.List;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.registry.ESItems;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The toolbelt (design doc 34 §3): an item with {@value #SIZE} slots for stealth tools. The tool wheel shows its
 * slots and quick use draws from it. Contents live in the vanilla {@code minecraft:container} component, so they
 * survive dropping, chests and death like a shulker box's. Side-neutral: the client reads the same stacks.
 */
public final class Toolbelt {
    private Toolbelt() {}

    public static final int SIZE = 8;
    /** Tools that stay in the inventory because a bow or blowgun draws them from there: arrows and darts. */
    public static final TagKey<Item> AMMO = TagKey.create(Registries.ITEM, EmergentStealth.id("tool_ammo"));

    /** Whether {@code stack} may go in a belt slot: a stealth tool that isn't ammo. */
    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Toolkit.TOOLS) && !stack.is(AMMO);
    }

    public static boolean isBelt(ItemStack stack) {
        return stack.is(ESItems.TOOLBELT.get());
    }

    /** The inventory slot of the belt in use, or -1: the offhand first, then the hotbar, then the main inventory. */
    public static int find(Inventory inventory) {
        if (isBelt(inventory.getItem(Inventory.SLOT_OFFHAND))) {
            return Inventory.SLOT_OFFHAND;
        }
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (isBelt(inventory.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    /** The belt in use, or {@link ItemStack#EMPTY}. */
    public static ItemStack belt(Inventory inventory) {
        int slot = find(inventory);
        return slot < 0 ? ItemStack.EMPTY : inventory.getItem(slot);
    }

    /** A copy of the belt's slots ({@value #SIZE}, empty ones as {@link ItemStack#EMPTY}). */
    public static NonNullList<ItemStack> contents(ItemStack belt) {
        NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        if (!belt.isEmpty()) {
            belt.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        }
        return items;
    }

    /** Writes {@code items} (up to {@value #SIZE}) back into the belt. */
    public static void store(ItemStack belt, List<ItemStack> items) {
        if (!belt.isEmpty()) {
            belt.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items.subList(0, Math.min(SIZE, items.size()))));
        }
    }

    /** The first belt slot holding {@code item}, or -1. */
    public static int slotOf(ItemStack belt, Item item) {
        List<ItemStack> items = contents(belt);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    /** How many of {@code item} the belt holds. */
    public static int count(ItemStack belt, Item item) {
        int total = 0;
        for (ItemStack stack : contents(belt)) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
