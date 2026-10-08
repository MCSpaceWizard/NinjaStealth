package com.mcspacewizard.emergentstealth.client.anim;

import com.mcspacewizard.emergentstealth.action.CarryLink;
import com.mcspacewizard.emergentstealth.anim.BodyPoses;
import com.mcspacewizard.emergentstealth.anim.VerletChain;
import com.mcspacewizard.emergentstealth.client.anim.sim.DragChainSim;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Where a body lies and which way (design doc 17 §2 and §4): the whole-model transform the NPC renderer applies
 * before the limbs are posed. A lying body pivots about its middle so it stays centred on its (flat) hitbox; a
 * dragged body follows its verlet chain; a carried body lies across the carrier's shoulders.
 *
 * <p>Filled at extraction time from the entity and its {@link ProceduralState}, then only read by the renderer.
 */
public final class BodyPlacement {
    /** Height of the pivot on a standing model (between the hips and the chest), blocks. */
    public static final float PIVOT_HEIGHT = 0.9F;
    /** Half the torso's depth: how far the pivot sits above the floor once lying, blocks. */
    public static final float LYING_HEIGHT = 0.13F;
    /** Height of the carrier's shoulders, where a carried body lies, blocks. */
    private static final double SHOULDERS = 1.52;

    /** 0 = standing, 1 = lying flat. Nothing is drawn differently at 0. */
    public float fall;
    public boolean faceUp = true;
    /** Facing of the lying body, degrees (the vanilla body-yaw convention). */
    public float yaw;
    /** Head raised by this much along the body, degrees (ground slope, dragging up steps). */
    public float pitch;
    /** Right side raised by this much, degrees. */
    public float roll;
    /** The pivot relative to the entity's render position, blocks. */
    public double dx;
    public double dy;
    public double dz;
    /** Carried over a shoulder (the limbs dangle). */
    public boolean carried;
    /** How much of the drag pose applies (0..1). */
    public float drag;

    public void reset() {
        fall = 0.0F;
        faceUp = true;
        yaw = 0.0F;
        pitch = 0.0F;
        roll = 0.0F;
        dx = 0.0;
        dy = 0.0;
        dz = 0.0;
        carried = false;
        drag = 0.0F;
    }

    /** Yaw of a body lying on its own: its facing plus a stable per-body twist. Also used by the body sim. */
    public static float lyingYaw(StealthNpc npc, float partialTick) {
        return npc.getYRot(partialTick) + BodyPoses.yawOffset(npc.getId());
    }

    /** Fills this placement for an NPC this frame. */
    public void compute(StealthNpc npc, ProceduralState sim, float partialTick) {
        reset();
        CarryLink link = npc.getData(ESAttachments.CARRY);
        Entity carrier = link.isNone() || !npc.isBody() ? null : npc.level().getEntity(link.otherId());
        if (carrier instanceof LivingEntity living && link.mode() == CarryLink.Mode.CARRY) {
            carried(npc, living, partialTick);
            return;
        }
        fall = sim.fall(npc.isBody(), partialTick);
        if (fall <= 0.0F) {
            return;
        }
        faceUp = sim.faceUp();
        yaw = lyingYaw(npc, partialTick);
        pitch = sim.groundPitch.get(partialTick) * fall;
        roll = sim.groundRoll.get(partialTick) * fall;
        double angle = Math.toRadians(90.0F * fall);
        dy = PIVOT_HEIGHT * Math.cos(angle) + LYING_HEIGHT * Math.sin(angle);
        drag = sim.dragBlend(partialTick);
        VerletChain chain = sim.chain;
        if (drag > 0.0F && chain != null) {
            dragged(npc, chain, partialTick);
        }
    }

    /**
     * Follows the drag chain (hand, shoulders, hips, feet): face up, head towards the dragger, the pivot a fifth of
     * the way from the hips to the shoulders, tilted with the chain.
     */
    private void dragged(StealthNpc npc, VerletChain chain, float partialTick) {
        double[] shoulders = new double[3];
        double[] hips = new double[3];
        chain.lerp(1, partialTick, shoulders);
        chain.lerp(2, partialTick, hips);
        double hx = hips[0] - shoulders[0];
        double hy = hips[1] - shoulders[1];
        double hz = hips[2] - shoulders[2];
        double horizontal = Math.sqrt(hx * hx + hz * hz);
        if (horizontal < 1.0E-4) {
            return;
        }
        // The body faces from its shoulders to its hips, so its back (where a face-up head points) faces the dragger.
        float chainYaw = (float) Math.toDegrees(Math.atan2(-hx, hz));
        float chainPitch = (float) Math.toDegrees(Math.atan2(-hy, horizontal));
        double t = (PIVOT_HEIGHT - 0.75) / DragChainSim.TORSO;
        Vec3 render = npc.getPosition(partialTick);
        double px = hips[0] - hx * t - render.x;
        double py = hips[1] - hy * t - render.y;
        double pz = hips[2] - hz * t - render.z;
        faceUp = true;
        yaw = yaw + Mth.wrapDegrees(chainYaw - yaw) * drag;
        pitch = Mth.lerp(drag, pitch, chainPitch);
        roll = Mth.lerp(drag, roll, 0.0F);
        dx = Mth.lerp(drag, dx, px);
        dy = Mth.lerp(drag, dy, py);
        dz = Mth.lerp(drag, dz, pz);
        fall = 1.0F;
    }

    /** Face down across the carrier's shoulders, head on the carrier's right. */
    private void carried(StealthNpc npc, LivingEntity carrier, float partialTick) {
        Vec3 render = npc.getPosition(partialTick);
        Vec3 at = carrier.getPosition(partialTick);
        float carrierYaw = Mth.rotLerp(partialTick, carrier.yBodyRotO, carrier.yBodyRot);
        double rad = Math.toRadians(carrierYaw);
        // A little behind the neck so the body rests on the shoulders rather than the face.
        double back = 0.08;
        fall = 1.0F;
        faceUp = false;
        carried = true;
        yaw = carrierYaw + 90.0F;
        dx = at.x + Math.sin(rad) * back - render.x;
        dy = at.y + SHOULDERS + LYING_HEIGHT - render.y;
        dz = at.z - Math.cos(rad) * back - render.z;
    }
}
