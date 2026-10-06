package com.mcspacewizard.emergentstealth.ai.brain;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.ai.perception.NpcPerception;
import com.mcspacewizard.emergentstealth.ai.perception.TargetAwareness;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Stage 4a "v0" brain: turns perception (awareness + last known position) into an {@link AlertState} and a
 * movement intent that {@link StealthActionGoal} executes. A data-driven behaviour-tree brain replaces the
 * decision logic in S4 proper; the states and the no-free-information rule stay.
 */
public final class StealthBrain {
    /** How long a seen target keeps a guard in combat after it slips out of view (then it hunts). */
    private static final long COMBAT_MEMORY_TICKS = 30;
    /** How long being suspicious lasts before the NPC walks over to look. */
    private static final long SUSPICIOUS_TO_INVESTIGATE_TICKS = 30;
    /** Investigations give up after this long even if awareness hasn't decayed. */
    private static final long INVESTIGATE_MAX_TICKS = 20 * 25;
    /** Non-combatants stop fleeing after this long without seeing the threat. */
    private static final long FLEE_TICKS = 20 * 12;
    /** How long an NPC stays on heightened alert after an incident (A-04: a few Minecraft days). */
    private static final long HEIGHTENED_TICKS = 24000L * 3;
    /** Guesses about where a ranged attacker is are this far back along the projectile's path. */
    private static final double RANGED_GUESS_DISTANCE = 8.0;

    private final StealthNpc npc;
    private AlertState state = AlertState.UNAWARE;
    private long stateSince;
    private @Nullable UUID alertTarget;
    private @Nullable Vec3 searchCenter;
    private long searchUntil;
    private long heightenedUntil;

    public StealthBrain(StealthNpc npc) {
        this.npc = npc;
    }

    public AlertState state() {
        return state;
    }

    public long stateSince() {
        return stateSince;
    }

    public @Nullable UUID alertTarget() {
        return alertTarget;
    }

    public @Nullable Vec3 searchCenter() {
        return searchCenter;
    }

    public float perceptionMultiplier() {
        return state.perceptionMultiplier();
    }

    /** The position the NPC is currently interested in: its focus target's last known position. */
    public @Nullable Vec3 pointOfInterest() {
        UUID id = alertTarget != null ? alertTarget : npc.perception().focus();
        TargetAwareness awareness = id == null ? null : npc.perception().get(id);
        return awareness == null ? null : awareness.lastKnownPos();
    }

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        NpcPerception perception = npc.perception();
        boolean combatant = npc.isCombatant();

        UUID focusId = perception.focus();
        TargetAwareness focus = focusId == null ? null : perception.get(focusId);
        float awareness = focus == null ? 0.0F : focus.awareness();
        if (awareness >= 1.0F) {
            alertTarget = focusId;
        }

