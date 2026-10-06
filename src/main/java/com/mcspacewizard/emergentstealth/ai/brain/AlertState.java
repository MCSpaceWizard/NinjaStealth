package com.mcspacewizard.emergentstealth.ai.brain;

/**
 * An NPC's alert state (A-01). Perception sets awareness; the brain maps awareness and memory to these.
 *
 * @param perceptionMultiplier how much faster awareness fills in this state (alert NPCs see more)
 */
public enum AlertState {
    /** Calm, never alerted (or long since cooled down). */
    UNAWARE(1.0F),
    /** Back to routine after an alert, but never fully calm again (until the cooldown passes). */
    HEIGHTENED(1.3F),
    /** "Huh?" Stops and looks toward what it noticed. */
    CURIOUS(1.3F),
    /** Sure something's there: keeps staring, about to investigate. */
    SUSPICIOUS(1.4F),
    /** Walks over to check the last known position. */
    INVESTIGATING(1.5F),
    /** Lost an alerted target: sweeps the area around the last known position. */
    SEARCHING(2.0F),
    /** Alerted, target out of view: runs to the last known position. */
    HUNTING(2.0F),
    /** Alerted and can see the target: attacks (guards). */
    COMBAT(3.0F),
    /** Alerted non-combatant: runs away (civilians, workers, targets). */
    FLEEING(2.0F);

    private final float perceptionMultiplier;

    AlertState(float perceptionMultiplier) {
        this.perceptionMultiplier = perceptionMultiplier;
    }

    public float perceptionMultiplier() {
        return perceptionMultiplier;
    }

    /** Alerted states: the NPC knows someone hostile is (or was just) here. */
    public boolean isAlerted() {
        return this == SEARCHING || this == HUNTING || this == COMBAT || this == FLEEING;
    }

    /** States where the brain drives movement and looking (the placeholder idle goals don't run). */
    public boolean isActive() {
        return this != UNAWARE && this != HEIGHTENED;
    }

    public static AlertState byId(int id) {
        AlertState[] values = values();
        return id >= 0 && id < values.length ? values[id] : UNAWARE;
    }
}
