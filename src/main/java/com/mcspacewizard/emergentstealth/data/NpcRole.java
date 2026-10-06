package com.mcspacewizard.emergentstealth.data;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** What an NPC does in the world. Behaviour stages (S4+) key off this rather than off Java classes. */
public enum NpcRole implements StringRepresentable {
    CIVILIAN("civilian"),
    WORKER("worker"),
    MERCHANT("merchant"),
    PATROL_GUARD("patrol_guard"),
    STATIONARY_GUARD("stationary_guard"),
    ELITE("elite"),
    CAPTAIN("captain"),
    TARGET("target"),
    BODYGUARD("bodyguard"),
    MONK("monk"),
    SHINOBI("shinobi");

    public static final Codec<NpcRole> CODEC = StringRepresentable.fromEnum(NpcRole::values);

    private final String name;

    NpcRole(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Guards of any kind: the roles that patrol, investigate and fight. */
    public boolean isGuard() {
        return switch (this) {
            case PATROL_GUARD, STATIONARY_GUARD, ELITE, CAPTAIN, BODYGUARD, SHINOBI -> true;
            default -> false;
        };
    }
}
