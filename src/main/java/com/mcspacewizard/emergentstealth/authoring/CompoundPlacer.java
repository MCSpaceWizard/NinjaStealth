package com.mcspacewizard.emergentstealth.authoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoute;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoutes;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Places a compound (design doc 32 §3): its templates, each turned by its own transform and then the compound's,
 * then its routes and zones (renamed per copy, {@code <compound>#<n>/<name>}) and its NPCs with their schedules
 * moved to match. The terrain round it is fitted first and last ({@link TerrainFit}). The whole copy, terrain
 * included, is one undoable placement, and is recorded in {@link PlacedCompounds}.
 */
public final class CompoundPlacer {
    private CompoundPlacer() {}

    /** A placed copy, or the templates that couldn't be found (then nothing was placed). */
    public record Result(PlacedCompounds.@Nullable Copy copy, BoundingBox box, List<Identifier> missing) {}

    private record Piece(StructureTemplate template, BlockPos origin, Transform transform) {}

    /** Places with the compound's own terrain setting. */
    public static Result place(ServerLevel level, UUID author, Identifier id, Compound compound, BlockPos origin, Transform transform) {
        return place(level, author, id, compound, origin, transform, compound.terrain());
    }

    /** Places, fitting the terrain round it as {@code terrain} says (doc 33 §4); the ground line is below {@code origin}. */
    public static Result place(ServerLevel level, UUID author, Identifier id, Compound compound, BlockPos origin, Transform transform,
                               Compound.Terrain terrain) {
        List<Piece> pieces = new ArrayList<>();
        List<Identifier> missing = new ArrayList<>();
        List<BoundingBox> boxes = new ArrayList<>();
        for (Compound.Module module : compound.structures()) {
            StructureTemplate template = level.getServer().getStructureManager().get(module.template()).orElse(null);
            if (template == null) {
                missing.add(module.template());
                continue;
            }
            Transform turned = module.transform().then(transform);
            BlockPos at = transform.apply(module.offset()).offset(origin);
            pieces.add(new Piece(template, at, turned));
            boxes.add(StructurePlacement.box(template, at, turned.rotation(), turned.mirror()));
        }
        List<BoundingBox> moduleBoxes = List.copyOf(boxes);
        for (Compound.Spawn spawn : compound.spawns()) {
            boxes.add(new BoundingBox(transform.apply(spawn.pos()).offset(origin)));
        }
        BoundingBox box = BoundingBox.encapsulatingBoxes(boxes).orElseGet(() -> new BoundingBox(origin));
        if (!missing.isEmpty()) {
            return new Result(null, box, missing);
        }
        TerrainFit.Plan terrainPlan = TerrainFit.plan(level, terrain, moduleBoxes, origin.getY() - 1);
        BoundingBox touched = terrainPlan == null ? box : BoundingBox.encapsulating(box, terrainPlan.area());

        PlacedCompounds placed = PlacedCompounds.get(level);
        int copyId = placed.nextId();
        Map<String, String> routeNames = new HashMap<>();
        compound.routes().forEach(route -> routeNames.put(route.name(), PlacedCompounds.Copy.scoped(id, copyId, route.name())));
        List<String> zoneNames = compound.zones().stream().map(zone -> PlacedCompounds.Copy.scoped(id, copyId, zone.name())).toList();
        List<UUID> npcs = new ArrayList<>();

        StructurePlacement.tracked(level, author, touched, () -> {
            if (terrainPlan != null) {
                TerrainFit.before(level, terrainPlan);
            }
            for (Piece piece : pieces) {
                StructurePlacement.placeTemplate(level, piece.template(), piece.origin(), piece.transform().rotation(), piece.transform().mirror());
            }
            if (terrainPlan != null) {
                TerrainFit.after(level, terrainPlan);
            }
            PatrolRoutes routes = PatrolRoutes.get(level);
            for (PatrolRoute route : compound.routes()) {
                routes.put(Compound.moveRoute(route, routeNames.get(route.name()), transform, origin));
            }
            Zones zones = Zones.get(level);
            for (int i = 0; i < compound.zones().size(); i++) {
                zones.put(compound.zones().get(i).placed(zoneNames.get(i), transform, origin));
            }
            for (Compound.Spawn spawn : compound.spawns()) {
                for (int n = 0; n < spawn.count(); n++) {
                    StealthNpc npc = spawnNpc(level, spawn, routeNames, transform, origin);
                    if (npc != null) {
                        npcs.add(npc.getUUID());
                    }
                }
            }
        }, () -> remove(level, copyId));

        PlacedCompounds.Copy copy = new PlacedCompounds.Copy(copyId, id, origin, transform.rotation(), transform.mirror(),
                List.copyOf(routeNames.values()), zoneNames, List.copyOf(npcs));
        placed.add(copy);
        return new Result(copy, box, List.of());
    }

    /** Forgets a copy's routes, zones and record (its blocks and NPCs stay). Returns false if there's no such copy. */
    public static boolean remove(ServerLevel level, int copyId) {
        PlacedCompounds placed = PlacedCompounds.get(level);
        PlacedCompounds.Copy copy = placed.get(copyId).orElse(null);
        if (copy == null) {
            return false;
        }
        PatrolRoutes routes = PatrolRoutes.get(level);
        copy.routes().forEach(routes::remove);
        Zones zones = Zones.get(level);
        copy.zones().forEach(zones::remove);
        return placed.remove(copyId);
    }

    private static @Nullable StealthNpc spawnNpc(ServerLevel level, Compound.Spawn spawn, Map<String, String> routeNames, Transform transform, BlockPos origin) {
        BlockPos pos = transform.apply(spawn.pos()).offset(origin);
        float yaw = transform.yaw(spawn.facing());
        StealthNpc npc = spawn(level, spawn.archetype(), pos, yaw, EntitySpawnReason.STRUCTURE);
        if (npc == null) {
            return null;
        }
        spawn.behaviour().ifPresent(npc::setBehaviourOverride);
        npc.setSchedule(Compound.moveSchedule(spawn.schedule(), name -> routeNames.getOrDefault(name, name), transform, origin));
        npc.setHome(pos, yaw);
        return npc;
    }

    /** Spawns a stealth NPC of an archetype standing on {@code pos}, facing {@code yaw}. */
    public static @Nullable StealthNpc spawn(ServerLevel level, Identifier archetype, BlockPos pos, float yaw, EntitySpawnReason reason) {
        StealthNpc npc = ESEntities.STEALTH_NPC.get().create(level, reason);
        if (npc == null) {
            return null;
        }
        npc.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setArchetypeId(archetype);
        EventHooks.finalizeMobSpawn(npc, level, level.getCurrentDifficultyAt(pos), reason, null);
        level.addFreshEntityWithPassengers(npc);
        return npc;
    }
}
