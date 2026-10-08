package com.mcspacewizard.emergentstealth.action;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** The player's stealth stance (design doc 17 §1). Sneaking stays vanilla; crawling is ours. */
public enum Stance implements StringRepresentable {
    STANDING("standing"),
    CRAWLING("crawling");

    public static final Codec<Stance> CODEC = StringRepresentable.fromEnum(Stance::values);
    public static final StreamCodec<ByteBuf, Stance> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Stance::ordinal);

    private final String name;

    Stance(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
