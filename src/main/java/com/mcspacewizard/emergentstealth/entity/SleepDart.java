package com.mcspacewizard.emergentstealth.entity;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.registry.ESEntities;
import com.mcspacewizard.emergentstealth.registry.ESItems;
import com.mcspacewizard.emergentstealth.tool.Darts;

import com.mcspacewizard.emergentstealth.tool.ToolEffects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Sleep dart (design doc 21 §2): does no damage. An NPC it hits staggers for 3 s and then falls unconscious
 * ({@link Darts}); one already fighting only staggers. Silent: no noise event. A dart that misses sticks
 * where it lands and can be picked up.
 */
public class SleepDart extends AbstractArrow {
    public SleepDart(EntityType<? extends SleepDart> type, Level level) {
        super(type, level);
    }

    public SleepDart(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
        super(ESEntities.SLEEP_DART.get(), owner, level, pickup, weapon);
    }

    public SleepDart(Level level, double x, double y, double z, ItemStack pickup) {
        super(ESEntities.SLEEP_DART.get(), x, y, z, level, pickup, null);
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        if (this.level() instanceof ServerLevel level && hitResult.getEntity() instanceof LivingEntity target) {
            Darts.hit(level, target, this.getOwner());
            this.discard();
        }
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.BAMBOO_HIT;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() && !this.isInGround() && !this.isRemoved()) {
            ToolEffects.dartTrail(this.level(), this);
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ESItems.SLEEP_DART.get());
    }
}
