package com.mcspacewizard.emergentstealth.action;

/**
 * Whether an NPC is up, knocked out or a corpse (design doc 17 §2). Bodies stay in the world as evidence;
 * knocked-out NPCs never wake on their own (A-12).
 */
public enum BodyState {
    NONE,
    UNCONSCIOUS,
    DEAD;

    public static BodyState byId(int id) {
        BodyState[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }

    public boolean isBody() {
        return this != NONE;
    }
}
