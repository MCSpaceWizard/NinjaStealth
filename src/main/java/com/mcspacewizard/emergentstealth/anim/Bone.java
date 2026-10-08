package com.mcspacewizard.emergentstealth.anim;

/**
 * The bones of a humanoid pose (design doc 17 §8). The six model parts plus {@link #ROOT}, the whole body.
 * Limb channels are in model-part space: rotations in radians like {@code ModelPart.xRot}, positions in model
 * pixels relative to the part's default pivot (y points down, -z is the front).
 */
public enum Bone {
    HEAD("head"),
    BODY("body"),
    RIGHT_ARM("right_arm"),
    LEFT_ARM("left_arm"),
    RIGHT_LEG("right_leg"),
    LEFT_LEG("left_leg"),
    /**
     * The whole body, drawn by the renderer (not a model part): rotation in radians about the hips
     * (x = pitch, y = yaw, z = roll), position in pixels (y down).
     */
    ROOT("root");

    public static final Bone[] VALUES = values();
    public static final int COUNT = VALUES.length;

    public final String partName;

    Bone(String partName) {
        this.partName = partName;
    }

    /** Arms and legs: the bones with an elbow or knee. */
    public boolean isLimb() {
        return this == RIGHT_ARM || this == LEFT_ARM || this == RIGHT_LEG || this == LEFT_LEG;
    }

    public boolean isArm() {
        return this == RIGHT_ARM || this == LEFT_ARM;
    }

    /** The same bone on the other side (head, body and root are their own mirror). */
    public Bone mirror() {
        return switch (this) {
            case RIGHT_ARM -> LEFT_ARM;
            case LEFT_ARM -> RIGHT_ARM;
            case RIGHT_LEG -> LEFT_LEG;
            case LEFT_LEG -> RIGHT_LEG;
            default -> this;
        };
    }
}
