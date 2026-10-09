package com.mcspacewizard.emergentstealth.stealth.light;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.registry.ESBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Putting out and relighting non-powered lights (L-03, design doc 13 §3).
 * <ul>
 *   <li>Empty-hand right-click on a torch or lantern swaps it for its unlit twin; on a campfire, douses it.
 *       (Candles already go out with an empty hand in vanilla.)</li>
 *   <li>Flint and steel, a fire charge or a lit torch item relights an unlit torch or lantern.</li>
 * </ul>
 * Powered lights (redstone lamps, glowstone, sea lanterns, froglights...) are deliberately not snuffable.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class Snuffing {
    private Snuffing() {}

    private static Map<Block, Block> litToUnlit;
    private static Map<Block, Block> unlitToLit;

    private static void ensureMaps() {
        if (litToUnlit != null) {
            return;
        }
        Map<Block, Block> map = new HashMap<>();
        map.put(Blocks.TORCH, ESBlocks.UNLIT_TORCH.get());
        map.put(Blocks.WALL_TORCH, ESBlocks.UNLIT_WALL_TORCH.get());
        map.put(Blocks.SOUL_TORCH, ESBlocks.UNLIT_SOUL_TORCH.get());
        map.put(Blocks.SOUL_WALL_TORCH, ESBlocks.UNLIT_SOUL_WALL_TORCH.get());
        map.put(Blocks.COPPER_TORCH, ESBlocks.UNLIT_COPPER_TORCH.get());
        map.put(Blocks.COPPER_WALL_TORCH, ESBlocks.UNLIT_COPPER_WALL_TORCH.get());
        map.put(Blocks.LANTERN, ESBlocks.UNLIT_LANTERN.get());
        map.put(Blocks.SOUL_LANTERN, ESBlocks.UNLIT_SOUL_LANTERN.get());
        Map<Block, Block> reverse = new HashMap<>();
        map.forEach((lit, unlit) -> reverse.put(unlit, lit));
        litToUnlit = map;
        unlitToLit = reverse;
    }

    public static boolean canSnuff(BlockState state) {
        ensureMaps();
        return litToUnlit.containsKey(state.getBlock())
                || (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT));
    }

    public static boolean canRelight(BlockState state) {
        ensureMaps();
        return unlitToLit.containsKey(state.getBlock());
    }

    /** A light the lamplighter cares about: a torch, lantern or campfire, lit or not. */
    public static boolean isLight(BlockState state) {
        return canSnuff(state) || canRelight(state) || state.getBlock() instanceof CampfireBlock;
    }

    /** The lit form of a light block (an unlit torch is a torch); other blocks are themselves. */
    public static Block litForm(Block block) {
        ensureMaps();
        return unlitToLit.getOrDefault(block, block);
    }

    /** Puts out the light at {@code pos}. Returns true if something was put out. */
    public static boolean snuff(ServerLevel level, BlockPos pos, @Nullable Player player) {
        ensureMaps();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
            CampfireBlock.dowse(player, level, pos, state);
            level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL_IMMEDIATE);
            level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 0.5F, 1.4F);
            LightSourceIndex.invalidate(level, pos);
            return true;
        }
        Block unlit = litToUnlit.get(state.getBlock());
        if (unlit == null) {
            return false;
        }
        level.setBlock(pos, copyProperties(state, unlit.defaultBlockState()), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        LightSourceIndex.invalidate(level, pos);
        return true;
    }

    /** Relights an unlit torch or lantern at {@code pos}. Returns true if it was relit. */
    public static boolean relight(ServerLevel level, BlockPos pos, @Nullable Player player) {
        ensureMaps();
        BlockState state = level.getBlockState(pos);
        Block lit = unlitToLit.get(state.getBlock());
        if (lit == null) {
            return false;
        }
        level.setBlock(pos, copyProperties(state, lit.defaultBlockState()), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        LightSourceIndex.invalidate(level, pos);
        return true;
    }

    private static boolean isIgniter(ItemStack stack) {
        return stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)
                || stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH) || stack.is(Items.COPPER_TORCH);
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack held = event.getItemStack();

        if (held.isEmpty() && canSnuff(state) && !player.isSecondaryUseActive()) {
            if (level instanceof ServerLevel serverLevel) {
                if (snuff(serverLevel, pos, player)) {
                    // The fizz (design doc 16 §1): quiet and unattributed, so a guard right next to you may glance over.
                    com.mcspacewizard.emergentstealth.stealth.sound.Noises.emit(serverLevel, com.mcspacewizard.emergentstealth.stealth.sound.NoiseEvent.of(net.minecraft.world.phys.Vec3.atCenterOf(pos), 2.0F, com.mcspacewizard.emergentstealth.stealth.sound.NoiseKind.OTHER));
                }
            }
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        } else if (isIgniter(held) && canRelight(state)) {
            if (level instanceof ServerLevel serverLevel && relight(serverLevel, pos, player)) {
                if (held.is(Items.FLINT_AND_STEEL)) {
                    held.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
                } else if (held.is(Items.FIRE_CHARGE)) {
                    held.consume(1, player);
                }
            }
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copyProperties(BlockState from, BlockState to) {
        for (Property property : from.getProperties()) {
            if (to.hasProperty(property)) {
                to = to.setValue(property, from.getValue(property));
            }
        }
        return to;
    }
}
