package com.mcspacewizard.emergentstealth.tool;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESParticles;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Blinding powder's puff (design doc 21 §2): a 4-block, 60° cone from the user's eyes. NPCs caught in it are blinded
 * for 6 s (they see nothing, but still hear) and stagger: slowed, stopping and turning at random.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class Blinding {
    private Blinding() {}

    public static final double RANGE = 4.0;
    /** Half the cone's opening angle (60° in all). */
    public static final double HALF_ANGLE_DEGREES = 30.0;
    public static final int BLIND_TICKS = 120;
    private static final double COS_HALF_ANGLE = Math.cos(Math.toRadians(HALF_ANGLE_DEGREES));
    private static final int STAGGER_INTERVAL = 8;

    /** Staggering NPCs and when they recover (game time). Server thread. */
    private static final Map<StealthNpc, Long> STAGGERING = new WeakHashMap<>();

    /**
     * Puffs powder from {@code user}'s eyes along their look direction.
     *
     * @return the entities that were caught in the cone
     */
    public static List<LivingEntity> puff(ServerLevel level, LivingEntity user) {
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getLookAngle();
        long now = level.getGameTime();
        List<LivingEntity> caught = new ArrayList<>();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(RANGE + 1.0),
                e -> e != user && e.isAlive() && !(e instanceof Player))) {
            if (!inCone(level, user, eye, look, target)) {
                continue;
            }
            caught.add(target);
            if (target instanceof StealthNpc npc) {
                if (npc.isBody()) {
                    continue;
                }
                npc.blind(now + BLIND_TICKS);
                stagger(npc, now + BLIND_TICKS);
            } else if (target instanceof Mob) {
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BLIND_TICKS, 1));
            }
        }
        effects(level, eye, look);
        return caught;
    }

    /** Whether the target's eyes or body centre are within the cone and not behind a wall. */
    public static boolean inCone(ServerLevel level, Entity user, Vec3 eye, Vec3 look, LivingEntity target) {
        return pointInCone(level, user, eye, look, target.getEyePosition())
                || pointInCone(level, user, eye, look, target.getBoundingBox().getCenter());
    }

    private static boolean pointInCone(ServerLevel level, Entity user, Vec3 eye, Vec3 look, Vec3 point) {
        Vec3 to = point.subtract(eye);
        double dist = to.length();
        if (dist > RANGE) {
            return false;
        }
        if (dist > 0.3 && to.dot(look) / dist < COS_HALF_ANGLE) {
            return false;
        }
        return level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user)).getType()
                == HitResult.Type.MISS;
    }

    /** The NPC stumbles about until {@code until}: slowed, and stops and turns at random every few ticks. */
    public static void stagger(StealthNpc npc, long until) {
        STAGGERING.merge(npc, until, Math::max);
        npc.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) Math.max(1, until - npc.level().getGameTime()), 2));
        npc.getNavigation().stop();
    }

    public static boolean isStaggering(StealthNpc npc, long now) {
        Long until = STAGGERING.get(npc);
        return until != null && now < until;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || STAGGERING.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<StealthNpc, Long>> it = STAGGERING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<StealthNpc, Long> entry = it.next();
            StealthNpc npc = entry.getKey();
            if (npc.isRemoved() || !npc.isAlive() || now >= entry.getValue()) {
                it.remove();
                continue;
            }
            if (npc.level() != level || npc.isBody() || (now + npc.getId()) % STAGGER_INTERVAL != 0) {
                continue;
            }
            var random = npc.getRandom();
            npc.getNavigation().stop();
            float yaw = npc.getYRot() + Mth.nextFloat(random, -60.0F, 60.0F);
            npc.setYRot(yaw);
            npc.setYHeadRot(yaw);
            npc.setYBodyRot(yaw);
            npc.push(Mth.nextDouble(random, -0.12, 0.12), 0.0, Mth.nextDouble(random, -0.12, 0.12));
            if (random.nextInt(3) == 0) {
                npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        }
    }

    private static void effects(ServerLevel level, Vec3 eye, Vec3 look) {
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 0.9F, 1.5F);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.6F, 1.8F);
        Vec3 start = eye.add(look.scale(0.6)).add(0.0, -0.2, 0.0);
        var random = level.getRandom();
        for (int i = 0; i < 28; i++) {
            // A random direction inside the cone.
            Vec3 dir = look.add(random.nextGaussian() * 0.22, random.nextGaussian() * 0.18, random.nextGaussian() * 0.22).normalize();
            double speed = 0.18 + random.nextDouble() * 0.17;
            level.sendParticles(ESParticles.BLINDING_PUFF, start.x, start.y, start.z, 0, dir.x, dir.y, dir.z, speed);
        }
    }
}
