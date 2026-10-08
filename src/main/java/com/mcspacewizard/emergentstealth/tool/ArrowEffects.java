package com.mcspacewizard.emergentstealth.tool;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.stealth.light.LightSourceIndex;
import com.mcspacewizard.emergentstealth.stealth.light.Snuffing;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseEvent;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseKind;
import com.mcspacewizard.emergentstealth.stealth.sound.Noises;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What water and fire arrows do where they land (design doc 21 §2). Pure world effects, shared by the arrow
 * entities and tests.
 */
public final class ArrowEffects {
    private ArrowEffects() {}

    /** Lights within this distance of the impact (block centre to impact point) are affected. */
    public static final double RADIUS = 1.5;
    /** The splash of a water arrow (design doc 21: noise 2, points at the spot). */
    public static final float SPLASH_NOISE = 2.0F;

    /**
     * Water arrow: puts out torches, lanterns, campfires and candles within {@link #RADIUS}, removes fire, and
     * douses burning entities nearby. Splash noise 2. Returns how many blocks were put out.
     */
    public static int splash(ServerLevel level, Vec3 at, @Nullable Entity cause) {
        int doused = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(at).offset(-2, -2, -2), BlockPos.containing(at).offset(2, 2, 2))) {
            if (Vec3.atCenterOf(pos).distanceTo(at) > RADIUS) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (Snuffing.canSnuff(state)) {
                if (Snuffing.snuff(level, pos.immutable(), null)) {
                    doused++;
                }
            } else if (state.getBlock() instanceof BaseFireBlock) {
                level.removeBlock(pos, false);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.6F);
                doused++;
            } else if ((state.getBlock() instanceof AbstractCandleBlock) && state.hasProperty(BlockStateProperties.LIT)
                    && state.getValue(BlockStateProperties.LIT)) {
                AbstractCandleBlock.extinguish(null, state, level, pos.immutable());
                LightSourceIndex.invalidate(level, pos.immutable());
                doused++;
            }
        }
        for (Entity entity : level.getEntities((Entity) null, new AABB(at, at).inflate(RADIUS), Entity::isOnFire)) {
            entity.clearFire();
        }
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 24, 0.3, 0.2, 0.3, 0.2);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.5F, 1.4F);
        Noises.emit(level, new NoiseEvent(at, SPLASH_NOISE, NoiseKind.IMPACT, null, cause != null ? cause.getUUID() : null));
        return doused;
    }

    /**
     * Fire arrow: relights unlit torches and lanterns, campfires and candles within {@link #RADIUS}, and sets
     * the struck block's face on fire if the block is flammable. Returns how many lights were lit.
     */
    public static int ignite(ServerLevel level, Vec3 at, @Nullable BlockPos struck, @Nullable Direction face, @Nullable Entity cause) {
        int lit = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(at).offset(-2, -2, -2), BlockPos.containing(at).offset(2, 2, 2))) {
            if (Vec3.atCenterOf(pos).distanceTo(at) > RADIUS) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            BlockPos fixed = pos.immutable();
            if (Snuffing.canRelight(state)) {
                if (Snuffing.relight(level, fixed, null)) {
                    lit++;
                }
            } else if (CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)) {
                level.setBlock(fixed, state.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL_IMMEDIATE);
                level.playSound(null, fixed, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 0.8F, 1.2F);
                level.gameEvent(cause, GameEvent.BLOCK_CHANGE, fixed);
                LightSourceIndex.invalidate(level, fixed);
                lit++;
            }
        }
        if (struck != null && face != null) {
            BlockPos firePos = struck.relative(face);
            BlockState struckState = level.getBlockState(struck);
            if (struckState.isFlammable(level, struck, face) && BaseFireBlock.canBePlacedAt(level, firePos, face)) {
                // No BLOCK_PLACE game event: that would be a placing noise (6), and arrows are silent (S-03).
                level.setBlock(firePos, BaseFireBlock.getState(level, firePos), Block.UPDATE_ALL_IMMEDIATE);
            }
        }
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.02);
        return lit;
    }
}
