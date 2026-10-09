package com.mcspacewizard.emergentstealth.item;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Ammo that spawns its own arrow entity (water arrow, fire arrow, sleep dart; design doc 21 §2). Arrows are in
 * {@code #minecraft:arrows}, so the vanilla bow and crossbow fire them; darts only fit the blowgun.
 * Dispensers fire them too.
 */
public class SpecialArrowItem extends ArrowItem {
    /** Fired by a shooter. */
    @FunctionalInterface
    public interface ShotFactory {
        AbstractArrow create(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon);
    }

    /** Spawned at a position (dispensers). */
    @FunctionalInterface
    public interface PlacedFactory {
        AbstractArrow create(Level level, double x, double y, double z, ItemStack pickup);
    }

    private final ShotFactory shot;
    private final PlacedFactory placed;

    public SpecialArrowItem(Properties properties, ShotFactory shot, PlacedFactory placed) {
        super(properties);
        this.shot = shot;
        this.placed = placed;
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack itemStack, LivingEntity owner, @Nullable ItemStack firedFromWeapon) {
        return shot.create(level, owner, itemStack.copyWithCount(1), firedFromWeapon);
    }

    @Override
    public Projectile asProjectile(Level level, Position position, ItemStack itemStack, Direction direction) {
        AbstractArrow arrow = placed.create(level, position.x(), position.y(), position.z(), itemStack.copyWithCount(1));
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        return arrow;
    }
}
