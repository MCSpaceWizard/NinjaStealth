package com.mcspacewizard.emergentstealth.progression;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** The two skill trees (R-01, design doc 26). */
public enum SkillPath implements StringRepresentable {
    SHINOBI("shinobi"),
    SHOGUNATE("shogunate");

    public static final Codec<SkillPath> CODEC = StringRepresentable.fromEnum(SkillPath::values);
    public static final StreamCodec<ByteBuf, SkillPath> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], SkillPath::ordinal);

    private final String name;

    SkillPath(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
