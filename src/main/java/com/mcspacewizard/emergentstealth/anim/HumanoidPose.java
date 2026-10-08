package com.mcspacewizard.emergentstealth.anim;

import java.util.Arrays;

/**
 * A pose expressed relative to whatever the base pose turns out to be (design doc 17 §8).
 *
 * <p>Layers are evaluated before the base is known: for NPCs the base is vanilla's walk pose, for players it is
 * the bone PAL hands us. So every channel is kept as an affine function of the base value {@code v}:
 * {@code result = (1 - amount) * v + offset}. An override layer with weight {@code w} and target {@code t}
 * maps it to {@code (1 - w) * result + w * t}; an additive layer adds {@code w * delta}. Applying the pose
 * is then one multiply-add per channel, and the same pose can be applied to the main model, the armour models
 * and the outfit layers.
 *
 * <p>Channels per bone: rotation x/y/z (radians), position x/y/z (pixels, y down), and the elbow/knee bend
 * (radians, same sense as {@code xRot}). Override amounts are kept per channel group (rotation, position,
 * bend), so a layer can take the rotation and leave the position alone.
 */
public final class HumanoidPose {
    public static final int ROT_X = 0;
    public static final int ROT_Y = 1;
    public static final int ROT_Z = 2;
    public static final int POS_X = 3;
    public static final int POS_Y = 4;
    public static final int POS_Z = 5;
    public static final int BEND = 6;
    public static final int CHANNELS = 7;

    private static final int GROUP_ROT = 0;
    private static final int GROUP_POS = 1;
    private static final int GROUP_BEND = 2;
    private static final int GROUPS = 3;

    private final float[] offset = new float[Bone.COUNT * CHANNELS];
    private final float[] amount = new float[Bone.COUNT * GROUPS];

    public HumanoidPose() {}

    /** Back to "use the base pose unchanged". */
    public HumanoidPose reset() {
        Arrays.fill(offset, 0.0F);
        Arrays.fill(amount, 0.0F);
        return this;
    }

    public HumanoidPose copyFrom(HumanoidPose other) {
        System.arraycopy(other.offset, 0, offset, 0, offset.length);
        System.arraycopy(other.amount, 0, amount, 0, amount.length);
        return this;
    }

    private static int group(int channel) {
        return channel <= ROT_Z ? GROUP_ROT : channel <= POS_Z ? GROUP_POS : GROUP_BEND;
    }

    // --- Writing -------------------------------------------------------------------------------------

    /** Blends one group towards fixed targets with weight {@code w} (0 = no change, 1 = replace). */
    private void overrideGroup(Bone bone, int firstChannel, int count, float w, float a, float b, float c) {
        if (w <= 0.0F) {
            return;
        }
        w = Math.min(w, 1.0F);
        int base = bone.ordinal() * CHANNELS + firstChannel;
        float keep = 1.0F - w;
        offset[base] = keep * offset[base] + w * a;
        if (count > 1) {
            offset[base + 1] = keep * offset[base + 1] + w * b;
            offset[base + 2] = keep * offset[base + 2] + w * c;
        }
        int g = bone.ordinal() * GROUPS + group(firstChannel);
        amount[g] = 1.0F - keep * (1.0F - amount[g]);
    }

    public HumanoidPose setRot(Bone bone, float x, float y, float z, float w) {
        overrideGroup(bone, ROT_X, 3, w, x, y, z);
        return this;
    }

    public HumanoidPose setPos(Bone bone, float x, float y, float z, float w) {
        overrideGroup(bone, POS_X, 3, w, x, y, z);
        return this;
    }

    public HumanoidPose setBend(Bone bone, float bend, float w) {
        overrideGroup(bone, BEND, 1, w, bend, 0.0F, 0.0F);
        return this;
    }

    public HumanoidPose addRot(Bone bone, float x, float y, float z, float w) {
        int base = bone.ordinal() * CHANNELS;
        offset[base + ROT_X] += w * x;
        offset[base + ROT_Y] += w * y;
        offset[base + ROT_Z] += w * z;
        return this;
    }

    public HumanoidPose addPos(Bone bone, float x, float y, float z, float w) {
        int base = bone.ordinal() * CHANNELS;
        offset[base + POS_X] += w * x;
        offset[base + POS_Y] += w * y;
        offset[base + POS_Z] += w * z;
        return this;
    }

    public HumanoidPose addBend(Bone bone, float bend, float w) {
        offset[bone.ordinal() * CHANNELS + BEND] += w * bend;
        return this;
    }

    // --- Reading -------------------------------------------------------------------------------------

    /** The final value of a channel given the base pose's value. */
    public float apply(Bone bone, int channel, float base) {
        float a = amount[bone.ordinal() * GROUPS + group(channel)];
        return (1.0F - a) * base + offset[bone.ordinal() * CHANNELS + channel];
    }

    /** The value of a channel over a zero base (for the root, which has no base pose). */
    public float get(Bone bone, int channel) {
        return offset[bone.ordinal() * CHANNELS + channel];
    }

    /** How much of the base survives in this channel's group (1 = untouched by overrides). */
    public float baseWeight(Bone bone, int channel) {
        return 1.0F - amount[bone.ordinal() * GROUPS + group(channel)];
    }

    /** Whether any layer wrote to this bone. */
    public boolean touched(Bone bone) {
        int g = bone.ordinal() * GROUPS;
        if (amount[g] != 0.0F || amount[g + 1] != 0.0F || amount[g + 2] != 0.0F) {
            return true;
        }
        int base = bone.ordinal() * CHANNELS;
        for (int i = 0; i < CHANNELS; i++) {
            if (offset[base + i] != 0.0F) {
                return true;
            }
        }
        return false;
    }

    /** Whether the pose changes anything at all. */
    public boolean isIdentity() {
        for (Bone bone : Bone.VALUES) {
            if (touched(bone)) {
                return false;
            }
        }
        return true;
    }
}
