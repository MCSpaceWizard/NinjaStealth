package com.mcspacewizard.emergentstealth.ai.brain;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.ai.behaviour.BehaviourRunner;
import com.mcspacewizard.emergentstealth.ai.behaviour.BehaviourTree;
import com.mcspacewizard.emergentstealth.ai.group.AttackTokens;
import com.mcspacewizard.emergentstealth.ai.group.SearchGroups;
import com.mcspacewizard.emergentstealth.ai.perception.NpcPerception;
import com.mcspacewizard.emergentstealth.ai.perception.TargetAwareness;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.stealth.sound.HeardNoise;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseEvent;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseKind;

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
 * The NPC's knowledge and alert state machine (design doc 14 §1-2). Perception (sight, hearing, being hurt)
 * updates what the NPC knows; the rules here (A-01, D-08) turn that into an {@link AlertState}; the NPC's
 * {@link BehaviourTree} (datapack) decides what to do about it.
 */
public final class StealthBrain {
    /** How long a seen target keeps a guard in combat after it slips out of view (then it hunts). */
    private static final long COMBAT_MEMORY_TICKS = 30;
    /** How long being suspicious lasts before the NPC walks over to look. */
    private static final long SUSPICIOUS_TO_INVESTIGATE_TICKS = 30;
    /** Investigations give up after this long even if the NPC never gets there. */
    private static final long INVESTIGATE_MAX_TICKS = 20 * 25;
    /** How long an NPC looks around after reaching what it was investigating. */
    private static final long INVESTIGATE_LOOK_TICKS = 20 * 4;
    /** Non-combatants stop fleeing after this long without seeing the threat. */
    private static final long FLEE_TICKS = 20 * 12;
    /** How long an NPC stays on heightened alert after an incident (A-04: a few Minecraft days). */
    private static final long HEIGHTENED_TICKS = 24000L * 3;
    /** Guesses about where a ranged attacker is are this far back along the projectile's path. */
    private static final double RANGED_GUESS_DISTANCE = 8.0;
    /** A quiet unexplained noise holds the NPC's attention this long. */
    private static final long NOISE_CURIOUS_TICKS = 20 * 3;
    /** Noises at least this strong get guards to walk over (design doc 16 §4). */
    private static final float NOISE_INVESTIGATE_STRENGTH = 0.5F;
    /** Within this distance a point of interest counts as reached. */
    private static final double ARRIVE_DISTANCE = 2.5;

    private final StealthNpc npc;
    private final BehaviourRunner behaviour = new BehaviourRunner();
    private @Nullable BehaviourTree treeOverride;

    private AlertState state = AlertState.UNAWARE;
    private long stateSince;
    private @Nullable UUID alertTarget;
    private long heightenedUntil;
    private long arrivedTick = -1;
    private PoiCause cause = PoiCause.NONE;

    // Unexplained noise (a thrown item, a shout): where it was and how strongly it was heard.
    private @Nullable Vec3 noisePos;
    private float noiseStrength;
    private long noiseTick = Long.MIN_VALUE;
    private PoiCause noiseCause = PoiCause.HEARD;

    // Debug and bark bookkeeping.
    private @Nullable HeardNoise lastHeard;
    private long lastHeardTick = Long.MIN_VALUE;
    private long lastShoutTick = Long.MIN_VALUE;
    long lastBarkTick = Long.MIN_VALUE;
    String lastBark = "";

    public StealthBrain(StealthNpc npc) {
        this.npc = npc;
    }

    // ------------------------------------------------------------------------------------------------
    // Queries (behaviour-tree conditions, actions and the debug view)

    public AlertState state() {
        return state;
    }

    /** Game time the current state started: one "episode" for once-per-state nodes. */
    public long stateSince() {
        return stateSince;
    }

    public @Nullable UUID alertTarget() {
        return alertTarget;
    }

    public PoiCause cause() {
        return cause;
    }

    public float perceptionMultiplier() {
        return state.perceptionMultiplier();
    }

    public String lastBark() {
        return lastBark;
    }

    public String activeBehaviour() {
        return behaviour.activeLabel();
    }

    public @Nullable HeardNoise lastHeard(long now) {
        return now - lastHeardTick < 60 ? lastHeard : null;
    }

    /** For tests and commands: use this tree instead of the archetype's. */
    public void setTreeOverride(@Nullable BehaviourTree tree) {
        this.treeOverride = tree;
    }

    public void noteShout(long now) {
        lastShoutTick = now;
    }

    public long lastShoutTick() {
        return lastShoutTick;
    }

