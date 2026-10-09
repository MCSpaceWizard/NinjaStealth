package com.mcspacewizard.emergentstealth.client.authoring;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.authoring.StructurePayloads;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The structure viewer's ghost (design doc 32 §1): a translucent copy of a structure that follows the crosshair,
 * starting at the block you look at and running away from you. It draws only the faces you could see (exposed
 * faces), tinted by each block's map colour, and uses the template's own transform with the pivot at the origin,
 * so it shows exactly what the server will place.
 */
public final class GhostPreview implements DebugRenderer.SimpleDebugRenderer {
    /** More faces than this and only the upward ones are drawn (roofs and floors still give the shape). */
    private static final int MAX_FACES = 30_000;
    private static final double REACH = 160.0;
    private static final int ALPHA = 0x66;
    private static final int OUTLINE = 0xFFE8DCC0;

    private static ClientStructures.@Nullable Preview active;
    private static Rotation rotation = Rotation.NONE;
    private static Mirror mirror = Mirror.NONE;
    private static int raise;
    private static @Nullable Faces faces;

    /** Exposed faces after the transform: 4 corners each (12 floats), relative to the origin, and their colours. */
    private record Faces(Rotation rotation, Mirror mirror, float[] corners, int[] colors, int count, BlockPos min, BlockPos max) {}

    public static void start(ClientStructures.Preview preview) {
        active = preview;
        rotation = Rotation.NONE;
        mirror = Mirror.NONE;
        raise = 0;
        faces = null;
    }

    public static void stop() {
        active = null;
        faces = null;
    }

    public static boolean isActive() {
        return active != null;
    }

    public static ClientStructures.@Nullable Preview active() {
        return active;
    }

    public static Rotation rotation() {
        return rotation;
    }

    public static Mirror mirror() {
        return mirror;
    }

    public static int raise() {
        return raise;
    }

    public static void rotate(int steps) {
        Rotation[] values = Rotation.values();
        rotation = values[Math.floorMod(rotation.ordinal() + steps, values.length)];
    }

    public static void toggleMirror() {
        mirror = mirror == Mirror.NONE ? Mirror.FRONT_BACK : Mirror.NONE;
    }

    public static void raise(int blocks) {
        raise += blocks;
    }

    /** Sends the placement to the server and ends the preview. */
    public static void place() {
        BlockPos origin = origin();
        if (active != null && origin != null) {
            ClientPacketDistributor.sendToServer(new StructurePayloads.Place(active.id(), origin, rotation, mirror));
        }
        stop();
    }

