package com.mcspacewizard.emergentstealth.entity;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.registry.ESEntities;
import com.mcspacewizard.emergentstealth.registry.ESItems;
import com.mcspacewizard.emergentstealth.tool.ArrowEffects;

import com.mcspacewizard.emergentstealth.tool.ToolEffects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Water arrow (design doc 21 §2): bursts on impact, putting out lights within 1.5 blocks and any fire, with a
 * soft splash (noise 2). Harmless to whoever it hits (it only douses them). Used up on impact.
 */
public class WaterArrow extends AbstractArrow {
    public WaterArrow(EntityType<? extends WaterArrow> type, Level level) {
        super(type, level);
    }

    public WaterArrow(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
        super(ESEntities.WATER_ARROW.get(), owner, level, pickup, weapon);
    }

    public WaterArrow(Level level, double x, double y, double z, ItemStack pickup) {
        super(ESEntities.WATER_ARROW.get(), x, y, z, level, pickup, null);
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        if (this.level() instanceof ServerLevel level) {
            // Just outside the struck face, so the splash is on the shooter's side of the wall.
            Vec3 at = hitResult.getLocation().add(hitResult.getDirection().getUnitVec3().scale(0.1));
            ArrowEffects.splash(level, at, this);
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        if (this.level() instanceof ServerLevel level) {
            hitResult.getEntity().clearFire();
            ArrowEffects.splash(level, hitResult.getLocation(), this);
            this.discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() && !this.isInGround() && !this.isRemoved()) {
            ToolEffects.waterTrail(this.level(), this);
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ESItems.WATER_ARROW.get());
    }
}
