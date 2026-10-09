package com.mcspacewizard.emergentstealth.authoring;

import com.mcspacewizard.emergentstealth.ai.routine.Schedule;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The minimal trespass rule (design doc 32 §2): which access rule holds at a block right now. S9 builds alert
 * levels and propagation on top of this.
 * <ul>
 *   <li><b>Restricted:</b> awareness of you fills {@link #RESTRICTED_GAIN} times faster (the grace period
 *       shrinks with it), and guards warn you off before they hunt.</li>
 *   <li><b>Hostile:</b> once a guard notices you there (the "huh?" threshold), you are detected.</li>
 *   <li><b>Public:</b> unchanged.</li>
 * </ul>
 */
public final class Trespass {
    private Trespass() {}

    /** How much faster awareness fills while the target stands in a restricted zone. */
    public static final float RESTRICTED_GAIN = 2.0F;

    /** The strictest rule of the zones holding {@code pos} whose hours include the current clock hour. */
    public static Zone.Access accessAt(ServerLevel level, BlockPos pos) {
        Zones zones = Zones.get(level);
        if (zones.all().isEmpty()) {
            return Zone.Access.PUBLIC;
        }
        return accessAt(zones.all(), pos, Schedule.hourOf(level.getDefaultClockTime()));
    }

    /** The strictest rule of the zones holding {@code pos} whose hours include {@code hour}. */
    public static Zone.Access accessAt(Iterable<Zone> zones, BlockPos pos, int hour) {
        Zone.Access strictest = Zone.Access.PUBLIC;
        for (Zone zone : zones) {
            if (zone.access().ordinal() > strictest.ordinal() && zone.appliesAt(hour) && zone.contains(pos)) {
                strictest = zone.access();
            }
        }
        return strictest;
    }

    /** The factor on awareness gain (and on the grace-period cap) for a target under this rule. */
    public static float gainFactor(Zone.Access access) {
        return access == Zone.Access.RESTRICTED ? RESTRICTED_GAIN : 1.0F;
    }
}