    /** The awareness record of whoever the NPC is most concerned with. */
    private @Nullable TargetAwareness concern() {
        UUID id = alertTarget != null ? alertTarget : npc.perception().focus();
        return id == null ? null : npc.perception().get(id);
    }

    /**
     * Where the NPC is interested in: the newest clue. That's its target's last known position (seen or heard),
     * or the spot an unexplained noise came from, whichever is more recent. Never where the target really is
     * unless it's seen right now (D-08).
     */
    public @Nullable Vec3 pointOfInterest() {
        TargetAwareness target = concern();
        Vec3 lastKnown = target == null ? null : target.lastKnownPos();
        long clueTick = target == null || lastKnown == null ? Long.MIN_VALUE : target.lastClueTick();
        if (noisePos != null && noiseTick > clueTick) {
            return noisePos;
        }
        return lastKnown;
    }

    public boolean canSeeTarget() {
        TargetAwareness target = concern();
        return target != null && target.seenNow();
    }

    /** The alert target as an entity, if it's still around and targetable. */
    public @Nullable LivingEntity targetEntity(ServerLevel level) {
        if (alertTarget == null) {
            return null;
        }
        Entity entity = level.getEntity(alertTarget);
        return entity instanceof LivingEntity living && living.isAlive()
                && !(living instanceof Player player && !NpcPerception.isTargetable(player)) ? living : null;
    }

    // ------------------------------------------------------------------------------------------------
    // Tick

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
        if (focus != null && focus.seenNow()) {
            cause = PoiCause.SEEN;
        }
        if (noisePos != null && state != AlertState.INVESTIGATING && now - noiseTick > NOISE_CURIOUS_TICKS
                && !(state.isAlerted())) {
            noisePos = null;
        }

