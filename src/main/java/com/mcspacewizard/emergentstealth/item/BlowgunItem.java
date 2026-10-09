package com.mcspacewizard.emergentstealth.item;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.registry.ESItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Blowgun (design doc 21 §2): a bow with a short draw that fires sleep darts. Silent: no noise event at all
 * (S-03); the puff is a quiet local sound only.
 */
public class BlowgunItem extends BowItem {
    /** Ticks to a full-power puff (a bow takes 20). */
    public static final int DRAW_TICKS = 8;
    /** Dart speed at full power (a bow's arrow is 3.0). */
    public static final float DART_SPEED = 2.2F;
    public static final Predicate<ItemStack> DART_ONLY = stack -> stack.is(ESItems.SLEEP_DART.get());

    public BlowgunItem(Properties properties) {
        super(properties);
    }

    public static float powerForTime(int timeHeld) {
        float pow = timeHeld / (float) DRAW_TICKS;
        pow = (pow * pow + pow * 2.0F) / 3.0F;
        return Math.min(pow, 1.0F);
    }

    @Override
    public boolean releaseUsing(ItemStack itemStack, Level level, LivingEntity entity, int remainingTime) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        ItemStack projectile = player.getProjectile(itemStack);
        if (projectile.isEmpty()) {
            return false;
        }
        int timeHeld = net.neoforged.neoforge.event.EventHooks.onArrowLoose(itemStack, level, player,
                this.getUseDuration(itemStack, entity) - remainingTime, true);
        if (timeHeld < 0) {
            return false;
        }
        float pow = powerForTime(timeHeld);
        if (pow < 0.1F) {
            return false;
        }
        List<ItemStack> fired = draw(itemStack, projectile, player);
        if (level instanceof ServerLevel serverLevel && !fired.isEmpty()) {
            this.shoot(serverLevel, player, player.getUsedItemHand(), itemStack, fired, pow * DART_SPEED, 0.5F, false, null);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
                0.2F, 1.8F + level.getRandom().nextFloat() * 0.2F);
        player.awardStat(Stats.ITEM_USED.get(this));
        return true;
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return DART_ONLY;
    }

    @Override
    public ItemStack getDefaultCreativeAmmo(@Nullable Player player, ItemStack weapon) {
        return new ItemStack(ESItems.SLEEP_DART.get());
    }

    @Override
    public int getDefaultProjectileRange() {
        return 12;
    }
}
