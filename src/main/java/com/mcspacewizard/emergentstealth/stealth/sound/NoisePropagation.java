package com.mcspacewizard.emergentstealth.stealth.sound;

import java.util.ArrayDeque;
import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.Reference2FloatOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

/**
 * How sound travels through blocks (design doc 16 §2, S-01). Pure world maths, no NPCs:
 * <ul>
 *   <li>{@link #clearLine}: whether the straight line between two points passes only through open blocks.
 *       That's the common outdoor case, and then the cost is simply the distance.</li>
 *   <li>{@link Flood}: otherwise a bounded shortest-path search (26 neighbours) from the noise. Moving costs the
 *       step length (1, √2 or √3); entering a block adds its {@link #extraCost muffling}. Sound therefore goes
 *       round the corner and through the open door before it goes through the stone wall.</li>
 * </ul>
 * Never loads chunks: unloaded blocks stop sound. All of this runs on the server thread only.
 */
public final class NoisePropagation {
    private NoisePropagation() {}

    /** The flood fill stays within this many blocks above and below the noise. */
    public static final int VERTICAL_BOUND = 10;
    /** The flood fill never reaches further than this horizontally (bounds memory for explosions). */
    public static final int HORIZONTAL_CAP = 48;
    /** Extra cost through glass, panes, doors, trapdoors, leaves, fences ({@link SoundTags#MUFFLES_SOUND_LIGHT}). */
    public static final float COST_LIGHT = 3.0F;
    /** Extra cost through any other solid block. */
    public static final float COST_SOLID = 8.0F;
    /** Extra cost through wool and other dampening blocks ({@link SoundTags#MUFFLES_SOUND_HEAVY}). */
    public static final float COST_HEAVY = 16.0F;
    /** Marks a cell sound can't enter at all (unloaded). */
    static final float BLOCKED = Float.POSITIVE_INFINITY;

    // ------------------------------------------------------------------------------------------------
    // Block costs

    /** Per block state extra cost. Swapped out (not cleared) when tags reload, so readers never see it half-built. */
    private static volatile Reference2FloatOpenHashMap<BlockState> stateCosts = newCostCache();

    private static Reference2FloatOpenHashMap<BlockState> newCostCache() {
        Reference2FloatOpenHashMap<BlockState> map = new Reference2FloatOpenHashMap<>();
        map.defaultReturnValue(-1.0F);
        return map;
    }

    /** Forget cached block costs (tags changed). */
    public static void invalidateCostCache() {
        stateCosts = newCostCache();
    }

    /**
     * Extra cost of sound passing through a block: 0 for air, open doors and other non-solid blocks,
     * {@link #COST_LIGHT} / {@link #COST_SOLID} / {@link #COST_HEAVY} otherwise. Closed doors, trapdoors and
     * gates count as light even when a pack leaves them out of the tag.
     */
    public static float extraCost(BlockState state) {
        if (state.isAir()) {
            return 0.0F;
        }
        Reference2FloatOpenHashMap<BlockState> cache = stateCosts;
        float cost = cache.getFloat(state);
        if (cost < 0.0F) {
            cost = computeExtraCost(state);
            cache.put(state, cost);
        }
        return cost;
    }

