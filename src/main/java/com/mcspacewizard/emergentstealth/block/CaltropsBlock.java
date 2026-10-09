package com.mcspacewizard.emergentstealth.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Scattered caltrops (design doc 21 §2). A flat, walk-through block that lasts 60 s. Anyone stepping in takes
 * 1 damage and Slowness II for 3 s (re-applied while they stay on it). Mobs plan around it (a path malus of 8), so guards avoid caltrops when there's a way round and slow down when there isn't.
 */
public class CaltropsBlock extends Block {
    public static final MapCodec<CaltropsBlock> CODEC = simpleCodec(CaltropsBlock::new);
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, EmergentStealth.id("caltrops"));

    public static final int LIFETIME_TICKS = 1200;
    public static final float DAMAGE_AMOUNT = 1.0F;
    public static final int SLOW_TICKS = 60;
    /** Slowness II. */
    public static final int SLOW_AMPLIFIER = 1;

    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 1.5, 15.0);

    public CaltropsBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends CaltropsBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, LIFETIME_TICKS);
        }
    }

    /** Now and then a spike catches the light, so a patch can be spotted at night (design doc 34 §2). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(14) == 0) {
            level.addParticle(com.mcspacewizard.emergentstealth.registry.ESParticles.GLINT,
                    pos.getX() + 0.15 + random.nextDouble() * 0.7, pos.getY() + 0.12, pos.getZ() + 0.15 + random.nextDouble() * 0.7, 0.0, 0.0, 0.0);
        }
    }

    /** Time's up: the caltrops are trodden into the ground. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level instanceof ServerLevel serverLevel && entity instanceof LivingEntity living) {
            step(serverLevel, living);
        }
    }

    /**
     * A living entity is on caltrops: 1 damage when it steps in (or keeps walking on them; the hurt cooldown limits
     * the rate) and Slowness II that lasts 3 s after it gets off.
     */
    public static void step(ServerLevel level, LivingEntity living) {
        if (living.isSpectator() || (living instanceof Player player && player.getAbilities().flying)) {
            return;
        }
        MobEffectInstance slow = living.getEffect(MobEffects.SLOWNESS);
        boolean fresh = slow == null || slow.getAmplifier() < SLOW_AMPLIFIER;
        Vec3 movement = living.isClientAuthoritative() ? living.getKnownMovement() : living.oldPosition().subtract(living.position());
        boolean moving = movement.horizontalDistanceSqr() > 1.0E-5;
        if (fresh || moving) {
            living.hurtServer(level, level.damageSources().source(DAMAGE), DAMAGE_AMOUNT);
        }
        living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, SLOW_AMPLIFIER));
    }

    @Override
    public @Nullable PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
        // Walkable with a malus of 8 (vanilla's DAMAGING would forbid it outright, so guards couldn't chase across).
        return PathType.STICKY_HONEY;
    }
}
