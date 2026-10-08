package com.mcspacewizard.emergentstealth.tool;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.brain.AlertState;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Sleep-dart effects (design doc 21 §2).
 * <ul>
 *   <li>A stealth NPC staggers for {@link #STAGGER_TICKS} (heavy slowness, a swaying head, drowsy particles),
 *       then is {@link StealthNpc#knockOut knocked out}. An NPC in {@link AlertState#COMBAT combat} when hit
 *       only staggers.</li>
 *   <li>Players only get a brief slowness; other mobs a short stagger.</li>
 * </ul>
 * The stagger is a saved attachment with a game-time deadline, so it survives a reload.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class Darts {
    private Darts() {}

    public static final int STAGGER_TICKS = 60;
    public static final int PLAYER_SLOW_TICKS = 30;

    /** Whether a dart hit on an NPC in this state ends in a knockout. */
    public static boolean knocksOut(AlertState state) {
        return state != AlertState.COMBAT;
    }

    /** Applies a dart hit to {@code target}. */
    public static void hit(ServerLevel level, LivingEntity target, @Nullable Entity shooter) {
        if (target instanceof StealthNpc npc) {
            if (npc.isBody() || !npc.getData(ESAttachments.STAGGER).isNone()) {
                return;
            }
            boolean knockOut = knocksOut(npc.stealthBrain().state());
            npc.setData(ESAttachments.STAGGER, new Stagger(level.getGameTime() + STAGGER_TICKS, knockOut));
            npc.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, 3, false, false));
        } else if (target instanceof Player) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PLAYER_SLOW_TICKS, 0, false, true));
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, 2, false, true));
        }
    }

    /** True while the NPC is staggering from a dart. */
    public static boolean isStaggered(StealthNpc npc) {
        Stagger stagger = npc.getExistingDataOrNull(ESAttachments.STAGGER);
        return stagger != null && !stagger.isNone();
    }

    @SubscribeEvent
    static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof StealthNpc npc && npc.level() instanceof ServerLevel level) {
            Stagger stagger = npc.getExistingDataOrNull(ESAttachments.STAGGER);
            if (stagger != null && !stagger.isNone()) {
                tick(level, npc, stagger);
            }
        }
    }

    private static void tick(ServerLevel level, StealthNpc npc, Stagger stagger) {
        long now = level.getGameTime();
        if (npc.isBody()) {
            npc.setData(ESAttachments.STAGGER, Stagger.NONE);
            return;
        }
        if (now >= stagger.until()) {
            npc.setData(ESAttachments.STAGGER, Stagger.NONE);
            if (stagger.knockOut()) {
                npc.knockOut(level, null);
            }
            return;
        }
        // The wobble: the head sways and the body drifts off its heading; drowsy particles over the head.
        float sway = Mth.sin(now * 0.45F) * 25.0F;
        npc.setYHeadRot(npc.yBodyRot + sway);
        npc.setXRot(10.0F + Mth.sin(now * 0.3F) * 12.0F);
        if (now % 6 == 0) {
            level.sendParticles(ParticleTypes.ENCHANT, npc.getX(), npc.getEyeY() + 0.4, npc.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
        }
    }
}