    /** The block the structure's footprint centres on: the empty block in front of what you look at. */
    private static @Nullable BlockPos target() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }
        HitResult hit = minecraft.player.pick(REACH, 1.0F, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return block.getBlockPos().relative(block.getDirection());
    }

    /**
     * Where the template's origin goes this frame, or null if you aren't looking at a block. The footprint starts
     * at the block you look at and runs away from you (centred across your view), so you stand outside the ghost.
     */
    public static @Nullable BlockPos origin() {
        BlockPos target = target();
        Faces f = faces();
        Minecraft minecraft = Minecraft.getInstance();
        if (target == null || f == null || minecraft.player == null) {
            return null;
        }
        Direction facing = minecraft.player.getDirection();
        int dx = switch (facing) {
            case EAST -> -f.min().getX();
            case WEST -> -f.max().getX();
            default -> -(f.min().getX() + f.max().getX()) / 2;
        };
        int dz = switch (facing) {
            case SOUTH -> -f.min().getZ();
            case NORTH -> -f.max().getZ();
            default -> -(f.min().getZ() + f.max().getZ()) / 2;
        };
        return target.offset(dx, -f.min().getY() + raise, dz);
    }

    private static @Nullable Faces faces() {
        ClientStructures.Preview preview = active;
        if (preview == null) {
            return null;
        }
        Faces f = faces;
        if (f == null || f.rotation() != rotation || f.mirror() != mirror) {
            f = build(preview, rotation, mirror);
            faces = f;
        }
        return f;
    }

    private static Faces build(ClientStructures.Preview preview, Rotation rotation, Mirror mirror) {
        int n = preview.solid();
        BlockPos[] moved = new BlockPos[n];
        LongOpenHashSet solid = new LongOpenHashSet(n * 2);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            BlockPos p = StructureTemplate.transform(preview.positions()[i], mirror, rotation, BlockPos.ZERO);
            moved[i] = p;
            solid.add(p.asLong());
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        if (n == 0) {
            minX = minY = minZ = maxX = maxY = maxZ = 0;
        }
        int exposed = 0;
        int exposedUp = 0;
        for (int i = 0; i < n; i++) {
            for (Direction d : Direction.values()) {
                if (!solid.contains(moved[i].relative(d).asLong())) {
                    exposed++;
                    if (d == Direction.UP) {
                        exposedUp++;
                    }
                }
            }
        }
        boolean upOnly = exposed > MAX_FACES;
        int wanted = upOnly ? exposedUp : exposed;
        int stride = Math.max(1, (wanted + MAX_FACES - 1) / MAX_FACES);
        float[] corners = new float[Math.min(wanted, MAX_FACES) * 12];
        int[] colors = new int[Math.min(wanted, MAX_FACES)];
        int count = 0;
        int seen = 0;
        for (int i = 0; i < n && count < colors.length; i++) {
            BlockState state = preview.states()[i];
            int base = color(state);
            for (Direction d : Direction.values()) {
                if ((upOnly && d != Direction.UP) || solid.contains(moved[i].relative(d).asLong())) {
                    continue;
                }
                if (seen++ % stride != 0 || count >= colors.length) {
                    continue;
                }
                quad(moved[i], d, corners, count * 12);
                colors[count++] = shade(base, d);
            }
        }
        return new Faces(rotation, mirror, corners, colors, count, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    private static int color(BlockState state) {
        MapColor map = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        int rgb = map == MapColor.NONE ? 0xB0B0B0 : map.col;
        return (ALPHA << 24) | (rgb & 0xFFFFFF);
    }

    private static int shade(int argb, Direction face) {
        float f = switch (face) {
            case UP -> 1.0F;
            case DOWN -> 0.5F;
            case NORTH, SOUTH -> 0.8F;
            case EAST, WEST -> 0.65F;
        };
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int g = (int) (((argb >> 8) & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** The 4 corners of a block's face, counter-clockwise seen from outside. */
    private static void quad(BlockPos p, Direction d, float[] out, int at) {
        float x0 = p.getX();
        float y0 = p.getY();
        float z0 = p.getZ();
        float x1 = x0 + 1;
        float y1 = y0 + 1;
        float z1 = z0 + 1;
        float[] q = switch (d) {
            case UP -> new float[] {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0};
            case DOWN -> new float[] {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1};
            case NORTH -> new float[] {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0};
            case SOUTH -> new float[] {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1};
            case WEST -> new float[] {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0};
            case EAST -> new float[] {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1};
        };
        System.arraycopy(q, 0, out, at, 12);
    }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess access, Frustum frustum, float partialTicks) {
        Faces f = faces();
        BlockPos origin = origin();
        if (f == null || origin == null) {
            return;
        }
        double ox = origin.getX();
        double oy = origin.getY();
        double oz = origin.getZ();
        AABB box = new AABB(ox + f.min().getX(), oy + f.min().getY(), oz + f.min().getZ(),
                ox + f.max().getX() + 1, oy + f.max().getY() + 1, oz + f.max().getZ() + 1);
        // Filled gizmos cull back faces: from inside the ghost, draw each face both ways round.
        boolean inside = box.contains(camX, camY, camZ);
        float[] c = f.corners();
        for (int i = 0; i < f.count(); i++) {
            int k = i * 12;
            Vec3 a = new Vec3(ox + c[k], oy + c[k + 1], oz + c[k + 2]);
            Vec3 b = new Vec3(ox + c[k + 3], oy + c[k + 4], oz + c[k + 5]);
            Vec3 cc = new Vec3(ox + c[k + 6], oy + c[k + 7], oz + c[k + 8]);
            Vec3 d = new Vec3(ox + c[k + 9], oy + c[k + 10], oz + c[k + 11]);
            GizmoStyle style = GizmoStyle.fill(f.colors()[i]);
            Gizmos.rect(a, b, cc, d, style);
            if (inside) {
                Gizmos.rect(d, cc, b, a, style);
            }
        }
        Gizmos.cuboid(box, GizmoStyle.stroke(OUTLINE, 2.0F));
    }
}
