package com.mcspacewizard.emergentstealth.client.anim.sim;

import com.mcspacewizard.emergentstealth.action.ActionPlayback;
import com.mcspacewizard.emergentstealth.action.BodyState;
import com.mcspacewizard.emergentstealth.anim.BodyPoses;
import com.mcspacewizard.emergentstealth.client.anim.BodyPlacement;
import com.mcspacewizard.emergentstealth.client.anim.Floors;
import com.mcspacewizard.emergentstealth.client.anim.ProceduralSim;
import com.mcspacewizard.emergentstealth.client.anim.ProceduralState;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Bodies: the fall from standing to lying (and back up when woken), which side the body lands on, and the
 * slope under it so it lies on the ground instead of through it.
 */
public final class BodySim implements ProceduralSim {
    /** Ticks to fall down. */
    private static final float FALL_TICKS = 9.0F;
    /** Ticks to get back up when woken. */
    private static final float RISE_TICKS = 16.0F;
    private static final float MAX_SLOPE = 30.0F;

    @Override
    public void tick(LivingEntity entity, ClientLevel level, ProceduralState s) {
        if (!(entity instanceof StealthNpc npc)) {
            return;
        }
        ActionPlayback action = npc.getData(ESAttachments.ACTION);
        if (!action.isNone() && action.role() == ActionPlayback.Role.VICTIM) {
            s.lastVictimAction = action;
        }
        BodyState body = npc.getBodyState();
        if (body != s.bodyState) {
            if (body.isBody() && !s.bodyState.isBody()) {
                s.faceUp = landsFaceUp(npc, s);
                if (s.isFresh()) {
                    s.fall = 1.0F; // already lying when we first saw it
                    s.fallPrev = 1.0F;
                }
            }
            s.bodyState = body;
        }
        s.fallPrev = s.fall;
        s.fall = body.isBody() ? Math.min(1.0F, s.fall + 1.0F / FALL_TICKS) : Math.max(0.0F, s.fall - 1.0F / RISE_TICKS);

        if (body.isBody()) {
            fitToGround(npc, level, s);
        } else {
            s.groundPitch.snap(0.0F);
            s.groundRoll.snap(0.0F);
        }
    }

    /** Chokes fall backwards (face up); kills and air takedowns pitch forwards; anything else by entity id. */
    private static boolean landsFaceUp(StealthNpc npc, ProceduralState s) {
        ActionPlayback action = s.lastVictimAction;
        if (action != null) {
            Identifier id = action.action();
            if (id.getPath().endsWith("rear_nonlethal")) {
                return true;
            }
            if (id.getPath().startsWith("takedown/")) {
                return false;
            }
        }
        return BodyPoses.faceUp(npc.getId());
    }

    /** Samples the floor at the head and feet (pitch) and both sides (roll) of the lying body. */
    private static void fitToGround(StealthNpc npc, ClientLevel level, ProceduralState s) {
        float yaw = BodyPlacement.lyingYaw(npc, 1.0F);
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        // Head lies backwards for a face-up body, forwards for face-down (see BodyPlacement).
        double hs = s.faceUp ? -1.0 : 1.0;
        double x = npc.getX();
        double y = npc.getY();
        double z = npc.getZ();
        double head = Floors.below(level, x + fx * hs * 0.75, y + 0.5, z + fz * hs * 0.75);
        double feet = Floors.below(level, x - fx * hs * 0.75, y + 0.5, z - fz * hs * 0.75);
        double right = Floors.below(level, x - fz * 0.3, y + 0.5, z + fx * 0.3);
        double left = Floors.below(level, x + fz * 0.3, y + 0.5, z - fx * 0.3);
        float pitch = slope(head, feet, y, 1.5);
        float roll = slope(right, left, y, 0.6);
        s.groundPitch.step(pitch);
        s.groundRoll.step(roll);
    }

    /** Degrees the line from {@code b} to {@code a} rises over {@code length}; missing floors count as level. */
    private static float slope(double a, double b, double y, double length) {
        if (a == Double.NEGATIVE_INFINITY) {
            a = y;
        }
        if (b == Double.NEGATIVE_INFINITY) {
            b = y;
        }
        a = Mth.clamp(a, y - 1.0, y + 1.0);
        b = Mth.clamp(b, y - 1.0, y + 1.0);
        return Mth.clamp((float) Math.toDegrees(Math.atan2(a - b, length)), -MAX_SLOPE, MAX_SLOPE);
    }
}
