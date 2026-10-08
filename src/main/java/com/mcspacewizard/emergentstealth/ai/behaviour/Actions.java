package com.mcspacewizard.emergentstealth.ai.behaviour;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mcspacewizard.emergentstealth.ai.brain.Barks;
import com.mcspacewizard.emergentstealth.ai.group.AttackTokens;
import com.mcspacewizard.emergentstealth.ai.group.SearchGroups;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseEvent;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseKind;
import com.mcspacewizard.emergentstealth.stealth.sound.Noises;

import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

/** Things NPCs do (design doc 14 §3). Most keep running until interrupted or finished. */
public final class Actions {
    private Actions() {}

    /** Where {@code move_to} goes. */
    public enum MoveTarget implements StringRepresentable {
        POI("poi"),
        HOME("home"),
        AWAY_FROM_POI("away_from_poi");

        public static final Codec<MoveTarget> CODEC = StringRepresentable.fromEnum(MoveTarget::values);
        private final String name;

        MoveTarget(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final BtNodeType<MoveTo> MOVE_TO = new BtNodeType<>("move_to", RecordCodecBuilder.mapCodec(i -> i.group(
            MoveTarget.CODEC.fieldOf("target").forGetter(MoveTo::target),
            Codec.floatRange(0.1F, 3.0F).optionalFieldOf("speed", 0.7F).forGetter(MoveTo::speed),
            Codec.floatRange(0.5F, 32.0F).optionalFieldOf("arrive", 2.0F).forGetter(MoveTo::arrive)
    ).apply(i, MoveTo::new)));
    public static final BtNodeType<LookAtPoi> LOOK_AT_POI = new BtNodeType<>("look_at_poi",
            Codec.BOOL.optionalFieldOf("stop", true).xmap(LookAtPoi::new, LookAtPoi::stop));
    public static final BtNodeType<LookAround> LOOK_AROUND = new BtNodeType<>("look_around",
            Codec.floatRange(0, 600).fieldOf("seconds").xmap(LookAround::new, LookAround::seconds));
    public static final BtNodeType<Wait> WAIT = new BtNodeType<>("wait",
            Codec.floatRange(0, 600).fieldOf("seconds").xmap(Wait::new, Wait::seconds));
    public static final BtNodeType<Stop> STOP = new BtNodeType<>("stop", MapCodec.unit(Stop::new));
    public static final BtNodeType<Attack> ATTACK = new BtNodeType<>("attack", MapCodec.unit(Attack::new));
    public static final BtNodeType<HoldRing> HOLD_RING = new BtNodeType<>("hold_ring", RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.floatRange(1, 32).optionalFieldOf("min", 4.0F).forGetter(HoldRing::min),
            Codec.floatRange(1, 32).optionalFieldOf("max", 6.0F).forGetter(HoldRing::max),
            Codec.floatRange(0.1F, 3.0F).optionalFieldOf("speed", 1.0F).forGetter(HoldRing::speed)
    ).apply(i, HoldRing::new)));
    public static final BtNodeType<Search> SEARCH = new BtNodeType<>("search", RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.floatRange(0.1F, 3.0F).optionalFieldOf("speed", 0.8F).forGetter(Search::speed),
            Codec.floatRange(0, 60).optionalFieldOf("look_seconds", 2.0F).forGetter(Search::lookSeconds)
    ).apply(i, Search::new)));
    public static final BtNodeType<Bark> BARK = new BtNodeType<>("bark",
            Codec.STRING.fieldOf("situation").xmap(Bark::new, Bark::situation));
    public static final BtNodeType<Shout> SHOUT = new BtNodeType<>("shout",
            Codec.floatRange(1, 128).optionalFieldOf("loudness", 24.0F).xmap(Shout::new, Shout::loudness));

    // ------------------------------------------------------------------------------------------------
    // Shared movement and looking helpers

    /** Per-NPC walking state with progress-based stuck detection. */
    static final class Walk {
        @Nullable Vec3 requested;
        @Nullable Vec3 chosen;
        long lastRequest = Long.MIN_VALUE;
        double best = Double.MAX_VALUE;
        int stuck;
        int failures;
    }

    private static final int STUCK_TICKS = 100;
    private static final int REPATH_TICKS = 10;

    static BtStatus walk(BtContext ctx, Walk walk, Vec3 dest, double speed, double arrive) {
        StealthNpc npc = ctx.npc();
        double distanceSq = npc.position().distanceToSqr(dest);
        if (distanceSq <= arrive * arrive) {
            npc.getNavigation().stop();
            return BtStatus.SUCCESS;
        }
        boolean moved = walk.requested == null || walk.requested.distanceToSqr(dest) > 2.25;
        if (moved || (npc.getNavigation().isDone() && ctx.now() - walk.lastRequest >= REPATH_TICKS)) {
            walk.lastRequest = ctx.now();
            walk.requested = dest;
            if (!npc.getNavigation().moveTo(dest.x, dest.y, dest.z, speed) && ++walk.failures > 3) {
                return BtStatus.FAILURE;
            }
        }
        if (distanceSq < walk.best - 0.25) {
            walk.best = distanceSq;
            walk.stuck = 0;
        } else if (++walk.stuck > STUCK_TICKS) {
            npc.getNavigation().stop();
            return BtStatus.FAILURE;
        }
        npc.getLookControl().setLookAt(dest.x, dest.y + npc.getEyeHeight(), dest.z);
        return BtStatus.RUNNING;
    }

    static final class Glance {
        @Nullable Vec3 at;
        long until = Long.MIN_VALUE;
    }

    /** Sweeps the gaze around: what a guard does when it reaches a spot and finds nothing. */
    static void glance(BtContext ctx, Glance glance) {
        StealthNpc npc = ctx.npc();
        if (glance.at == null || ctx.now() >= glance.until) {
            float yaw = npc.getYHeadRot() + Mth.nextFloat(npc.getRandom(), -120.0F, 120.0F);
            double rad = Math.toRadians(yaw);
            glance.at = npc.getEyePosition().add(-Math.sin(rad) * 6.0, Mth.nextDouble(npc.getRandom(), -1.0, 0.5), Math.cos(rad) * 6.0);
            glance.until = ctx.now() + 15 + npc.getRandom().nextInt(25);
        }
        npc.getLookControl().setLookAt(glance.at.x, glance.at.y, glance.at.z);
    }

    static final class Timer {
        long until = Long.MIN_VALUE;
        final Glance glance = new Glance();
    }

    // ------------------------------------------------------------------------------------------------
    // Actions

    public record MoveTo(MoveTarget target, float speed, float arrive) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return MOVE_TO;
        }

        @Override
        public String label() {
            return "move_to " + target.getSerializedName();
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            StealthNpc npc = ctx.npc();
            Walk walk = ctx.state(this, Walk::new);
            ctx.running(label());
            Vec3 poi = ctx.brain().pointOfInterest();
            return switch (target) {
                case POI -> poi == null ? BtStatus.FAILURE : walk(ctx, walk, poi, speed, arrive);
                case HOME -> walk(ctx, walk, Vec3.atBottomCenterOf(npc.getHome()), speed, arrive);
                case AWAY_FROM_POI -> {
                    if (poi == null) {
                        yield BtStatus.FAILURE;
                    }
                    if (walk.chosen == null || npc.getNavigation().isDone()) {
                        walk.chosen = LandRandomPos.getPosAway(npc, 16, 7, poi);
                        if (walk.chosen != null) {
                            npc.getNavigation().moveTo(walk.chosen.x, walk.chosen.y, walk.chosen.z, speed);
                        }
                    }
                    yield BtStatus.RUNNING;
                }
            };
        }

        @Override
        public void abort(BtContext ctx) {
            ctx.npc().getNavigation().stop();
            ctx.clear(this);
        }
    }

    /** Stands (unless {@code stop} is false) and stares at the point of interest. Keeps running. */
    public record LookAtPoi(boolean stop) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return LOOK_AT_POI;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            Vec3 poi = ctx.brain().pointOfInterest();
            if (poi == null) {
                return BtStatus.FAILURE;
            }
            if (stop) {
                ctx.npc().getNavigation().stop();
            }
            ctx.npc().getLookControl().setLookAt(poi.x, poi.y + 1.2, poi.z);
            ctx.running(label());
            return BtStatus.RUNNING;
        }
    }

    public record LookAround(float seconds) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return LOOK_AROUND;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            Timer timer = ctx.state(this, Timer::new);
            if (timer.until == Long.MIN_VALUE) {
                timer.until = ctx.now() + Math.round(seconds * 20.0F);
            }
            ctx.npc().getNavigation().stop();
            glance(ctx, timer.glance);
            ctx.running(label());
            if (ctx.now() >= timer.until) {
                ctx.clear(this);
                return BtStatus.SUCCESS;
            }
            return BtStatus.RUNNING;
        }
    }

    public record Wait(float seconds) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return WAIT;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            Timer timer = ctx.state(this, Timer::new);
            if (timer.until == Long.MIN_VALUE) {
                timer.until = ctx.now() + Math.round(seconds * 20.0F);
            }
            ctx.running(label());
            if (ctx.now() >= timer.until) {
                ctx.clear(this);
                return BtStatus.SUCCESS;
            }
            return BtStatus.RUNNING;
        }
    }

    public record Stop() implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return STOP;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            ctx.npc().getNavigation().stop();
            return BtStatus.SUCCESS;
        }
    }

    /**
     * Melee the alert target (vanilla melee for now; S13 brings real combat). Needs an attack token. Fails when the
     * NPC may not attack it (vanilla rule: nobody attacks players on Peaceful), so the tree falls back to the ring.
     */
    public record Attack() implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return ATTACK;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            UUID target = ctx.brain().alertTarget();
            LivingEntity entity = ctx.brain().targetEntity(ctx.level());
            if (target == null || entity == null || !ctx.npc().canAttack(entity)
                    || !AttackTokens.get(ctx.level()).request(ctx.level(), ctx.npc(), target, ctx.now())) {
                return BtStatus.FAILURE;
            }
            ctx.requestMelee();
            ctx.running(label());
            return BtStatus.RUNNING;
        }
    }

    /** Waits for a turn to attack: stands in a ring around the target, away from the attackers, facing it. */
    public record HoldRing(float min, float max, float speed) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return HOLD_RING;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            UUID target = ctx.brain().alertTarget();
            LivingEntity entity = ctx.brain().targetEntity(ctx.level());
            Vec3 targetPos = entity != null && ctx.brain().canSeeTarget() ? entity.position() : ctx.brain().pointOfInterest();
            if (target == null || targetPos == null) {
                return BtStatus.FAILURE;
            }
            StealthNpc npc = ctx.npc();
            Vec3 slot = AttackTokens.get(ctx.level()).ringSlot(ctx.level(), npc, target, targetPos, (min + max) / 2.0, ctx.now());
            Walk walk = ctx.state(this, Walk::new);
            double fromTarget = npc.position().distanceTo(targetPos);
            if (npc.position().distanceToSqr(slot) > 1.5 * 1.5 || fromTarget < min || fromTarget > max) {
                walk(ctx, walk, slot, speed, 1.0);
            } else {
                npc.getNavigation().stop();
                walk.best = Double.MAX_VALUE;
                walk.stuck = 0;
            }
            npc.getLookControl().setLookAt(targetPos.x, targetPos.y + 1.2, targetPos.z);
            ctx.running(label());
            return BtStatus.RUNNING;
        }

        @Override
        public void abort(BtContext ctx) {
            ctx.npc().getNavigation().stop();
            ctx.clear(this);
        }
    }

    /** Coordinated search step (design doc 14 §4): go to a claimed search point, look around, claim the next. */
    public record Search(float speed, float lookSeconds) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return SEARCH;
        }

        private static final class State {
            SearchGroups.@Nullable Point point;
            Walk walk = new Walk();
            long lookUntil = Long.MIN_VALUE;
            final Glance glance = new Glance();
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            SearchGroups groups = SearchGroups.get(ctx.level());
            if (groups.groupOf(ctx.npc()) == null) {
                return BtStatus.FAILURE;
            }
            State state = ctx.state(this, State::new);
            ctx.running(label());
            if (state.point == null || state.point.visited()) {
                state.point = groups.claim(ctx.level(), ctx.npc());
                state.walk = new Walk();
                state.lookUntil = Long.MIN_VALUE;
                if (state.point == null) {
                    glance(ctx, state.glance);
                    return BtStatus.RUNNING;
                }
            }
            if (state.lookUntil == Long.MIN_VALUE) {
                BtStatus walking = walk(ctx, state.walk, Vec3.atBottomCenterOf(state.point.pos), speed, 1.5);
                if (walking != BtStatus.RUNNING) {
                    // Arrived (or can't get there): look around from here.
                    ctx.npc().getNavigation().stop();
                    state.lookUntil = ctx.now() + Math.round(lookSeconds * 20.0F);
                }
            } else {
                glance(ctx, state.glance);
                if (ctx.now() >= state.lookUntil) {
                    groups.done(ctx.npc(), state.point);
                    state.point = null;
                }
            }
            return BtStatus.RUNNING;
        }

        @Override
        public void abort(BtContext ctx) {
            ctx.npc().getNavigation().stop();
            ctx.clear(this);
        }
    }

    /** Says a line above the head (design doc 14 §6). */
    public record Bark(String situation) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return BARK;
        }

        @Override
        public String label() {
            return "bark " + situation;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            Barks.say(ctx.level(), ctx.npc(), situation);
            return BtStatus.SUCCESS;
        }
    }

    /** A loud call: a noise (S6) that brings other NPCs to this NPC. Says nothing about where the target is. */
    public record Shout(float loudness) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return SHOUT;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            StealthNpc npc = ctx.npc();
            Noises.emit(ctx.level(), new NoiseEvent(npc.getEyePosition(), loudness, NoiseKind.SHOUT, null, npc.getUUID()));
            ctx.brain().noteShout(ctx.now());
            return BtStatus.SUCCESS;
        }
    }
}
