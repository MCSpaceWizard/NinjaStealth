package com.mcspacewizard.emergentstealth.authoring;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Fits the world's terrain to a placed compound (design doc 33 §4). Columns covered by a module's box are the
 * <b>footprint</b>; the columns within {@code blend} blocks of it are the <b>ring</b>. The compound's ground line is
 * the layer below its origin (where every module's ground goes).
 *
 * <ul>
 *   <li>{@link Mode#FIT}: before the templates go down, the footprint is cleared above the ground line (hills, trees)
 *       and filled below it (holes, water, overhangs), at most {@code depth} blocks down, so the compound stands on
 *       solid ground. After, the ring slopes from the ground line out to the natural surface, at most one block
 *       up or down per block (an embankment or a cutting); where the ground is further off than the ring is wide,
 *       a step remains at its outer edge.</li>
 *   <li>{@link Mode#REPLACE}: the footprint and the ring are flattened to the ground line: the compound brings a
 *       level stretch of its own ground with it.</li>
 *   <li>{@link Mode#EXACT}: nothing; only the templates are placed.</li>
 * </ul>
 *
 * The surface keeps the world's own top block (grass, sand, snow ...); fill under it is dirt for a few blocks, then
 * stone. {@link Plan} is made before anything changes, so the undo snapshot can cover every block touched.
 */
public final class TerrainFit {
    private TerrainFit() {}

    public enum Mode implements StringRepresentable {
        FIT, REPLACE, EXACT;

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Blocks set without neighbour updates (no sand falling mid-way, no plants popping), like templates. */
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    /** Dirt this many blocks under a filled surface, stone below. */
    private static final int SOIL = 3;

    /**
     * The columns to work on and the natural surface of each, read before anything changes.
     *
     * @param ground  the ground line (y of the compound's ground layer)
     * @param area    footprint plus ring, as a box spanning the heights involved (for the undo snapshot)
     * @param minX    west edge of the grid ({@code area} without the height)
     * @param distance per column: 0 in the footprint, 1..blend in the ring, -1 outside
     * @param surface per column: the y of the natural top block (no leaves), before placing
     * @param top     per column: the y of the highest non-air block (trees included), before placing
     */
    public record Plan(Compound.Terrain terrain, int ground, BoundingBox area, int minX, int minZ, int sizeX, int sizeZ,
                       int[] distance, int[] surface, int[] top, BlockState[] cover) {
        int index(int x, int z) {
            return (x - minX) * sizeZ + (z - minZ);
        }
    }

    /** Reads the terrain around the module boxes. Returns null for {@link Mode#EXACT}. */
    public static Plan plan(ServerLevel level, Compound.Terrain terrain, List<BoundingBox> modules, int ground) {
        if (terrain.mode() == Mode.EXACT || modules.isEmpty()) {
            return null;
        }
        int blend = terrain.blend();
        BoundingBox all = BoundingBox.encapsulatingBoxes(modules).orElseThrow();
        int minX = all.minX() - blend, minZ = all.minZ() - blend;
        int sizeX = all.getXSpan() + 2 * blend, sizeZ = all.getZSpan() + 2 * blend;
        int[] distance = new int[sizeX * sizeZ];
        java.util.Arrays.fill(distance, -1);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (BoundingBox box : modules) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    int i = (x - minX) * sizeZ + (z - minZ);
                    if (distance[i] != 0) {
                        distance[i] = 0;
                        queue.add(new int[] {x, z});
                    }
                }
            }
        }
        // Chebyshev distance from the footprint, out to the blend width (breadth-first, 8 neighbours).
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int d = distance[(c[0] - minX) * sizeZ + (c[1] - minZ)];
            if (d >= blend) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = c[0] + dx, z = c[1] + dz;
                    if (x < minX || z < minZ || x >= minX + sizeX || z >= minZ + sizeZ) {
                        continue;
                    }
                    int i = (x - minX) * sizeZ + (z - minZ);
                    if (distance[i] < 0) {
                        distance[i] = d + 1;
                        queue.add(new int[] {x, z});
                    }
                }
            }
        }

        int[] surface = new int[distance.length];
        int[] top = new int[distance.length];
        BlockState[] cover = new BlockState[distance.length];
        int lowest = ground - terrain.depth(), highest = all.maxY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x < minX + sizeX; x++) {
            for (int z = minZ; z < minZ + sizeZ; z++) {
                int i = (x - minX) * sizeZ + (z - minZ);
                if (distance[i] < 0) {
                    continue;
                }
                surface[i] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                top[i] = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                cover[i] = coverAt(level, pos.set(x, surface[i], z));
                lowest = Math.min(lowest, Math.max(surface[i], ground - terrain.depth()));
                highest = Math.max(highest, top[i]);
            }
        }
        lowest = Math.max(lowest, level.getMinY());
        BoundingBox area = new BoundingBox(minX, lowest, minZ, minX + sizeX - 1, highest, minZ + sizeZ - 1);
        return new Plan(terrain, ground, area, minX, minZ, sizeX, sizeZ, distance, surface, top, cover);
    }

    /** The block to surface new ground with: the world's own top block if it's ground, else grass. */
    private static BlockState coverAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.STONE) || state.is(Blocks.TERRACOTTA)) {
            return state.is(Blocks.DIRT) || state.is(Blocks.ROOTED_DIRT) ? Blocks.GRASS_BLOCK.defaultBlockState() : state;
        }
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }

    /** What goes {@code depth} blocks under a surface of {@code cover}. */
    private static BlockState fillUnder(BlockState cover, int depth) {
        if (depth > SOIL) {
            return Blocks.STONE.defaultBlockState();
        }
        if (cover.is(BlockTags.SAND)) {
            return cover.getBlock().defaultBlockState();
        }
        if (cover.is(Blocks.STONE) || cover.is(Blocks.TERRACOTTA)) {
            return cover;
        }
        return Blocks.DIRT.defaultBlockState();
    }

    /** Before the templates: clear and fill the footprint (and, for replace, the ring) to the ground line. */
    public static void before(ServerLevel level, Plan plan) {
        Mode mode = plan.terrain().mode();
        for (int x = plan.minX(); x < plan.minX() + plan.sizeX(); x++) {
            for (int z = plan.minZ(); z < plan.minZ() + plan.sizeZ(); z++) {
                int i = plan.index(x, z);
                int d = plan.distance()[i];
                if (d == 0 || (d > 0 && mode == Mode.REPLACE)) {
                    shape(level, plan, x, z, i, plan.ground());
                }
            }
        }
    }

    /** After the templates: slope the ring between the ground line and the natural surface (fit only). */
    public static void after(ServerLevel level, Plan plan) {
        if (plan.terrain().mode() != Mode.FIT) {
            return;
        }
        for (int x = plan.minX(); x < plan.minX() + plan.sizeX(); x++) {
            for (int z = plan.minZ(); z < plan.minZ() + plan.sizeZ(); z++) {
                int i = plan.index(x, z);
                int d = plan.distance()[i];
                if (d <= 0) {
                    continue;
                }
                // An embankment or cutting at most one block up or down per block out from the footprint's edge:
                // ground that is already that close to the ground line is left alone.
                int natural = Math.max(plan.surface()[i], plan.ground() - plan.terrain().depth());
                int target = Math.clamp(natural, plan.ground() - d, plan.ground() + d);
                if (target == natural) {
                    continue;
                }
                shape(level, plan, x, z, i, target);
            }
        }
    }

    /**
     * Makes column (x, z) a solid ground surface at {@code height}: everything above it up to the column's old top
     * is cleared (terrain, trees, water), and everything from there down to solid ground is filled.
     */
    private static void shape(ServerLevel level, Plan plan, int x, int z, int i, int height) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = plan.top()[i]; y > height; y--) {
            if (!level.getBlockState(pos.set(x, y, z)).isAir()) {
                level.setBlock(pos, air, FLAGS);
            }
        }
        BlockState cover = plan.cover()[i];
        level.setBlock(pos.set(x, height, z), cover, FLAGS);
        int floor = plan.ground() - plan.terrain().depth();
        for (int y = height - 1; y >= floor && y >= level.getMinY(); y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (solidGround(state) && y <= plan.surface()[i]) {
                break;
            }
            level.setBlock(pos, fillUnder(cover, height - y), FLAGS);
        }
    }

    /** Natural solid ground (not plants, logs, leaves or fluids) that a fill can stop at. */
    private static boolean solidGround(BlockState state) {
        return state.isSolid() && state.getFluidState().isEmpty() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES);
    }
}