    private static float computeExtraCost(BlockState state) {
        if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)) {
            return 0.0F;
        }
        if (state.is(SoundTags.MUFFLES_SOUND_HEAVY)) {
            return COST_HEAVY;
        }
        if (state.is(SoundTags.MUFFLES_SOUND_LIGHT)) {
            return COST_LIGHT;
        }
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock) {
            return COST_LIGHT;
        }
        boolean full;
        try {
            full = state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException e) {
            // Shapes that need a real world: fall back to "does it fill its cube visually".
            full = state.canOcclude();
        }
        return full ? COST_SOLID : 0.0F;
    }

    // ------------------------------------------------------------------------------------------------
    // Block access without loading chunks

    /** Reads block states with a one-section cache. Returns null for unloaded chunks. */
    static final class BlockReader {
        private final ServerLevel level;
        private final int minY;
        private final int maxY;
        private long cachedChunkKey = Long.MIN_VALUE;
        private @Nullable LevelChunk cachedChunk;
        private int cachedSectionIndex = Integer.MIN_VALUE;
        private @Nullable LevelChunkSection cachedSection;

        BlockReader(ServerLevel level) {
            this.level = level;
            this.minY = level.getMinY();
            this.maxY = level.getMaxY();
        }

        @Nullable BlockState get(int x, int y, int z) {
            if (y < minY || y > maxY) {
                return Blocks.AIR.defaultBlockState();
            }
            int cx = SectionPos.blockToSectionCoord(x);
            int cz = SectionPos.blockToSectionCoord(z);
            long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
            if (key != cachedChunkKey) {
                cachedChunkKey = key;
                cachedChunk = level.getChunkSource().getChunkNow(cx, cz);
                cachedSectionIndex = Integer.MIN_VALUE;
            }
            if (cachedChunk == null) {
                return null;
            }
            int sectionIndex = level.getSectionIndex(y);
            if (sectionIndex != cachedSectionIndex) {
                cachedSectionIndex = sectionIndex;
                cachedSection = cachedChunk.getSection(sectionIndex);
            }
            LevelChunkSection section = cachedSection;
            if (section == null || section.hasOnlyAir()) {
                return Blocks.AIR.defaultBlockState();
            }
            return section.getBlockState(x & 15, y & 15, z & 15);
        }

        /** {@link #extraCost} of a cell, or {@link #BLOCKED} when unloaded. */
        float cost(int x, int y, int z) {
            BlockState state = get(x, y, z);
            return state == null ? BLOCKED : extraCost(state);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Straight line

    /**
     * True when every block the segment passes through (except the two end cells) lets sound through freely.
     * Voxel walk (Amanatides & Woo), so no block is skipped.
     */
    public static boolean clearLine(ServerLevel level, Vec3 from, Vec3 to) {
        return clearLine(new BlockReader(level), from, to);
    }

    static boolean clearLine(BlockReader reader, Vec3 from, Vec3 to) {
        int x = Mth.floor(from.x);
        int y = Mth.floor(from.y);
        int z = Mth.floor(from.z);
        int endX = Mth.floor(to.x);
        int endY = Mth.floor(to.y);
        int endZ = Mth.floor(to.z);
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tDeltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double tDeltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double tMaxX = stepX == 0 ? Double.MAX_VALUE : tDeltaX * (stepX > 0 ? (x + 1 - from.x) : (from.x - x));
        double tMaxY = stepY == 0 ? Double.MAX_VALUE : tDeltaY * (stepY > 0 ? (y + 1 - from.y) : (from.y - y));
        double tMaxZ = stepZ == 0 ? Double.MAX_VALUE : tDeltaZ * (stepZ > 0 ? (z + 1 - from.z) : (from.z - z));
        int guard = Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z) + 2;
        while (guard-- > 0) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                if (tMaxX > 1.0) {
                    return true;
                }
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                if (tMaxY > 1.0) {
                    return true;
                }
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                if (tMaxZ > 1.0) {
                    return true;
                }
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (x == endX && y == endY && z == endZ) {
                return true;
            }
            if (reader.cost(x, y, z) != 0.0F) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------
    // Flood fill

    private static final int[] DX = new int[26];
    private static final int[] DY = new int[26];
    private static final int[] DZ = new int[26];
    private static final float[] STEP = new float[26];

    static {
        int i = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    DX[i] = dx;
                    DY[i] = dy;
                    DZ[i] = dz;
                    STEP[i] = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    i++;
                }
            }
        }
    }

    /** Recycled flood buffers, so a footstep doesn't allocate a fresh grid. */
    private static final ArrayDeque<Flood> POOL = new ArrayDeque<>();
    private static final int POOL_SIZE = 4;

    /**
     * Starts a flood fill from {@code origin}. Add targets with {@link Flood#addTarget}, then call
     * {@link Flood#run} until {@link Flood#isDone()}, read the costs and {@link Flood#release()} it.
     *
     * @param bound the highest cost worth exploring (the loudest effective loudness of any target)
     */
    static Flood flood(ServerLevel level, Vec3 origin, float bound) {
        int radius = Math.min(HORIZONTAL_CAP, Mth.ceil(bound) + 1);
        int ox = Mth.floor(origin.x);
        int oy = Mth.floor(origin.y);
        int oz = Mth.floor(origin.z);
        int size = radius * 2 + 1;
        int height = VERTICAL_BOUND * 2 + 1;
        int cells = size * size * height;
        Flood flood = null;
        for (Flood pooled : POOL) {
            if (pooled.capacity() >= cells) {
                flood = pooled;
                break;
            }
        }
        if (flood != null) {
            POOL.remove(flood);
        } else {
            flood = new Flood(Math.max(cells, 4096));
        }
        flood.start(level, ox - radius, oy - VERTICAL_BOUND, oz - radius, size, height, size, ox, oy, oz, bound);
        return flood;
    }

    /** A resumable bounded Dijkstra from one noise. Arrays are reused through generations, never cleared. */
    static final class Flood {
        private final float[] dist;
        private final float[] cost;
        private final int[] stamp;
        private final int[] targetMark;
        private int generation;

        private int[] heapNode = new int[1024];
        private float[] heapKey = new float[1024];
        private int heapSize;

        private int[] targetCell = new int[8];
        private float[] targetCost = new float[8];
        private int targetCount;
        private int remaining;

        private @Nullable BlockReader reader;
        private int minX;
        private int minY;
        private int minZ;
        private int sizeX;
        private int sizeY;
        private int sizeZ;
        private float bound;
        private boolean done;
        private int visited;

        Flood(int capacity) {
            dist = new float[capacity];
            cost = new float[capacity];
            stamp = new int[capacity];
            targetMark = new int[capacity];
        }

        int capacity() {
            return dist.length;
        }

        void start(ServerLevel level, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
                   int ox, int oy, int oz, float bound) {
            this.reader = new BlockReader(level);
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.bound = bound;
            this.done = false;
            this.visited = 0;
            this.heapSize = 0;
            this.targetCount = 0;
            this.remaining = 0;
            if (++generation == Integer.MAX_VALUE) {
                Arrays.fill(stamp, 0);
                Arrays.fill(targetMark, 0);
                generation = 1;
            }
            int origin = index(ox, oy, oz);
            touch(origin, ox, oy, oz);
            // The noise starts inside its own block (a door, a placed block): no muffling for that one.
            cost[origin] = 0.0F;
            dist[origin] = 0.0F;
            push(origin, 0.0F);
        }

        /** Adds a listener cell; returns its target id, or -1 if it's outside the search box. */
        int addTarget(Vec3 ear) {
            int x = Mth.floor(ear.x);
            int y = Mth.floor(ear.y);
            int z = Mth.floor(ear.z);
            if (!inBox(x, y, z)) {
                return -1;
            }
            if (targetCount == targetCell.length) {
                targetCell = Arrays.copyOf(targetCell, targetCount * 2);
                targetCost = Arrays.copyOf(targetCost, targetCount * 2);
            }
            int cell = index(x, y, z);
            targetCell[targetCount] = cell;
            targetCost[targetCount] = Float.NaN;
            targetMark[cell] = generation;
            remaining++;
            return targetCount++;
        }

        /** Cost to reach a target, or NaN if it was never reached within the bound. */
        float targetCost(int target) {
            return targetCost[target];
        }

        boolean isDone() {
            return done;
        }

        /** Nodes settled so far (for stats). */
        int visited() {
            return visited;
        }

        /**
         * Explores up to {@code budget} nodes. Stops early once every target is settled, or when nothing within
         * the bound is left.
         *
         * @return nodes used
         */
        int run(int budget) {
            if (done) {
                return 0;
            }
            if (remaining == 0) {
                done = true;
                return 0;
            }
            int used = 0;
            while (heapSize > 0 && used < budget) {
                int node = heapNode[0];
                float key = heapKey[0];
                pop();
                used++;
                if (key > dist[node]) {
                    continue; // stale entry
                }
                if (key > bound) {
                    heapSize = 0;
                    break;
                }
                visited++;
                if (targetMark[node] == generation) {
                    for (int t = 0; t < targetCount; t++) {
                        if (targetCell[t] == node && Float.isNaN(targetCost[t])) {
                            targetCost[t] = key;
                            remaining--;
                        }
                    }
                    if (remaining == 0) {
                        done = true;
                        return used;
                    }
                }
                int x = minX + node % sizeX;
                int rest = node / sizeX;
                int z = minZ + rest % sizeZ;
                int y = minY + rest / sizeZ;
                for (int d = 0; d < 26; d++) {
                    int nx = x + DX[d];
                    int ny = y + DY[d];
                    int nz = z + DZ[d];
                    if (!inBox(nx, ny, nz)) {
                        continue;
                    }
                    int next = index(nx, ny, nz);
                    touch(next, nx, ny, nz);
                    float enter = cost[next];
                    if (enter == BLOCKED) {
                        continue;
                    }
                    float squeeze = 0.0F;
                    if (STEP[d] > 1.0F) {
                        squeeze = squeezeCost(x, y, z, DX[d], DY[d], DZ[d]);
                        if (squeeze == BLOCKED) {
                            continue;
                        }
                    }
                    float total = key + STEP[d] + enter + squeeze;
                    if (total <= bound && total < dist[next]) {
                        dist[next] = total;
                        push(next, total);
                    }
                }
            }
            if (heapSize == 0) {
                done = true;
            }
            return used;
        }

        /**
         * A diagonal step slips between blocks that only touch at an edge. Charge the cheapest of the
         * single-axis cells it cuts past, so a diagonal wall still muffles.
         */
        private float squeezeCost(int x, int y, int z, int dx, int dy, int dz) {
            float best = BLOCKED;
            if (dx != 0) {
                best = Math.min(best, cellCost(x + dx, y, z));
            }
            if (dy != 0) {
                best = Math.min(best, cellCost(x, y + dy, z));
            }
            if (dz != 0) {
                best = Math.min(best, cellCost(x, y, z + dz));
            }
            return best;
        }

        private float cellCost(int x, int y, int z) {
            if (!inBox(x, y, z)) {
                return BLOCKED;
            }
            int index = index(x, y, z);
            touch(index, x, y, z);
            return cost[index];
        }

        private boolean inBox(int x, int y, int z) {
            return x >= minX && x < minX + sizeX && y >= minY && y < minY + sizeY && z >= minZ && z < minZ + sizeZ;
        }

        private int index(int x, int y, int z) {
            return ((y - minY) * sizeZ + (z - minZ)) * sizeX + (x - minX);
        }

        private void touch(int index, int x, int y, int z) {
            if (stamp[index] != generation) {
                stamp[index] = generation;
                dist[index] = Float.POSITIVE_INFINITY;
                cost[index] = reader.cost(x, y, z);
            }
        }

        private void push(int node, float key) {
            if (heapSize == heapNode.length) {
                heapNode = Arrays.copyOf(heapNode, heapSize * 2);
                heapKey = Arrays.copyOf(heapKey, heapSize * 2);
            }
            int i = heapSize++;
            while (i > 0) {
                int parent = (i - 1) >>> 1;
                if (heapKey[parent] <= key) {
                    break;
                }
                heapNode[i] = heapNode[parent];
                heapKey[i] = heapKey[parent];
                i = parent;
            }
            heapNode[i] = node;
            heapKey[i] = key;
        }

        private void pop() {
            int last = --heapSize;
            if (last == 0) {
                return;
            }
            int node = heapNode[last];
            float key = heapKey[last];
            int i = 0;
            while (true) {
                int child = 2 * i + 1;
                if (child >= last) {
                    break;
                }
                if (child + 1 < last && heapKey[child + 1] < heapKey[child]) {
                    child++;
                }
                if (heapKey[child] >= key) {
                    break;
                }
                heapNode[i] = heapNode[child];
                heapKey[i] = heapKey[child];
                i = child;
            }
            heapNode[i] = node;
            heapKey[i] = key;
        }

        /** Returns the buffers to the pool. Don't use the flood afterwards. */
        void release() {
            reader = null;
            if (POOL.size() < POOL_SIZE) {
                POOL.add(this);
            }
        }
    }
}
