package com.mcspacewizard.emergentstealth.stealth.light;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Light carried by entities: anything held that is a light-emitting block (torch 14, lantern 15, ...), and
 * burning entities. Positions are approximate hand height. Vanilla doesn't render these; perception and the
 * light gem treat them as real lights (L-02).
 */
public final class DynamicLights {
    private DynamicLights() {}

    /** A moving light: position, emission (1-15) and the entity carrying it (whose own block is skipped). */
    public record Light(Vec3 pos, int emission, Entity carrier) {}

    private static final double SEARCH_RADIUS = 15.0;

    public static List<Light> near(ServerLevel level, Vec3 point) {
        List<Light> lights = new ArrayList<>();
        AABB box = new AABB(point, point).inflate(SEARCH_RADIUS);
        for (Entity entity : level.getEntities((Entity) null, box, e -> e.isAlive() && !e.isSpectator())) {
            int emission = emission(entity);
            if (emission > 0) {
                lights.add(new Light(lightPosition(entity), emission, entity));
            }
        }
        return lights;
    }

    /** Brightest light an entity carries, or 0. */
    public static int emission(Entity entity) {
        int emission = entity.isOnFire() ? 15 : 0;
        if (entity instanceof LivingEntity living) {
            emission = Math.max(emission, itemEmission(living.getItemInHand(InteractionHand.MAIN_HAND)));
            emission = Math.max(emission, itemEmission(living.getItemInHand(InteractionHand.OFF_HAND)));
        }
        return emission;
    }

    public static int itemEmission(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState().getLightEmission();
        }
        return 0;
    }

    private static Vec3 lightPosition(Entity entity) {
        if (entity.isOnFire() || !(entity instanceof LivingEntity)) {
            return entity.getBoundingBox().getCenter();
        }
        // Roughly hand height, slightly in front of the body.
        Vec3 forward = Vec3.directionFromRotation(0.0F, entity.getYRot()).scale(0.3);
        return entity.position().add(forward).add(0.0, entity.getBbHeight() * 0.55, 0.0);
    }
}
