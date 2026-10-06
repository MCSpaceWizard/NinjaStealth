package com.mcspacewizard.emergentstealth.ai.nav;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;

/** Ground navigation using {@link StealthNodeEvaluator} (doors, fence gates, ladders). */
public class StealthNavigation extends GroundPathNavigation {
    public StealthNavigation(Mob mob, Level level) {
        super(mob, level);
        this.setCanOpenDoors(true);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new StealthNodeEvaluator();
        this.nodeEvaluator.setCanOpenDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canUpdatePath() {
        // Also re-plan while on a ladder (vanilla only re-plans on the ground).
        return super.canUpdatePath() || this.mob.onClimbable();
    }
}
