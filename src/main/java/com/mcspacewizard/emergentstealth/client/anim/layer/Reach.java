package com.mcspacewizard.emergentstealth.client.anim.layer;

import com.mcspacewizard.emergentstealth.anim.Bone;
import com.mcspacewizard.emergentstealth.anim.HumanoidPose;
import com.mcspacewizard.emergentstealth.anim.LimbIk;

/** Points a limb's end at a target with a two-bone solve (elbow or knee). */
final class Reach {
    private Reach() {}

    /**
     * @param y        target below the limb's pivot, model pixels
     * @param z        target behind the pivot, model pixels (-z is the front)
     * @param bendSign -1 for elbows (flex to the front), +1 for knees
     */
    static void to(HumanoidPose pose, Bone bone, float y, float z, float roll, float bendSign, float weight) {
        boolean arm = bone.isArm();
        float[] solved = new float[2];
        LimbIk.solve(y, z, arm ? LimbIk.ARM_UPPER : LimbIk.LEG_UPPER, arm ? LimbIk.ARM_LOWER : LimbIk.LEG_LOWER, bendSign, solved);
        pose.setRot(bone, solved[0], 0.0F, roll, weight);
        pose.setBend(bone, solved[1], weight);
    }
}
