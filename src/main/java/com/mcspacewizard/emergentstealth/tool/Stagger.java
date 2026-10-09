package com.mcspacewizard.emergentstealth.tool;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A sleep dart's effect on an NPC (design doc 21 §2), as game-time timestamps.
 *
 * @param until    game time the stagger ends
 * @param knockOut whether the NPC is knocked out when it ends (false when it was hit in combat)
 */
public record Stagger(long until, boolean knockOut) {
    public static final Stagger NONE = new Stagger(Long.MIN_VALUE, false);

    public static final Codec<Stagger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("until").forGetter(Stagger::until),
            Codec.BOOL.fieldOf("knock_out").forGetter(Stagger::knockOut)
    ).apply(instance, Stagger::new));

    public boolean isNone() {
        return until == Long.MIN_VALUE;
    }
}
