package com.mcspacewizard.emergentstealth.client.anim.sim;

import com.mcspacewizard.emergentstealth.action.BodyState;
import com.mcspacewizard.emergentstealth.anim.BodyPoses;
import com.mcspacewizard.emergentstealth.client.anim.ProceduralSim;
import com.mcspacewizard.emergentstealth.client.anim.ProceduralState;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Movement memory: crawled/walked distance (gait phase), lean springs, and exertion (breathing rate). Uses the
 * entity's tick positions, which remote players and NPCs have too, so other players see the same gait.
 */
public final class MotionSim implements ProceduralSim {
    /** Roll per (degree of turn per tick × block per tick). */
    private static final float ROLL_GAIN = 0.12F;
    private static final float MAX_ROLL = 0.22F;
    private static final float PITCH_GAIN = 0.35F;
    private static final float MAX_PITCH = 0.12F;
    private static final float CALM_BREATH = 90.0F;
    private static final float HARD_BREATH = 36.0F;
    private static final float UNCONSCIOUS_BREATH = 110.0F;

    @Override
    public void tick(LivingEntity entity, ClientLevel level, ProceduralState s) {
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        float yaw = entity.yBodyRot;
        if (!s.hasLastPosition()) {
            s.resetMotion(x, y, z, yaw);
        }
        double dx = x - s.lastX();
        double dz = z - s.lastZ();
        double step = Math.sqrt(dx * dx + dz * dz);
        if (step > 2.0) {
            step = 0.0; // teleport, not a stride
        }
        s.distancePrev = s.distance;
        s.distance += step;
        s.stillTicks = step < 0.003 ? s.stillTicks + 1 : 0;

        float yawRate = Mth.wrapDegrees(yaw - s.lastYaw());
        float speed = (float) step;
        // Lean into the turn (roll towards the inside) and forward with speed.
        s.leanRoll.step(Mth.clamp(-yawRate * speed * ROLL_GAIN, -MAX_ROLL, MAX_ROLL));
        s.leanPitch.step(Mth.clamp(speed * PITCH_GAIN, 0.0F, MAX_PITCH));
        s.exertion.step(entity.isSprinting() ? 1.0F : 0.0F);
        // Calm 4.5 s breaths, quick 1.8 s ones after sprinting, slow deep ones while knocked out.
        boolean unconscious = entity instanceof StealthNpc npc && npc.getBodyState() == BodyState.UNCONSCIOUS;
        float period = unconscious ? UNCONSCIOUS_BREATH : Mth.lerp(Mth.clamp(s.exertion.value(), 0.0F, 1.0F), CALM_BREATH, HARD_BREATH);
        if (s.breath == 0.0) {
            s.breath = BodyPoses.unit(entity.getId(), 11); // stagger crowds
        }
        s.breathPrev = s.breath;
        s.breath += 1.0 / period;
        s.resetMotion(x, y, z, yaw);
    }
}
