package com.mcspacewizard.emergentstealth.world.lock;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.item.KeyItem;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Public lock API (design doc 21 §3). Locks are per-position world data ({@link LockData}): any block in
 * {@code #emergentstealth:lockable} (doors, trapdoors, fence gates, chests, barrels) can be locked to a key name.
 * <ul>
 *   <li>Doors are stored by their lower half; a double chest is locked if either half is.</li>
 *   <li>A lock only stops <em>opening</em>: anyone may close a locked door, and a door that is open (picked,
 *       opened with a key, or by redstone) is simply open until someone closes it.</li>
 *   <li>A lock whose block is gone or no longer lockable is dropped the next time it's looked up.</li>
 * </ul>
 * Server side only.
 */
public final class Locks {
    private Locks() {}

    /** Blocks that can be locked. */
    public static final TagKey<Block> LOCKABLE = TagKey.create(Registries.BLOCK, EmergentStealth.id("lockable"));

    public static boolean isLockable(BlockState state) {
        return state.is(LOCKABLE);
    }

    /** The position a lock is stored under: a door's lower half, otherwise {@code pos}. */
    public static BlockPos canonical(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return pos.immutable();
    }

    /** The other half of a double chest, or null. */
    private static @Nullable BlockPos chestPartner(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return pos.relative(ChestBlock.getConnectedDirection(state));
        }
        return null;
    }

    /** The lock on the block at {@code pos} (either door half, either chest half), or null. */
    public static LockData.@Nullable Lock lockAt(ServerLevel level, BlockPos pos) {
        LockData data = LockData.get(level);
        if (data.isEmpty()) {
            return null;
        }
        BlockPos canonical = canonical(level, pos);
        BlockState state = level.getBlockState(canonical);
        LockData.Lock lock = data.get(canonical);
        if (lock != null && !isLockable(state)) {
            data.remove(canonical); // the block was broken or replaced
            return null;
        }
        if (lock == null) {
            BlockPos partner = chestPartner(state, canonical);
            if (partner != null) {
                lock = data.get(partner);
            }
        }
        return lock;
    }

    public static boolean isLocked(ServerLevel level, BlockPos pos) {
        return lockAt(level, pos) != null;
    }

    /** Locks the block at {@code pos} to {@code key}. False if the block can't be locked. */
    public static boolean lock(ServerLevel level, BlockPos pos, String key, int difficulty) {
        if (!isLockable(level.getBlockState(pos)) || key.isBlank()) {
            return false;
        }
        LockData.get(level).put(canonical(level, pos), new LockData.Lock(key, difficulty));
        return true;
    }

    /** Removes the lock on the block at {@code pos} (both chest halves). True if there was one. */
    public static boolean unlock(ServerLevel level, BlockPos pos) {
        LockData data = LockData.get(level);
        BlockPos canonical = canonical(level, pos);
        boolean removed = data.remove(canonical);
        BlockPos partner = chestPartner(level.getBlockState(canonical), canonical);
        if (partner != null) {
            removed |= data.remove(partner);
        }
        return removed;
    }

    // ------------------------------------------------------------------------------------------------
    // Keys

    /**
     * Whether {@code entity} carries the key: a player anywhere in their inventory; an NPC or other mob in any
     * equipment slot, or in its {@link ESAttachments#NPC_KEYS} list.
     */
    public static boolean hasKey(LivingEntity entity, String key) {
        if (entity instanceof Player player) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (key.equals(KeyItem.keyId(player.getInventory().getItem(i)))) {
                    return true;
                }
            }
            return false;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (key.equals(KeyItem.keyId(entity.getItemBySlot(slot)))) {
                return true;
            }
        }
        List<String> extra = entity.getExistingDataOrNull(ESAttachments.NPC_KEYS);
        return extra != null && extra.contains(key);
    }

    /** Whether {@code entity} may open the block at {@code pos}: it isn't locked, or they carry its key. */
    public static boolean mayOpen(ServerLevel level, BlockPos pos, LivingEntity entity) {
        LockData.Lock lock = lockAt(level, pos);
        return lock == null || hasKey(entity, lock.key());
    }

    // ------------------------------------------------------------------------------------------------
    // Navigation hooks (StealthNodeEvaluator, PassageGoal)

    /** True for a closed door, trapdoor or fence gate. */
    public static boolean isClosedPassage(BlockState state) {
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof TrapDoorBlock) {
            return state.hasProperty(BlockStateProperties.OPEN) && !state.getValue(BlockStateProperties.OPEN);
        }
        return false;
    }

    /**
     * Pathfinding: a closed, locked door or gate is a wall for a mob without its key. Cheap when nothing in the
     * level is locked.
     */
    public static boolean blocksMob(@Nullable Mob mob, BlockPos pos, BlockState state) {
        if (mob == null || !isClosedPassage(state) || !(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        return !mayOpen(level, pos, mob);
    }

    // ------------------------------------------------------------------------------------------------
    // Opening (used for a successful lockpick)

    /**
     * Opens the block once for {@code player}, bypassing the lock: a door, trapdoor or gate swings open (it
     * locks again when closed); a container's menu opens. Returns false if there was nothing to open.
     */
    public static boolean openOnce(ServerLevel level, BlockPos pos, Player player) {
        BlockPos canonical = canonical(level, pos);
        BlockState state = level.getBlockState(canonical);
        if (state.getBlock() instanceof DoorBlock door) {
            door.setOpen(player, level, state, canonical, true);
            return true;
        }
        if (state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof TrapDoorBlock) {
            if (!state.getValue(BlockStateProperties.OPEN)) {
                BlockState open = state.setValue(BlockStateProperties.OPEN, true);
                if (state.getBlock() instanceof FenceGateBlock && state.hasProperty(FenceGateBlock.FACING)) {
                    // Swing away from the player, like vanilla.
                    var facing = player.getDirection();
                    if (state.getValue(FenceGateBlock.FACING) == facing.getOpposite()) {
                        open = open.setValue(FenceGateBlock.FACING, facing);
                    }
                }
                level.setBlock(canonical, open, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                level.playSound(null, canonical, state.getBlock() instanceof FenceGateBlock ? SoundEvents.FENCE_GATE_OPEN
                        : SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(player, GameEvent.BLOCK_OPEN, canonical);
            }
            return true;
        }
        MenuProvider menu = state.getMenuProvider(level, canonical);
        if (menu != null) {
            player.openMenu(menu);
            return true;
        }
        return false;
    }

    /** "Locked" feedback: action bar message and the vanilla locked-container rattle. */
    public static void rattle(ServerLevel level, BlockPos pos, Player player) {
        player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.emergentstealth.locked"));
        level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** A blank-safe empty check for item stacks used as keys. */
    static boolean isKey(ItemStack stack) {
        return KeyItem.keyId(stack) != null;
    }
}
