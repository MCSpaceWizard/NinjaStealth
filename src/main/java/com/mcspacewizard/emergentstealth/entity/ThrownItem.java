package com.mcspacewizard.emergentstealth.entity;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.registry.ESEntities;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseEvent;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseKind;
import com.mcspacewizard.emergentstealth.stealth.sound.Noises;
import com.mcspacewizard.emergentstealth.stealth.sound.SoundTags;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Any item thrown with the throw key (design doc 16 §5). Flies like a snowball and makes an
 * {@link NoiseKind#IMPACT impact} noise where it lands — a noise with no cause, so it points at the spot,
 * not at the thrower.
 * <ul>
 *   <li>{@link SoundTags#SHATTERS_ON_IMPACT Glass}: louder (14), shatters and leaves nothing behind.</li>
 *   <li>{@link SoundTags#SHARP_THROWABLES Sharp items}: 1 damage to a mob they hit, then drop.</li>
 *   <li>Anything else drops where it lands and can be picked up again.</li>
 * </ul>
 */
public class ThrownItem extends net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile {
    public static final float IMPACT_LOUDNESS = 10.0F;
    public static final float GLASS_LOUDNESS = 14.0F;
    public static final float SHARP_DAMAGE = 1.0F;

    public ThrownItem(EntityType<? extends ThrownItem> type, Level level) {
        super(type, level);
    }

    public ThrownItem(Level level, LivingEntity thrower, ItemStack stack) {
        super(ESEntities.THROWN_ITEM.get(), thrower, level, stack);
    }

    public ThrownItem(Level level, double x, double y, double z, ItemStack stack) {
        super(ESEntities.THROWN_ITEM.get(), x, y, z, level, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.COBBLESTONE;
    }

    public boolean shatters() {
        return getItem().is(SoundTags.SHATTERS_ON_IMPACT);
    }

    public boolean sharp() {
        return getItem().is(SoundTags.SHARP_THROWABLES);
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (this.level() instanceof ServerLevel level && sharp() && hitResult.getEntity() instanceof LivingEntity target) {
            target.hurtServer(level, this.damageSources().thrown(this, this.getOwner()), SHARP_DAMAGE);
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            land(level, hitResult);
            this.discard();
        }
    }

    /** Noise, then shatter or drop. */
    private void land(ServerLevel level, HitResult hitResult) {
        Vec3 at = impactPoint(hitResult);
        ItemStack stack = getItem();
        boolean glass = shatters();
        Noises.emit(level, new NoiseEvent(at, glass ? GLASS_LOUDNESS : IMPACT_LOUDNESS, NoiseKind.IMPACT, null, null));
        if (glass) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.0F,
                    0.9F + this.random.nextFloat() * 0.2F);
            if (!stack.isEmpty()) {
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(stack)),
                        at.x, at.y, at.z, 14, 0.15, 0.15, 0.15, 0.12);
            }
            return;
        }
        level.playSound(null, at.x, at.y, at.z, impactSound(level, hitResult), SoundSource.NEUTRAL, 0.8F,
                0.9F + this.random.nextFloat() * 0.2F);
        if (!stack.isEmpty()) {
            ItemEntity drop = new ItemEntity(level, at.x, at.y, at.z, stack.copy());
            drop.setDeltaMovement(this.getDeltaMovement().scale(-0.05).add(0.0, 0.1, 0.0));
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
    }

    /** Just outside the block face that was hit, so the noise starts in the air on the thrower's side of a wall. */
    private Vec3 impactPoint(HitResult hitResult) {
        if (hitResult instanceof BlockHitResult blockHit && hitResult.getType() == HitResult.Type.BLOCK) {
            return blockHit.getLocation().add(blockHit.getDirection().getUnitVec3().scale(0.15));
        }
        return hitResult.getLocation();
    }

    private net.minecraft.sounds.SoundEvent impactSound(ServerLevel level, HitResult hitResult) {
        if (hitResult instanceof BlockHitResult blockHit && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockState state = level.getBlockState(blockHit.getBlockPos());
            return state.getSoundType(level, blockHit.getBlockPos(), this).getHitSound();
        }
        return sharp() ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK;
    }

    /** The thrower, if still around (for tests and debugging). */
    public @Nullable Entity thrower() {
        return this.getOwner();
    }
}
