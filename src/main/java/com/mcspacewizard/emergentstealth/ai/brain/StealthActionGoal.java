package com.mcspacewizard.emergentstealth.ai.brain;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.entity.StealthNpc;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Executes the brain's non-combat alert behaviour: looking at what it noticed, investigating, hunting,
 * searching and fleeing. Combat itself is vanilla melee driven by the brain setting the target.
 */
public class StealthActionGoal extends Goal {
    private static final double INVESTIGATE_SPEED = 0.7;
    private static final double HUNT_SPEED = 1.2;
    private static final double SEARCH_SPEED = 0.8;
    private static final double FLEE_SPEED = 1.3;
    private static final double SEARCH_RADIUS = 8.0;

    private final StealthNpc npc;
    private @Nullable Vec3 lookAround;
    private int lookAroundTicks;

    public StealthActionGoal(StealthNpc npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        AlertState state = npc.stealthBrain().state();
        return state.isActive() && state != AlertState.COMBAT;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        lookAround = null;
    }

    @Override
    public void tick() {
        StealthBrain brain = npc.stealthBrain();
        Vec3 poi = brain.pointOfInterest();
        switch (brain.state()) {
            case CURIOUS, SUSPICIOUS -> {
                npc.getNavigation().stop();
                if (poi != null) {
                    npc.getLookControl().setLookAt(poi.x, poi.y + 1.0, poi.z);
                }
            }
            case INVESTIGATING -> walkThenLookAround(poi, INVESTIGATE_SPEED);
            case HUNTING -> walkThenLookAround(poi, HUNT_SPEED);
            case SEARCHING -> search(brain.searchCenter() != null ? brain.searchCenter() : poi);
            case FLEEING -> flee(poi);
            default -> {
            }
        }
    }

    private void walkThenLookAround(@Nullable Vec3 target, double speed) {
        if (target == null) {
            lookAroundRandomly();
            return;
        }
        if (npc.position().distanceToSqr(target) > 2.5 * 2.5) {
            if (npc.getNavigation().isDone()) {
                npc.getNavigation().moveTo(target.x, target.y, target.z, speed);
            }
            npc.getLookControl().setLookAt(target.x, target.y + 1.0, target.z);
        } else {
            npc.getNavigation().stop();
            lookAroundRandomly();
        }
    }

    private void search(@Nullable Vec3 center) {
        if (center == null) {
            lookAroundRandomly();
            return;
        }
        if (npc.getNavigation().isDone()) {
            if (lookAroundTicks > 0) {
                lookAroundRandomly();
                return;
            }
            double angle = npc.getRandom().nextDouble() * Math.PI * 2.0;
            double radius = 2.0 + npc.getRandom().nextDouble() * (SEARCH_RADIUS - 2.0);
            Vec3 spot = center.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            npc.getNavigation().moveTo(spot.x, spot.y, spot.z, SEARCH_SPEED);
            lookAroundTicks = 30 + npc.getRandom().nextInt(30);
        }
    }

    private void flee(@Nullable Vec3 threat) {
        if (threat == null || !npc.getNavigation().isDone()) {
            return;
        }
        Vec3 away = LandRandomPos.getPosAway(npc, 16, 7, threat);
        if (away != null) {
            npc.getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
        }
    }

    /** Sweeps the gaze around: what a guard does when they reach a spot and find nothing. */
    private void lookAroundRandomly() {
        if (lookAroundTicks > 0) {
            lookAroundTicks--;
        }
        if (lookAround == null || npc.getRandom().nextInt(25) == 0) {
            float yaw = npc.getYHeadRot() + Mth.nextFloat(npc.getRandom(), -120.0F, 120.0F);
            double rad = Math.toRadians(yaw);
            lookAround = npc.getEyePosition().add(-Math.sin(rad) * 6.0, Mth.nextDouble(npc.getRandom(), -1.0, 0.5), Math.cos(rad) * 6.0);
        }
        npc.getLookControl().setLookAt(lookAround.x, lookAround.y, lookAround.z);
    }
}
