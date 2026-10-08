package com.mcspacewizard.emergentstealth.tool;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * An item the tool wheel's quick use (V) can use straight from the inventory, without changing what the player
 * holds (design doc 21 §1). Items in {@code #emergentstealth:tools} that don't implement this (ranged tools such
 * as bows and blowguns) are equipped into the hand instead.
 */
public interface StealthTool {
    /**
     * Uses one of {@code stack} for the player. Server side only; the stack may be anywhere in the inventory.
     *
     * @param charge 0 = a tap, 1 = fully charged (throwables throw further)
     * @return whether anything happened (false: cooldown, nothing in range...)
     */
    boolean quickUse(ServerPlayer player, ItemStack stack, float charge);
}
