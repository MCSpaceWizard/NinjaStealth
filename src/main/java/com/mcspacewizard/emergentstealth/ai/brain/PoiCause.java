package com.mcspacewizard.emergentstealth.ai.brain;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** Why an NPC is interested in its point of interest (design doc 14 §1). */
public enum PoiCause implements StringRepresentable {
    NONE("none"),
    SEEN("seen"),
    HEARD("heard"),
    HURT("hurt"),
    SHOUT("shout"),
    EVIDENCE("evidence");

    public static final Codec<PoiCause> CODEC = StringRepresentable.fromEnum(PoiCause::values);

    private final String name;

    PoiCause(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
