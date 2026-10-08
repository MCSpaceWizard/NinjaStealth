package com.mcspacewizard.emergentstealth.progression;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * Numbers the stealth systems read through {@link StealthStats} (design doc 26 §1). Skills, gear and (later)
 * disguises all modify them; each system asks for the final value instead of knowing where it came from.
 *
 * @param base     the value with no modifiers
 * @param additive whether modifiers add (counts) or multiply (factors)
 */
public enum StealthStat implements StringRepresentable {
    /** Multiplies the player's visibility to NPC sight. */
    VISIBILITY("visibility", 1.0F, false),
    /** Multiplies footstep and landing noise. */
    FOOTSTEP_LOUDNESS("footstep_loudness", 1.0F, false),
    /** Multiplies takedown speed (higher = faster chokes). */
    TAKEDOWN_SPEED("takedown_speed", 1.0F, false),
    /** Multiplies movement speed while dragging or carrying a body. */
    DRAG_SPEED("drag_speed", 1.0F, false),
    /** Multiplies the lockpicking timing window. */
    LOCKPICK_WINDOW("lockpick_window", 1.0F, false),
    /** Extra spyglass tags. */
    TAG_COUNT("tag_count", 0.0F, true),
    /** Multiplies smoke bomb duration. */
    SMOKE_DURATION("smoke_duration", 1.0F, false),
    /** Multiplies technique cooldowns (lower = shorter). */
    TECHNIQUE_COOLDOWN("technique_cooldown", 1.0F, false);

    public static final Codec<StealthStat> CODEC = StringRepresentable.fromEnum(StealthStat::values);

    private final String name;
    private final float base;
    private final boolean additive;

    StealthStat(String name, float base, boolean additive) {
        this.name = name;
        this.base = base;
        this.additive = additive;
    }

    public float base() {
        return base;
    }

    public boolean additive() {
        return additive;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
