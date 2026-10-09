package com.mcspacewizard.emergentstealth.entity;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.registry.ESEntities;
import com.mcspacewizard.emergentstealth.registry.ESItems;
import com.mcspacewizard.emergentstealth.tool.ArrowEffects;
import com.mcspacewizard.emergentstealth.tool.ToolEffects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fire arrow (design doc 21 §2): flies burning. Where it lands it relights unlit torches, lanterns, campfires
 * and candles within 1.5 blocks and sets a flammable block alight; an entity it hits burns for 3 s. Water or
 * rain puts the arrow out on the way, and then it lands as a plain arrow. A spent fire arrow is picked up as a
 * plain arrow.
 */
public class FireArrow extends AbstractArrow {
    /** How long a struck entity burns. */
    public static final float BURN_SECONDS = 3.0F;
    private static final int FLIGHT_FIRE_TICKS = 20 * 60;

    public FireArrow(EntityType<? extends FireArrow> type, Level level) {
        super(type, level);
    }

    public FireArrow(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
        super(ESEntities.FIRE_ARROW.get(), owner, level, pickup, weapon);
        this.setRemainingFireTicks(FLIGHT_FIRE_TICKS);
    }

    public FireArrow(Level level, double x, double y, double z, ItemStack pickup) {
        super(ESEntities.FIRE_ARROW.get(), x, y, z, level, pickup, null);
        this.setRemainingFireTicks(FLIGHT_FIRE_TICKS);
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        boolean burning = this.isOnFire();
        super.onHitBlock(hitResult);
        if (this.level() instanceof ServerLevel level) {
            if (burning) {
                Vec3 at = hitResult.getLocation().add(hitResult.getDirection().getUnitVec3().scale(0.1));
                ArrowEffects.ignite(level, at, hitResult.getBlockPos(), hitResult.getDirection(), this);
            }
            this.clearFire();
            this.setPickupItemStack(new ItemStack(Items.ARROW));
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        Entity target = hitResult.getEntity();
        boolean burning = this.isOnFire();
        int before = target.getRemainingFireTicks();
        super.onHitEntity(hitResult);
        if (burning && this.level() instanceof ServerLevel && target.getRemainingFireTicks() > before) {
            // Vanilla sets 5 s for a burning arrow; ours burns briefly.
            target.setRemainingFireTicks(Math.max(before, Math.round(BURN_SECONDS * 20)));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() && !this.isInGround() && this.isOnFire()) {
            ToolEffects.fireTrail(this.level(), this);
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ESItems.FIRE_ARROW.get());
    }
}
