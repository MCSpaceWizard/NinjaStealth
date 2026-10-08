package com.mcspacewizard.emergentstealth.ai.group;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.stealth.StealthTags;
import com.mcspacewizard.emergentstealth.stealth.light.ExposureModel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Coordinated search (design doc 14 §4). Guards searching for the same target, or the same spot, share a
 * group: the group picks search points biased toward hiding places, each guard claims a different one, and
 * the search lasts longer the more guards take part.
 */
public final class SearchGroups {
    private static final Map<ServerLevel, SearchGroups> BY_LEVEL = new WeakHashMap<>();
    private static final double JOIN_RADIUS = 12.0;
    private static final int POINT_RADIUS = 10;
    private static final int MAX_POINTS = 12;
    private static final int CANDIDATES = 32;

    private final List<Group> groups = new ArrayList<>();
    private final Map<UUID, Group> byMember = new HashMap<>();

    public static SearchGroups get(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, l -> new SearchGroups());
    }

    /** A search point and who (if anyone) is checking it. */
    public static final class Point {
        public final BlockPos pos;
        @Nullable UUID claimedBy;
        boolean visited;

        Point(BlockPos pos) {
            this.pos = pos;
        }

        public boolean visited() {
            return visited;
        }

        public @Nullable UUID claimedBy() {
            return claimedBy;
        }
    }

    public static final class Group {
        final Vec3 center;
        final @Nullable UUID target;
        final long started;
        final Set<UUID> members = new LinkedHashSet<>();
        final List<Point> points = new ArrayList<>();
        /** A searcher found the target again (no escape Insight). */
        boolean found;

        Group(Vec3 center, @Nullable UUID target, long started) {
            this.center = center;
            this.target = target;
            this.started = started;
        }

        public Vec3 center() {
            return center;
        }

        public long started() {
            return started;
        }

        public int size() {
            return members.size();
        }

        public List<Point> points() {
            return points;
        }

        /** When the search ends: longer with more searchers (A-08), capped at twice the base. */
        public long endTick() {
            double factor = Math.min(2.0, 1.0 + 0.3 * Math.max(0, members.size() - 1));
            return started + Math.round(ESConfig.SEARCH_SECONDS.get() * 20.0 * factor);
        }
    }

    /** Joins (or starts) the search for {@code target} around {@code center}. */
    public Group join(ServerLevel level, StealthNpc npc, Vec3 center, @Nullable UUID target, long now) {
        Group current = byMember.get(npc.getUUID());
        if (current != null) {
            return current;
        }
        Group group = null;
        for (Group g : groups) {
            if ((target != null && target.equals(g.target)) || g.center.distanceToSqr(center) <= JOIN_RADIUS * JOIN_RADIUS) {
                group = g;
                break;
            }
        }
        if (group == null) {
            group = new Group(center, target, now);
            generatePoints(level, group, npc.getRandom());
            groups.add(group);
        }
        group.members.add(npc.getUUID());
        byMember.put(npc.getUUID(), group);
        return group;
    }

    public @Nullable Group groupOf(StealthNpc npc) {
        return byMember.get(npc.getUUID());
    }

    /** A searcher found its target again (it went back to hunting or fighting). */
    public void markFound(StealthNpc npc) {
        Group group = byMember.get(npc.getUUID());
        if (group != null) {
            group.found = true;
        }
    }

    public void leave(StealthNpc npc) {
        Group group = byMember.remove(npc.getUUID());
        if (group == null) {
            return;
        }
        if (group.members.size() == 1 && !group.found && group.target != null
                && npc.level().getPlayerByUUID(group.target) instanceof net.minecraft.server.level.ServerPlayer escaped) {
            // The whole search ended without finding them: they escaped (design doc 26 §2).
            com.mcspacewizard.emergentstealth.progression.Skills.awardInsight(escaped,
                    com.mcspacewizard.emergentstealth.progression.SkillPath.SHINOBI,
                    com.mcspacewizard.emergentstealth.progression.Skills.INSIGHT_ESCAPE);
        }
        group.members.remove(npc.getUUID());
        for (Point point : group.points) {
            if (npc.getUUID().equals(point.claimedBy)) {
                point.claimedBy = null;
            }
        }
        if (group.members.isEmpty()) {
            groups.remove(group);
        }
    }

    /** The point this NPC is checking, claiming the nearest free one if it has none. Null when none are left. */
    public @Nullable Point claim(ServerLevel level, StealthNpc npc) {
        Group group = byMember.get(npc.getUUID());
        if (group == null) {
            return null;
        }
        for (Point point : group.points) {
            if (npc.getUUID().equals(point.claimedBy) && !point.visited) {
                return point;
            }
        }
        List<Point> free = group.points.stream().filter(p -> !p.visited && p.claimedBy == null).toList();
        if (free.isEmpty()) {
            // Everything checked: look again, starting with the spots nobody is at.
            group.points.forEach(p -> p.visited = false);
            free = group.points.stream().filter(p -> p.claimedBy == null).toList();
            if (free.isEmpty()) {
                return null;
            }
        }
        Point best = free.stream().min(Comparator.comparingDouble(p -> npc.distanceToSqr(Vec3.atBottomCenterOf(p.pos)))).orElseThrow();
        best.claimedBy = npc.getUUID();
        return best;
    }

    /** The NPC finished checking its point. */
    public void done(StealthNpc npc, Point point) {
        if (npc.getUUID().equals(point.claimedBy)) {
            point.claimedBy = null;
            point.visited = true;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Points: dark, covered, bushy spots near the centre (guards check the shadows)

    private static void generatePoints(ServerLevel level, Group group, RandomSource random) {
        BlockPos origin = BlockPos.containing(group.center);
        record Scored(BlockPos pos, double score) {}
        List<Scored> candidates = new ArrayList<>();
        Set<BlockPos> seen = new LinkedHashSet<>();
        for (int i = 0; i < CANDIDATES; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 2.0 + random.nextDouble() * (POINT_RADIUS - 2.0);
            BlockPos column = origin.offset((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius));
            BlockPos ground = standable(level, column);
            if (ground == null || !seen.add(ground)) {
                continue;
            }
            double dark = 1.0 - ExposureModel.INSTANCE.exposure(level, Vec3.atBottomCenterOf(ground).add(0.0, 1.0, 0.0));
            int cover = 0;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (!level.getBlockState(ground.above().relative(direction)).getCollisionShape(level, ground).isEmpty()) {
                    cover++;
                }
            }
            BlockState feet = level.getBlockState(ground);
            double bush = feet.is(StealthTags.CONCEALING_FOLIAGE) ? 0.5 : 0.0;
            candidates.add(new Scored(ground, dark + cover * 0.15 + bush + random.nextDouble() * 0.2));
        }
        candidates.sort(Comparator.comparingDouble(Scored::score).reversed());
        for (int i = 0; i < Math.min(MAX_POINTS, candidates.size()); i++) {
            group.points.add(new Point(candidates.get(i).pos()));
        }
        if (group.points.isEmpty()) {
            group.points.add(new Point(origin));
        }
    }

    /** A spot within ±4 blocks of the column's height where a guard can stand (feet and head clear, floor below). */
    private static @Nullable BlockPos standable(ServerLevel level, BlockPos column) {
        for (int dy = 0; dy <= 4; dy++) {
            for (int sign : new int[] {1, -1}) {
                BlockPos feet = column.above(dy * sign);
                if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                        && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
                    return feet.immutable();
                }
                if (dy == 0) {
                    break;
                }
            }
        }
        return null;
    }

}
