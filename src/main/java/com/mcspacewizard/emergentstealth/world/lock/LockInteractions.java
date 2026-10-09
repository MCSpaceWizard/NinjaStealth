package com.mcspacewizard.emergentstealth.world.lock;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.item.LockpickItem;
import com.mcspacewizard.emergentstealth.item.LocksmithKitItem;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Enforces locks on players (design doc 21 §3), server side:
 * <ul>
 *   <li>Right-clicking a locked block without its key is blocked: "Locked" in the action bar and a rattle.
 *       Lockpicks and the Locksmith's Kit are let through (they handle the click themselves).</li>
 *   <li>Survival players can't break a locked block. Creative players can, and the lock goes with it.</li>
 * </ul>
 * Any key in the inventory opens its locks; the key doesn't need to be held.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class LockInteractions {
    private LockInteractions() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (held.getItem() instanceof LockpickItem || held.getItem() instanceof LocksmithKitItem) {
            return;
        }
        Player player = event.getEntity();
        LockData.Lock lock = Locks.lockAt(level, event.getPos());
        if (lock == null || Locks.hasKey(player, lock.key())) {
            return;
        }
        // Sneaking with an item in hand never uses the block in vanilla: let the item do its thing (place a block...).
        if (player.isSecondaryUseActive() && !held.isEmpty()) {
            event.setUseBlock(net.minecraft.util.TriState.FALSE);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        Locks.rattle(level, event.getPos(), player);
    }

    @SubscribeEvent
    static void onBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !Locks.isLockable(event.getState())) {
            return;
        }
        Player player = event.getPlayer();
        if (Locks.lockAt(level, event.getPos()) == null) {
            return;
        }
        if (player.isCreative()) {
            Locks.unlock(level, event.getPos());
            return;
        }
        event.setCanceled(true);
        player.sendOverlayMessage(Component.translatable("message.emergentstealth.locked_unbreakable"));
    }
}