        AlertState next;
        if (alertTarget != null) {
            next = alertedState(level, now, combatant, perception.get(alertTarget));
        } else if (awareness >= ESConfig.SUSPICIOUS_THRESHOLD.get()) {
            boolean readyToInvestigate = state == AlertState.INVESTIGATING
                    || (state == AlertState.SUSPICIOUS && now - stateSince >= SUSPICIOUS_TO_INVESTIGATE_TICKS);
            next = readyToInvestigate && combatant ? AlertState.INVESTIGATING : AlertState.SUSPICIOUS;
        } else if (state == AlertState.INVESTIGATING && !investigationDone(now)) {
            next = AlertState.INVESTIGATING;
        } else if (awareness >= ESConfig.NOTICED_THRESHOLD.get()) {
            next = AlertState.CURIOUS;
        } else if (noisePos != null && state != AlertState.INVESTIGATING) {
            next = combatant && noiseStrength >= NOISE_INVESTIGATE_STRENGTH ? AlertState.INVESTIGATING : AlertState.CURIOUS;
        } else {
            if (state == AlertState.INVESTIGATING) {
                noisePos = null;
            }
            next = baseline(now);
        }
        setState(level, next, now);
        tickBehaviour(level, now);
    }

    /** An investigation ends after looking around at the spot for a while, or when it takes too long. */
    private boolean investigationDone(long now) {
        if (now - stateSince > INVESTIGATE_MAX_TICKS) {
            return true;
        }
        Vec3 poi = pointOfInterest();
        boolean there = poi == null || npc.position().distanceToSqr(poi) <= ARRIVE_DISTANCE * ARRIVE_DISTANCE
                || (npc.getNavigation().isDone() && now - stateSince > 40);
        if (!there) {
            arrivedTick = -1;
            return false;
        }
        if (arrivedTick < 0) {
            arrivedTick = now;
        }
        return now - arrivedTick > INVESTIGATE_LOOK_TICKS;
    }

    private AlertState alertedState(ServerLevel level, long now, boolean combatant, @Nullable TargetAwareness target) {
        long since = target == null ? Long.MAX_VALUE : target.ticksSincePerceived(now);
        if (!combatant) {
            if (since > FLEE_TICKS) {
                return endAlert(level, now);
            }
            return AlertState.FLEEING;
        }
        if (target != null && since <= COMBAT_MEMORY_TICKS && targetEntity(level) != null) {
            return AlertState.COMBAT;
        }
        Vec3 poi = pointOfInterest();
        if (state == AlertState.COMBAT) {
            return AlertState.HUNTING;
        }
        if (state == AlertState.HUNTING) {
            boolean arrived = poi == null || npc.position().distanceToSqr(poi) < 2.0 * 2.0
                    || (npc.getNavigation().isDone() && now - stateSince > 40);
            return arrived ? AlertState.SEARCHING : AlertState.HUNTING;
        }
        if (state == AlertState.SEARCHING) {
            // A fresh clue (heard the target, or a shout or noise) sends the guard there.
            boolean freshClue = (target != null && target.lastClueTick() > stateSince) || (noisePos != null && noiseTick > stateSince);
            if (freshClue) {
                return AlertState.HUNTING;
            }
            SearchGroups.Group group = SearchGroups.get(level).groupOf(npc);
            if (group != null && now < group.endTick()) {
                return AlertState.SEARCHING;
            }
            return endAlert(level, now);
        }
        // Freshly alerted without a sighting (e.g. hit by an arrow): go and look.
        return AlertState.HUNTING;
    }

    private AlertState endAlert(ServerLevel level, long now) {
        alertTarget = null;
        noisePos = null;
        heightenedUntil = now + HEIGHTENED_TICKS;
        return baseline(now);
    }

    private AlertState baseline(long now) {
        return now < heightenedUntil ? AlertState.HEIGHTENED : AlertState.UNAWARE;
    }

    private void setState(ServerLevel level, AlertState next, long now) {
        if (next == state) {
            return;
        }
        AlertState previous = state;
        state = next;
        stateSince = now;
        arrivedTick = -1;
        npc.getNavigation().stop();
        behaviour.reset();

        if (previous == AlertState.SEARCHING) {
            SearchGroups.get(level).leave(npc);
        }
        if (previous == AlertState.COMBAT) {
            AttackTokens.get(level).release(npc);
        }
        if (next == AlertState.SEARCHING) {
            Vec3 center = pointOfInterest();
            SearchGroups.get(level).join(level, npc, center != null ? center : npc.position(), alertTarget, now);
        }
        if (!next.isActive()) {
            // Calming down. Anything beyond a glance leaves the NPC on heightened alert.
            if (previous != AlertState.CURIOUS && previous.isActive()) {
                heightenedUntil = Math.max(heightenedUntil, now + HEIGHTENED_TICKS);
                if (npc.isCombatant() && npc.getRandom().nextFloat() < 0.7F) {
                    Barks.say(level, npc, "give_up");
                }
            }
            cause = PoiCause.NONE;
        }
    }

    private void tickBehaviour(ServerLevel level, long now) {
        boolean melee = false;
        if (state.isActive()) {
            BehaviourTree tree = treeOverride != null ? treeOverride : npc.getBehaviourTree();
            melee = behaviour.tick(npc, level, tree, now);
        }
        // Vanilla's melee goal follows getTarget(): only set while an attack action runs on a seen target.
        LivingEntity target = melee && state == AlertState.COMBAT ? targetEntity(level) : null;
        if (npc.getTarget() != target) {
            npc.setTarget(target);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Stimuli

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
        cause = PoiCause.HURT;
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

    /**
     * Hearing (design doc 16 §4). A noise that gives a player away raises awareness of them (capped: only sight
     * detects) and points at where the noise was. Anything else (a thrown item, a shout) points at a spot.
     */
    public void onNoise(ServerLevel level, HeardNoise heard) {
        long now = level.getGameTime();
        NoiseEvent noise = heard.event();
        lastHeard = heard;
        lastHeardTick = now;
        if (noise.cause() != null) {
            Player player = level.getPlayerByUUID(noise.cause());
            if (player == null || !NpcPerception.isTargetable(player)) {
                return;
            }
            float gain = heard.intensity() * ESConfig.HEARING_GAIN.get().floatValue() * state.perceptionMultiplier();
            npc.perception().getOrCreate(player.getUUID())
                    .hear(noise.pos(), gain, ESConfig.HEARING_AWARENESS_CAP.get().floatValue(), now);
            if (cause != PoiCause.SEEN || !canSeeTarget()) {
                cause = PoiCause.HEARD;
            }
            return;
        }
        float strength = heard.intensity() * (noise.kind() == NoiseKind.SHOUT || noise.kind() == NoiseKind.EXPLOSION ? 1.5F : 1.0F);
        boolean replaces = noisePos == null || now - noiseTick > 40 || strength >= noiseStrength * 0.8F;
        if (replaces) {
            noisePos = noise.pos();
            noiseStrength = strength;
            noiseTick = now;
            noiseCause = noise.kind() == NoiseKind.SHOUT ? PoiCause.SHOUT : PoiCause.HEARD;
            if (!canSeeTarget()) {
                cause = noiseCause;
            }
        }
    }

    /** The NPC is gone (died, unloaded): leave groups and give back tokens. */
    public void onRemoved(ServerLevel level) {
        SearchGroups.get(level).leave(npc);
        AttackTokens.get(level).release(npc);
    }

    // ------------------------------------------------------------------------------------------------
    // Persistence

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
