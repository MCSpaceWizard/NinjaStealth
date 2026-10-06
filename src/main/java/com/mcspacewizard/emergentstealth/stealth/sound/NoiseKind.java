package com.mcspacewizard.emergentstealth.stealth.sound;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** What made a noise (design doc 16 §1). NPCs react differently to each. */
public enum NoiseKind implements StringRepresentable {
    FOOTSTEP("footstep"),
    LANDING("landing"),
    DOOR("door"),
    BLOCK("block"),
    COMBAT("combat"),
    IMPACT("impact"),
    SHOUT("shout"),
    EXPLOSION("explosion"),
    OTHER("other");

    public static final Codec<NoiseKind> CODEC = StringRepresentable.fromEnum(NoiseKind::values);

    private final String name;

    NoiseKind(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