        AlertState next;
        if (alertTarget != null) {
            next = alertedState(level, now, combatant, perception.get(alertTarget));
        } else if (awareness >= ESConfig.SUSPICIOUS_THRESHOLD.get()) {
            boolean readyToInvestigate = state == AlertState.INVESTIGATING
                    || (state == AlertState.SUSPICIOUS && now - stateSince >= SUSPICIOUS_TO_INVESTIGATE_TICKS);
            next = readyToInvestigate && combatant ? AlertState.INVESTIGATING : AlertState.SUSPICIOUS;
        } else if (awareness >= ESConfig.NOTICED_THRESHOLD.get()) {
            next = state == AlertState.INVESTIGATING ? AlertState.INVESTIGATING : AlertState.CURIOUS;
        } else if (state == AlertState.INVESTIGATING && now - stateSince < INVESTIGATE_MAX_TICKS && !npc.getNavigation().isDone()) {
            next = AlertState.INVESTIGATING;
        } else if (state.isActive()) {
            // Calming down. Anything beyond a glance leaves the NPC on heightened alert.
            if (state != AlertState.CURIOUS) {
                heightenedUntil = now + HEIGHTENED_TICKS;
            }
            next = baseline(now);
        } else {
            next = baseline(now);
        }
        setState(next, now);
        updateTarget(level);
    }

    private AlertState alertedState(ServerLevel level, long now, boolean combatant, @Nullable TargetAwareness target) {
        long since = target == null ? Long.MAX_VALUE : target.ticksSincePerceived(now);
        if (!combatant) {
            if (since > FLEE_TICKS) {
                return endAlert(now);
            }
            return AlertState.FLEEING;
        }
        if (target != null && since <= COMBAT_MEMORY_TICKS && targetEntity(level) != null) {
            return AlertState.COMBAT;
        }
        if (state == AlertState.COMBAT || state == AlertState.HUNTING) {
            Vec3 lastKnown = target == null ? null : target.lastKnownPos();
            boolean arrived = lastKnown == null || npc.position().distanceToSqr(lastKnown) < 4.0
                    || (state == AlertState.HUNTING && npc.getNavigation().isDone() && now - stateSince > 20);
            if (!arrived) {
                return AlertState.HUNTING;
            }
            searchCenter = lastKnown != null ? lastKnown : npc.position();
            searchUntil = now + ESConfig.SEARCH_SECONDS.get() * 20L;
            return AlertState.SEARCHING;
        }
        if (state == AlertState.SEARCHING && now < searchUntil) {
            return AlertState.SEARCHING;
        }
        if (state != AlertState.SEARCHING) {
            // Freshly alerted without a sighting (e.g. hit by an arrow): go and look.
            return AlertState.HUNTING;
        }
        return endAlert(now);
    }

    private AlertState endAlert(long now) {
        alertTarget = null;
        searchCenter = null;
        heightenedUntil = now + HEIGHTENED_TICKS;
        return baseline(now);
    }

    private AlertState baseline(long now) {
        return now < heightenedUntil ? AlertState.HEIGHTENED : AlertState.UNAWARE;
    }

    private void setState(AlertState next, long now) {
        if (next != state) {
            state = next;
            stateSince = now;
            npc.getNavigation().stop();
        }
    }

    /** Vanilla's melee goal follows {@code getTarget()}, so only keep a target while it's actually seen. */
    private void updateTarget(ServerLevel level) {
        LivingEntity target = state == AlertState.COMBAT ? targetEntity(level) : null;
        if (npc.getTarget() != target) {
            npc.setTarget(target);
        }
    }

    private @Nullable LivingEntity targetEntity(ServerLevel level) {
        if (alertTarget == null) {
            return null;
        }
        Entity entity = level.getEntity(alertTarget);
        return entity instanceof LivingEntity living && living.isAlive()
                && !(living instanceof Player player && !NpcPerception.isTargetable(player)) ? living : null;
    }

    /**
     * Being hurt is a stimulus (not free information): melee reveals the attacker; a projectile only
     * reveals the direction it came from.
     */
    public void onHurt(ServerLevel level, DamageSource source) {
        Entity attacker = source.getEntity();
        if (!(attacker instanceof Player player) || !NpcPerception.isTargetable(player)) {
            return;
        }
        long now = level.getGameTime();
        TargetAwareness awareness = npc.perception().getOrCreate(player.getUUID());
        Entity direct = source.getDirectEntity();
        if (direct == null || direct == attacker) {
            awareness.stimulate(attacker.position(), 1.0F, now);
            alertTarget = player.getUUID();
        } else {
            Vec3 motion = direct.getDeltaMovement();
            Vec3 back = motion.lengthSqr() > 1.0E-6 ? motion.normalize().scale(-RANGED_GUESS_DISTANCE) : Vec3.ZERO;
            Vec3 guess = npc.position().add(back).add(
                    Mth.nextDouble(npc.getRandom(), -2.0, 2.0), 0.0, Mth.nextDouble(npc.getRandom(), -2.0, 2.0));
            awareness.stimulate(guess, Math.max(ESConfig.SUSPICIOUS_THRESHOLD.get().floatValue(), 0.6F), now);
        }
    }

    public void save(ValueOutput output) {
        output.putString("AlertState", state.name());
        output.putLong("HeightenedUntil", heightenedUntil);
    }

    public void load(ValueInput input) {
        heightenedUntil = input.getLongOr("HeightenedUntil", 0L);
        // Active states need perception memory we don't persist; resume from the calm baseline.
        String saved = input.getStringOr("AlertState", AlertState.UNAWARE.name());
        state = AlertState.HEIGHTENED.name().equals(saved) ? AlertState.HEIGHTENED : AlertState.UNAWARE;
    }
}
