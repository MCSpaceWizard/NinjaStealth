package com.mcspacewizard.emergentstealth.authoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoute;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoutes;
import com.mcspacewizard.emergentstealth.ai.routine.Schedule;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESNetwork;
import com.mcspacewizard.emergentstealth.registry.ESRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Server side of the NPC spawner, the Muster Roll (design doc 32 §4). The panel picks an archetype, a behaviour
 * tree, a schedule (each window a post, a route or wandering) and a count; the server checks the request and
 * spawns the NPCs on the clicked block. When the author has a compound draft open in that dimension, the spawn
 * also joins it as a marker (and the routes its schedule walks are copied in), so the compound brings these
 * guards along wherever it is placed.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class MusterRoll {
    private MusterRoll() {}

    /** How far from the author a spawn (or a post in its schedule) may be. */
    public static final double REACH = 32.0;
    /** Most NPCs at one spawn (the compound format's limit). */
    public static final int MAX_COUNT = 16;
    /** Most world routes offered in the panel, nearest first. */
    private static final int MAX_ROUTES = 64;

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ESNetwork.PROTOCOL_VERSION);
        registrar.playToClient(MusterPayloads.Open.TYPE, MusterPayloads.Open.STREAM_CODEC);
        registrar.playToServer(MusterPayloads.Request.TYPE, MusterPayloads.Request.STREAM_CODEC, MusterRoll::handleRequest);
    }

    /** The facing the panel suggests: where the author looks, to the nearest 45°, in 0..360. */
    public static float snapFacing(float yaw) {
        return Mth.positiveModulo(Math.round(yaw / 45.0F) * 45.0F, 360.0F);
    }

    /** Opens the panel for a spawn on {@code pos}. */
    public static void open(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        List<Identifier> archetypes = level.registryAccess().lookupOrThrow(ESRegistries.ARCHETYPE).keySet().stream()
                .sorted(Comparator.comparing(Identifier::toString)).limit(MusterPayloads.MAX_LIST).toList();
        List<Identifier> behaviours = level.registryAccess().lookupOrThrow(ESRegistries.BEHAVIOUR).keySet().stream()
                .sorted(Comparator.comparing(Identifier::toString)).limit(MusterPayloads.MAX_LIST - 1).toList();
        CompoundDrafts.Draft draft = draftIn(level, player.getUUID());
        ESNetwork.sendIfSupported(player, new MusterPayloads.Open(pos, snapFacing(player.getYRot()), archetypes, behaviours,
                routeChoices(level, draft, pos), draft == null ? "" : draft.id().toString()));
    }

    /** The draft's routes first, then the world's, nearest to {@code pos} first. */
    static List<String> routeChoices(ServerLevel level, CompoundDrafts.@Nullable Draft draft, BlockPos pos) {
        Set<String> names = new LinkedHashSet<>();
        if (draft != null) {
            draft.compound().routes().forEach(route -> names.add(route.name()));
        }
        PatrolRoutes.get(level).all().stream()
                .filter(route -> !route.waypoints().isEmpty())
                .sorted(Comparator.comparingDouble(route -> route.waypoints().getFirst().pos().distSqr(pos)))
                .limit(MAX_ROUTES)
                .forEach(route -> names.add(route.name()));
        return names.stream().filter(name -> name.length() <= MusterPayloads.MAX_NAME).limit(MusterPayloads.MAX_LIST).toList();
    }

    private static CompoundDrafts.@Nullable Draft draftIn(ServerLevel level, UUID author) {
        CompoundDrafts.Draft draft = CompoundDrafts.get(author);
        return draft != null && draft.dimension().equals(level.dimension()) ? draft : null;
    }

    static void handleRequest(MusterPayloads.Request request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !StructureViewer.mayAuthor(player)) {
            return;
        }
        if (player.distanceToSqr(request.pos().getCenter()) > REACH * REACH) {
            player.sendOverlayMessage(Component.translatable("message.emergentstealth.muster.too_far"));
            return;
        }
        player.sendOverlayMessage(muster(player.level(), player.getUUID(), request).message());
    }

    /** What a muster did: the NPCs it spawned (none if refused) and what to tell the author. */
    public record Result(List<StealthNpc> npcs, boolean recorded, Component message) {
        static Result refused(String key, Object... args) {
            return new Result(List.of(), false, Component.translatable(key, args));
        }
    }

    /**
     * Checks and carries out a request: spawns the NPCs and, with a draft open here, records them as one spawn
     * marker. Permission and reach are the caller's job. Also used by GameTests.
     */
    public static Result muster(ServerLevel level, UUID author, MusterPayloads.Request request) {
        BlockPos pos = request.pos();
        if (!level.isLoaded(pos)) {
            return Result.refused("message.emergentstealth.muster.too_far");
        }
        if (!level.registryAccess().lookupOrThrow(ESRegistries.ARCHETYPE).containsKey(request.archetype())) {
            return Result.refused("message.emergentstealth.muster.unknown_archetype", request.archetype().toString());
        }
        if (request.behaviour().isPresent() && !level.registryAccess().lookupOrThrow(ESRegistries.BEHAVIOUR).containsKey(request.behaviour().get())) {
            return Result.refused("message.emergentstealth.muster.unknown_behaviour", request.behaviour().get().toString());
        }
        if (request.count() < 1 || request.count() > MAX_COUNT) {
            return Result.refused("message.emergentstealth.muster.bad_count", MAX_COUNT);
        }
        Schedule schedule = request.schedule();
        if (schedule.entries().size() > Schedule.MAX_ENTRIES) {
            return Result.refused("message.emergentstealth.muster.too_many_windows", Schedule.MAX_ENTRIES);
        }
        CompoundDrafts.Draft draft = draftIn(level, author);
        PatrolRoutes worldRoutes = PatrolRoutes.get(level);
        for (Schedule.Entry entry : schedule.entries()) {
            switch (entry.activity()) {
                case Schedule.Post post -> {
                    if (post.pos().distSqr(pos) > REACH * REACH) {
                        return Result.refused("message.emergentstealth.muster.post_too_far");
                    }
                }
                case Schedule.Route route -> {
                    boolean inDraft = draft != null && draft.compound().routes().stream().anyMatch(r -> r.name().equals(route.route()));
                    if (!inDraft && worldRoutes.get(route.route()).isEmpty()) {
                        return Result.refused("message.emergentstealth.muster.unknown_route", route.route());
                    }
                }
                case Schedule.Wander wander -> {
                    // The codec already bounds the radius.
                }
            }
        }

        float facing = Mth.positiveModulo(request.facing(), 360.0F);
        List<StealthNpc> npcs = new ArrayList<>();
        for (int i = 0; i < request.count(); i++) {
            StealthNpc npc = CompoundPlacer.spawn(level, request.archetype(), pos, facing, EntitySpawnReason.SPAWN_ITEM_USE);
            if (npc == null) {
                continue;
            }
            request.behaviour().ifPresent(npc::setBehaviourOverride);
            npc.setSchedule(schedule);
            npc.setHome(pos, facing);
            npcs.add(npc);
        }
        if (npcs.isEmpty()) {
            return Result.refused("message.emergentstealth.muster.failed");
        }
        String name = request.archetype().getPath();
        if (draft == null) {
            return new Result(npcs, false, Component.translatable("message.emergentstealth.muster.spawned", npcs.size(), name));
        }

        // Routes the schedule walks come along, so the compound is complete.
        for (Schedule.Entry entry : schedule.entries()) {
            if (entry.activity() instanceof Schedule.Route route
                    && draft.compound().routes().stream().noneMatch(r -> r.name().equals(route.route()))) {
                PatrolRoute world = worldRoutes.get(route.route()).orElse(null);
                if (world != null) {
                    draft = CompoundDrafts.addRoute(author, draft, world);
                }
            }
        }
        Compound.Spawn spawn = new Compound.Spawn(request.archetype(), draft.local(pos), facing, request.behaviour(),
                CompoundDrafts.localSchedule(draft, schedule), npcs.size());
        draft = CompoundDrafts.addSpawn(author, draft, spawn, npcs.stream().map(StealthNpc::getUUID).toList());
        return new Result(npcs, true, Component.translatable("message.emergentstealth.muster.recorded", npcs.size(), name,
                draft.id().toString(), draft.compound().spawns().size()));
    }
}
