package com.mcspacewizard.emergentstealth.client.anim.sim;

import com.mcspacewizard.emergentstealth.action.CarryLink;
import com.mcspacewizard.emergentstealth.anim.VerletChain;
import com.mcspacewizard.emergentstealth.client.anim.Floors;
import com.mcspacewizard.emergentstealth.client.anim.ProceduralSim;
import com.mcspacewizard.emergentstealth.client.anim.ProceduralState;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * A dragged body as a verlet chain (design doc 17 §4): the dragger's hand (pinned), the body's shoulders,
 * hips and feet. The chain lives in world space and is stepped from the dragger's tick positions, so it
 * renders smoothly relative to the dragger instead of jittering against the body's own interpolation.
 */
public final class DragChainSim implements ProceduralSim {
    /** Hand to shoulders (arms held overhead), shoulders to hips, hips to feet, in blocks. */
    public static final double ARMS = 0.62;
    public static final double TORSO = 0.75;
    public static final double LEGS = 0.75;
    private static final double RADIUS = 0.12;
    private static final float BLEND_TICKS = 8.0F;
    private static final double HIP_PULL = 0.2;

    @Override
    public void tick(LivingEntity entity, ClientLevel level, ProceduralState s) {
        if (!(entity instanceof StealthNpc npc)) {
            return;
        }
        CarryLink link = npc.getData(ESAttachments.CARRY);
        Entity dragger = link.mode() == CarryLink.Mode.DRAG && npc.isBody() ? level.getEntity(link.otherId()) : null;
        s.dragBlendPrev = s.dragBlend;
        if (!(dragger instanceof LivingEntity carrier)) {
            s.chain = null;
            s.dragBlend = Math.max(0.0F, s.dragBlend - 1.0F / BLEND_TICKS);
            return;
        }
        double[] hand = handPosition(carrier, carrier.getX(), carrier.getY(), carrier.getZ());
        VerletChain chain = s.chain;
        if (chain == null) {
            chain = new VerletChain(RADIUS, ARMS, TORSO, LEGS);
            // Start stretched from the hand towards where the body lies.
            chain.place(hand[0], hand[1], hand[2], npc.getX() - hand[0], npc.getY() + RADIUS - hand[1], npc.getZ() - hand[2]);
            s.chain = chain;
        }
        chain.step(hand[0], hand[1], hand[2], (x, y, z) -> Floors.below(level, x, y, z), 0.9, 6);
        // The server decides where the body is (evidence, hiding): keep the hips near it, so the chain can't
        // swing round to a side the server doesn't agree with (walking backwards, turning on the spot).
        chain.pull(2, npc.getX(), npc.getZ(), HIP_PULL);
        s.dragBlend = Math.min(1.0F, s.dragBlend + 1.0F / BLEND_TICKS);
    }

    /**
     * The dragger's right hand reaching back and down, in world space, for a body position {@code (x, y, z)}.
     * Also used by the renderer with interpolated positions.
     */
    public static double[] handPosition(LivingEntity carrier, double x, double y, double z) {
        double rad = Math.toRadians(carrier.yBodyRot);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        // Right of the facing direction is (-fz, fx) rotated: facing south (+z), right is west (-x).
        double rx = -Math.cos(rad);
        double rz = -Math.sin(rad);
        return new double[] {x + rx * 0.3 - fx * 0.5, y + 0.62, z + rz * 0.3 - fz * 0.5};
    }
}
