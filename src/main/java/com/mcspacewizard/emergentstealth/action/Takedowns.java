package com.mcspacewizard.emergentstealth.action;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.brain.AlertState;
import com.mcspacewizard.emergentstealth.data.Archetype;
import com.mcspacewizard.emergentstealth.data.NpcRole;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.registry.ESRegistries;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseEvent;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseKind;
import com.mcspacewizard.emergentstealth.stealth.sound.Noises;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Takedowns (design doc 17 §3). Rear: left-click (lethal) or empty-hand right-click (non-lethal) on an NPC from
 * behind that isn't fighting or hunting you. Air: fall onto an NPC from 2+ blocks. The server validates, snaps
 * the attacker behind the victim, locks both in a synced {@link ActionPlayback} and applies the outcome at the
 * impact tick.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class Takedowns {
    private Takedowns() {}

    public static final Identifier REAR_NONLETHAL = EmergentStealth.id("rear_nonlethal");
    public static final Identifier REAR_LETHAL = EmergentStealth.id("rear_lethal");
    public static final Identifier AIR_NONLETHAL = EmergentStealth.id("air_nonlethal");
    public static final Identifier AIR_LETHAL = EmergentStealth.id("air_lethal");

    /** Rear takedowns reach this far (horizontal) and this much height difference. */
    public static final double REAR_REACH = 2.0;
    private static final double REAR_HEIGHT = 1.0;
    /** "Behind": more than this many degrees from where the NPC's head faces. */
    public static final double BEHIND_ANGLE = 120.0;
    /** Air takedowns need at least this much fall. */
    public static final double AIR_MIN_FALL = 2.0;
    /** The attacker is pulled back if pushed further than this from its snap position. */
    private static final double HOLD_TOLERANCE = 0.5;

    private static final class Active {
        final ServerPlayer attacker;
        final StealthNpc victim;
        final TakedownDefinition definition;
        final long start;
        final Vec3 attackerPos;
        final float yaw;
        boolean impacted;

        Active(ServerPlayer attacker, StealthNpc victim, TakedownDefinition definition, long start, Vec3 attackerPos, float yaw) {
            this.attacker = attacker;
            this.victim = victim;
            this.definition = definition;
            this.start = start;
            this.attackerPos = attackerPos;
            this.yaw = yaw;
        }
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    /** Locks the attacker in place during the action (speed and jump to zero; also stops client prediction). */
    private static final Identifier LOCK_MODIFIER = EmergentStealth.id("takedown_lock");

    private static void lock(ServerPlayer player, boolean locked) {
        for (var attribute : java.util.List.of(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH)) {
            var instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            instance.removeModifier(LOCK_MODIFIER);
            if (locked) {
                instance.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(LOCK_MODIFIER, -1.0,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Rules

    /** Why a rear takedown can't happen, or null if it can. Shared with the client prompt (as far as it knows). */
    public static @Nullable String rearBlocker(Player player, StealthNpc npc) {
        if (npc.isBody() || !npc.isAlive()) {
            return "body";
        }
        if (inAction(player) || inAction(npc)) {
            return "busy";
        }
        Vec3 offset = player.position().subtract(npc.position());
        if (offset.horizontalDistanceSqr() > REAR_REACH * REAR_REACH || Math.abs(offset.y) > REAR_HEIGHT) {
            return "range";
        }
        if (!isBehind(npc, player.position())) {
            return "front";
        }
        if (npc.getArchetype().map(Archetype::role).orElse(null) == NpcRole.ELITE) {
            return "elite";
        }
        if (!npc.level().isClientSide()) {
            AlertState state = npc.stealthBrain().state();
            boolean onYou = player.getUUID().equals(npc.stealthBrain().alertTarget());
            if (onYou && (state == AlertState.COMBAT || state == AlertState.HUNTING)) {
                return "alerted";
            }
        }
        return null;
    }

    /** Whether {@code from} is outside the NPC's frontal 2×(180 - BEHIND_ANGLE) degrees (its head's facing). */
    public static boolean isBehind(StealthNpc npc, Vec3 from) {
        Vec3 toPlayer = from.subtract(npc.position());
        double length = Math.sqrt(toPlayer.x * toPlayer.x + toPlayer.z * toPlayer.z);
        if (length < 1.0E-4) {
            return true;
        }
        double rad = Math.toRadians(npc.getYHeadRot());
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        double cos = (fx * toPlayer.x + fz * toPlayer.z) / length;
        return cos < Math.cos(Math.toRadians(BEHIND_ANGLE));
    }

    public static boolean inAction(net.minecraft.world.entity.Entity entity) {
        return entity.getData(ESAttachments.ACTION).activeAt(entity.level().getGameTime());
    }

    /** Action id played for a takedown entry: {@code ns:takedown/name}. */
    public static Identifier actionId(Identifier takedown) {
        return Identifier.fromNamespaceAndPath(takedown.getNamespace(), "takedown/" + takedown.getPath());
    }

    // ------------------------------------------------------------------------------------------------
    // Starting

    /** Starts a rear takedown if allowed. Returns whether it started. */
    public static boolean tryRear(ServerPlayer player, StealthNpc npc, boolean lethal) {
        if (rearBlocker(player, npc) != null) {
            return false;
        }
        return start(player, npc, lethal ? REAR_LETHAL : REAR_NONLETHAL);
    }

    public static boolean start(ServerPlayer player, StealthNpc victim, Identifier takedownId) {
        ServerLevel level = (ServerLevel) victim.level();
        TakedownDefinition definition = level.registryAccess().lookupOrThrow(ESRegistries.TAKEDOWN).getValue(takedownId);
        if (definition == null) {
            EmergentStealth.LOGGER.error("Missing takedown definition {}", takedownId);
            return false;
        }
        long now = level.getGameTime();
        float yaw = victim.getYHeadRot();
        victim.setYRot(yaw);
        victim.setYBodyRot(yaw);
        victim.getNavigation().stop();
        double rad = Math.toRadians(yaw);
        Vec3 behind = new Vec3(Math.sin(rad), 0.0, -Math.cos(rad)).scale(definition.attackerOffset());
        // Rear and air takedowns both end with the attacker standing just behind the victim.
        Vec3 attackerPos = new Vec3(victim.getX() + behind.x, victim.getY(), victim.getZ() + behind.z);
        player.connection.teleport(attackerPos.x, attackerPos.y, attackerPos.z, yaw, player.getXRot());
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        lock(player, true);

        Identifier action = actionId(takedownId);
        Vec3 anchor = victim.position();
        player.setData(ESAttachments.ACTION, new ActionPlayback(action, ActionPlayback.Role.ATTACKER, now, definition.duration(), anchor, yaw));
        victim.setData(ESAttachments.ACTION, new ActionPlayback(action, ActionPlayback.Role.VICTIM, now, definition.duration(), anchor, yaw));
        Active active = new Active(player, victim, definition, now, attackerPos, yaw);
        ACTIVE.put(player.getUUID(), active);
        if (definition.impactTick() == 0) {
            impact(level, active);
        }
        return true;
    }

    private static void impact(ServerLevel level, Active active) {
        active.impacted = true;
        StealthNpc victim = active.victim;
        Noises.emit(level, new NoiseEvent(victim.getEyePosition(), active.definition.noise(), NoiseKind.COMBAT,
                active.attacker.getUUID(), victim.getUUID()));
        if (active.definition.lethal()) {
            victim.becomeCorpse(level, level.damageSources().playerAttack(active.attacker));
        } else {
            victim.knockOut(level, active.attacker);
        }
    }

    private static void finish(Active active) {
        lock(active.attacker, false);
        active.attacker.setData(ESAttachments.ACTION, ActionPlayback.NONE);
        if (!active.victim.isRemoved()) {
            active.victim.setData(ESAttachments.ACTION, ActionPlayback.NONE);
        }
    }

    /** The choke was broken (the attacker got hit): the victim fights back. */
    private static void interrupt(ServerLevel level, Active active) {
        finish(active);
        if (!active.victim.isBody() && active.victim.isAlive()) {
            active.victim.stealthBrain().onHurt(level, level.damageSources().playerAttack(active.attacker));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Events

    /** Left-click from behind: lethal takedown instead of a normal hit. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof StealthNpc npc
                && tryRear(player, npc, true)) {
            event.setCanceled(true);
        }
    }

    /** Empty-hand right-click from behind: non-lethal takedown. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof StealthNpc npc) || npc.isBody()) {
            return;
        }
        if (!event.getEntity().getMainHandItem().isEmpty()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            if (tryRear(player, npc, false)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        } else if (rearBlocker(event.getEntity(), npc) == null) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** Air takedowns: falling onto an NPC from high enough. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            checkAirTakedown(player);
        }
    }

    /** Starts an air takedown if the falling player is about to land on an NPC. Returns whether one started. */
    public static boolean checkAirTakedown(ServerPlayer player) {
        if (player.onGround() || player.fallDistance < AIR_MIN_FALL || player.getDeltaMovement().y >= 0.0 || inAction(player)
                || player.isSpectator()) {
            return false;
        }
        double dy = player.getDeltaMovement().y;
        AABB reach = player.getBoundingBox().expandTowards(0.0, dy - 0.5, 0.0).inflate(0.3, 0.0, 0.3);
        for (StealthNpc npc : player.level().getEntitiesOfClass(StealthNpc.class, reach, n -> !n.isBody() && n.isAlive() && !inAction(n))) {
            boolean weapon = player.getMainHandItem().is(ItemTags.SWORDS) || player.getMainHandItem().is(ItemTags.AXES);
            boolean lethal = weapon && !player.isShiftKeyDown();
            return start(player, npc, lethal ? AIR_LETHAL : AIR_NONLETHAL);
        }
        return false;
    }

    /** Getting hit breaks a takedown before its impact. */
    @SubscribeEvent
    public static void onAttackerHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Active active = ACTIVE.get(player.getUUID());
        if (active != null && !active.impacted) {
            ACTIVE.remove(player.getUUID());
            interrupt(level, active);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || ACTIVE.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Active> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Active active = it.next();
            if (active.attacker.level() != level) {
                continue;
            }
            if (active.attacker.isRemoved() || !active.attacker.isAlive() || active.victim.isRemoved()) {
                it.remove();
                finish(active);
                continue;
            }
            // Both stay put: the victim is immobile while in the action; the attacker is pulled back if pushed.
            if (active.attacker.position().distanceToSqr(active.attackerPos) > HOLD_TOLERANCE * HOLD_TOLERANCE) {
                active.attacker.connection.teleport(active.attackerPos.x, active.attackerPos.y, active.attackerPos.z,
                        active.yaw, active.attacker.getXRot());
            }
            if (!active.impacted && now - active.start >= active.definition.impactTick()) {
                impact(level, active);
            }
            if (now - active.start >= active.definition.duration()) {
                it.remove();
                finish(active);
            }
        }
    }
}
