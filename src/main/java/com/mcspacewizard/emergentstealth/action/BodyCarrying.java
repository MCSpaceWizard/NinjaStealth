package com.mcspacewizard.emergentstealth.action;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Moving bodies (design doc 17 §4, P-09). Right-click a body to drag it along the floor (×0.75 speed, silent),
 * sneak + right-click to carry it on the shoulder (×0.6). Right-click again to drop it. The server moves the
 * body each tick; clients animate it from the synced {@link CarryLink}.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class BodyCarrying {
    private BodyCarrying() {}

    private static final Identifier SPEED_MODIFIER = EmergentStealth.id("carrying_body");
    public static final double DRAG_SPEED = 0.75;
    public static final double CARRY_SPEED = 0.6;
    /** How far behind the player a dragged body lies. */
    private static final double DRAG_DISTANCE = 1.3;
    /** A body further than this from its carrier (teleport, lag) is dropped. */
    private static final double MAX_DISTANCE = 6.0;

    /** Bodies a player put down: who, and when (design doc 26 §2: unfound for 5 minutes earns Insight). */
    private record Hidden(java.util.UUID player, long tick) {}

    private static final java.util.Map<java.util.UUID, Hidden> HIDDEN = new java.util.HashMap<>();
    public static final long HIDDEN_TICKS = 20L * 60 * 5;

    /** An NPC noticed this body: no hiding Insight for it. */
    public static void discovered(StealthNpc body) {
        HIDDEN.remove(body.getUUID());
    }

    @SubscribeEvent
    public static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || HIDDEN.isEmpty() || level.getGameTime() % 20 != 0) {
            return;
        }
        long now = level.getGameTime();
        var it = HIDDEN.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (now - entry.getValue().tick() < HIDDEN_TICKS) {
                continue;
            }
            if (level.getEntity(entry.getKey()) instanceof StealthNpc body && body.isBody()
                    && level.getPlayerByUUID(entry.getValue().player()) instanceof ServerPlayer player) {
                com.mcspacewizard.emergentstealth.progression.Skills.awardInsight(player,
                        com.mcspacewizard.emergentstealth.progression.SkillPath.SHINOBI,
                        com.mcspacewizard.emergentstealth.progression.Skills.INSIGHT_BODY_HIDDEN);
                it.remove();
            } else if (level.getEntity(entry.getKey()) == null && now - entry.getValue().tick() > HIDDEN_TICKS * 4) {
                it.remove(); // unloaded or gone: forget it eventually
            }
        }
    }

    public static CarryLink link(Entity entity) {
        return entity.getData(ESAttachments.CARRY);
    }

    public static @Nullable StealthNpc carriedBody(Player player) {
        CarryLink link = link(player);
        return !link.isNone() && player.level().getEntity(link.otherId()) instanceof StealthNpc npc ? npc : null;
    }

    public static void pickUp(ServerPlayer player, StealthNpc body, CarryLink.Mode mode) {
        drop(player);
        if (!link(body).isNone()) {
            return; // someone else has it
        }
        player.setData(ESAttachments.CARRY, new CarryLink(body.getId(), mode));
        body.setData(ESAttachments.CARRY, new CarryLink(player.getId(), mode));
        body.setNoGravity(mode == CarryLink.Mode.CARRY);
        double skill = com.mcspacewizard.emergentstealth.progression.StealthStats.get(player,
                com.mcspacewizard.emergentstealth.progression.StealthStat.DRAG_SPEED);
        setSpeed(player, Math.min(1.0, (mode == CarryLink.Mode.CARRY ? CARRY_SPEED : DRAG_SPEED) * skill));
    }

    /** Server: the player right-clicked while moving a body. */
    public static void handleDrop(DropBodyPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            drop(player);
        }
    }

    public static void drop(Player player) {
        StealthNpc body = carriedBody(player);
        player.setData(ESAttachments.CARRY, CarryLink.NONE);
        clearSpeed(player);
        if (body != null) {
            body.setData(ESAttachments.CARRY, CarryLink.NONE);
            body.setNoGravity(false);
            if (link(player).isNone() && player.level() instanceof ServerLevel) {
                // Put it down just in front of the player's feet, on the ground.
                Vec3 at = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(0.8));
                body.setPos(at.x, player.getY(), at.z);
                body.setDeltaMovement(Vec3.ZERO);
                if (player instanceof ServerPlayer && !player.isCreative()) {
                    HIDDEN.put(body.getUUID(), new Hidden(player.getUUID(), player.level().getGameTime()));
                }
            }
        }
    }

    private static void setSpeed(Player player, double factor) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER);
            speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER, factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void clearSpeed(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER);
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof StealthNpc body) || !body.isBody()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (carriedBody(player) == body) {
            drop(player);
        } else {
            pickUp(player, body, player.isShiftKeyDown() ? CarryLink.Mode.CARRY : CarryLink.Mode.DRAG);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !link(player).isNone()) {
            tick(player);
        }
    }

    /** Moves the carried body along with the player (one server tick). */
    public static void tick(ServerPlayer player) {
        StealthNpc body = carriedBody(player);
        if (body == null || !body.isBody() || !player.isAlive() || player.isSpectator()
                || body.distanceToSqr(player) > MAX_DISTANCE * MAX_DISTANCE) {
            drop(player);
            return;
        }
        player.setSprinting(false);
        CarryLink.Mode mode = link(player).mode();
        Vec3 target;
        if (mode == CarryLink.Mode.CARRY) {
            target = player.position().add(0.0, player.getBbHeight() * 0.75, 0.0);
        } else {
            double rad = Math.toRadians(player.getYRot());
            Vec3 back = new Vec3(Math.sin(rad), 0.0, -Math.cos(rad)).scale(DRAG_DISTANCE);
            target = new Vec3(player.getX() + back.x, body.getY(), player.getZ() + back.z);
            // Slide along the floor: walk the body there with collisions (it follows down steps by gravity).
            Vec3 move = target.subtract(body.position()).multiply(1.0, 0.0, 1.0);
            body.move(net.minecraft.world.entity.MoverType.SELF, move);
            if (player.getY() > body.getY() + 0.5 && body.horizontalCollision) {
                body.setPos(body.getX(), player.getY(), body.getZ());
            }
            body.setYRot(player.getYRot());
            return;
        }
        body.setPos(target.x, target.y, target.z);
        body.setDeltaMovement(Vec3.ZERO);
        body.setYRot(player.getYRot());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        drop(event.getEntity());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        drop(event.getEntity());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            drop(player);
        }
    }
}
