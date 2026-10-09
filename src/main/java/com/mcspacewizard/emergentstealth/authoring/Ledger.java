package com.mcspacewizard.emergentstealth.authoring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoute;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoutes;
import com.mcspacewizard.emergentstealth.registry.ESNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Server side of the Compound Ledger (design doc 32 §3): the item that drives the compound authoring steps the
 * {@code /es compound} commands do. Used on a structure you just placed it starts a draft from it; with a draft
 * open its panel adds or takes out nearby rope zones and baton routes, takes out spawn markers, saves and discards.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class Ledger {
    private Ledger() {}

    /** How far from the author zones and routes are offered, and a new draft may start. */
    public static final double RANGE = 96.0;

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ESNetwork.PROTOCOL_VERSION);
        registrar.playToClient(LedgerPayloads.State.TYPE, LedgerPayloads.State.STREAM_CODEC);
        registrar.playToServer(LedgerPayloads.Action.TYPE, LedgerPayloads.Action.STREAM_CODEC, Ledger::handleAction);
    }

    /** Opens the ledger panel; {@code at} is where it was used (a new draft starts there). */
    public static void open(ServerPlayer player, BlockPos at) {
        ESNetwork.sendIfSupported(player, state(player, at, true));
    }

    /** The author's last placed structure, if {@code at} is inside it (in this level). */
    static StructurePlacement.@Nullable Placed placedAt(ServerLevel level, UUID author, BlockPos at) {
        StructurePlacement.Placed placed = StructurePlacement.last(author);
        if (placed == null || !placed.dimension().equals(level.dimension())) {
            return null;
        }
        StructureTemplate template = level.getServer().getStructureManager().get(placed.template()).orElse(null);
        if (template == null) {
            return null;
        }
        return StructurePlacement.box(template, placed.origin(), placed.rotation(), placed.mirror()).isInside(at) ? placed : null;
    }

    static LedgerPayloads.State state(ServerPlayer player, BlockPos at, boolean open) {
        ServerLevel level = player.level();
        UUID author = player.getUUID();
        CompoundDrafts.Draft draft = CompoundDrafts.get(author);
        if (draft == null || !draft.dimension().equals(level.dimension())) {
            StructurePlacement.Placed placed = draft == null ? placedAt(level, author, at) : null;
            BlockPos origin = draft != null ? draft.origin() : at;
            String id = draft != null ? draft.id().toString() : "";
            return new LedgerPayloads.State(id, origin, draft != null ? draft.compound().structures().size() : 0,
                    placed != null ? placed.template().toString() : "", List.of(), List.of(), List.of(), true, List.of(), open);
        }

        Map<String, LedgerPayloads.Entry> zones = new LinkedHashMap<>();
        Zones worldZones = Zones.get(level);
        for (Zone zone : draft.compound().zones()) {
            zones.put(zone.name(), new LedgerPayloads.Entry(zone.name(), describe(zone), true, worldZones.get(zone.name()).isPresent()));
        }
        worldZones.all().stream()
                .filter(zone -> !zones.containsKey(zone.name()) && near(player, zone.bounds()))
                .sorted(Comparator.comparing(Zone::name))
                .forEach(zone -> zones.put(zone.name(), new LedgerPayloads.Entry(zone.name(), describe(zone), false, true)));

        Map<String, LedgerPayloads.Entry> routes = new LinkedHashMap<>();
        PatrolRoutes worldRoutes = PatrolRoutes.get(level);
        for (PatrolRoute route : draft.compound().routes()) {
            routes.put(route.name(), new LedgerPayloads.Entry(route.name(), String.valueOf(route.waypoints().size()), true,
                    worldRoutes.get(route.name()).isPresent()));
        }
        worldRoutes.all().stream()
                .filter(route -> !routes.containsKey(route.name()) && !route.waypoints().isEmpty()
                        && route.waypoints().getFirst().pos().closerToCenterThan(player.position(), RANGE))
                .sorted(Comparator.comparing(PatrolRoute::name))
                .forEach(route -> routes.put(route.name(), new LedgerPayloads.Entry(route.name(), String.valueOf(route.waypoints().size()), false, true)));

        List<String> spawns = new ArrayList<>();
        for (Compound.Spawn spawn : draft.compound().spawns()) {
            spawns.add(spawn.archetype().getPath() + (spawn.count() > 1 ? " x" + spawn.count() : "") + "  " + spawn.pos().toShortString()
                    + (spawn.schedule().entries().isEmpty() ? "" : "  (" + spawn.schedule().entries().size() + ")"));
        }
        Compound.Lights rule = draft.compound().lights();
        List<LedgerPayloads.Entry> lights = new ArrayList<>();
        for (BlockPos pos : CompoundDrafts.lightsIn(level, draft)) {
            lights.add(new LedgerPayloads.Entry(pos.getX() + " " + pos.getY() + " " + pos.getZ(),
                    BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath(), rule.relights(draft.local(pos)), true));
        }
        return new LedgerPayloads.State(draft.id().toString(), draft.origin(), draft.compound().structures().size(), "",
                cap(zones.values()), cap(routes.values()), cap(spawns), rule.relight() == Compound.Relight.ALL, cap(lights), open);
    }

    private static <T> List<T> cap(java.util.Collection<T> items) {
        return items.stream().limit(MusterPayloads.MAX_LIST).toList();
    }

    private static String describe(Zone zone) {
        return zone.access().getSerializedName() + zone.hours().map(h -> " " + h.from() + "-" + h.to() + "h").orElse("");
    }

    private static boolean near(ServerPlayer player, BoundingBox box) {
        double dx = Math.max(0, Math.max(box.minX() - player.getX(), player.getX() - box.maxX() - 1));
        double dy = Math.max(0, Math.max(box.minY() - player.getY(), player.getY() - box.maxY() - 1));
        double dz = Math.max(0, Math.max(box.minZ() - player.getZ(), player.getZ() - box.maxZ() - 1));
        return dx * dx + dy * dy + dz * dz <= RANGE * RANGE;
    }

    static void handleAction(LedgerPayloads.Action action, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !StructureViewer.mayAuthor(player)) {
            return;
        }
        if (action.kind() == LedgerPayloads.Kind.START && !action.pos().closerToCenterThan(player.position(), RANGE)) {
            player.sendOverlayMessage(Component.translatable("message.emergentstealth.ledger.too_far"));
            return;
        }
        Component result = apply(player.level(), player.getUUID(), action);
        if (action.kind() == LedgerPayloads.Kind.SAVE) {
            player.sendSystemMessage(result); // carries the file's path
        } else {
            player.sendOverlayMessage(result);
        }
        ESNetwork.sendIfSupported(player, state(player, action.pos(), false));
    }

    /** "x y z" as a position, or null. */
    private static @Nullable BlockPos parsePos(String text) {
        String[] parts = text.trim().split(" ");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** What to tell the author after a save: the file, and the structures saved again because they were edited. */
    public static List<Component> savedMessages(CompoundSaver.Saved saved) {
        List<Component> messages = new ArrayList<>();
        messages.add(Component.translatable("commands.emergentstealth.compound.saved", saved.id().toString(), saved.file().toString()));
        if (!saved.resaved().isEmpty()) {
            messages.add(Component.translatable("commands.emergentstealth.compound.resaved", saved.resaved().size(),
                    String.join(", ", saved.resaved().stream().map(Identifier::toString).toList())));
        }
        if (saved.skipped() > 0) {
            messages.add(Component.translatable("commands.emergentstealth.compound.resave_skipped", saved.skipped()));
        }
        return messages;
    }

    /** {@code minecraft:x} or a bare {@code x} means {@code emergentstealth:x}. Null if it isn't a usable id. */
    public static @Nullable Identifier compoundId(String text) {
        Identifier id = Identifier.tryParse(text.trim());
        if (id == null || id.getPath().isEmpty() || id.getPath().contains("..")) {
            return null;
        }
        return id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? EmergentStealth.id(id.getPath()) : id;
    }

    /** Carries out one ledger action for {@code author} in {@code level}; returns what to tell them. Also used by GameTests. */
    public static Component apply(ServerLevel level, UUID author, LedgerPayloads.Action action) {
        CompoundDrafts.Draft draft = CompoundDrafts.get(author);
        if (action.kind() == LedgerPayloads.Kind.START) {
            if (draft != null) {
                return Component.translatable("message.emergentstealth.ledger.already_open", draft.id().toString());
            }
            Identifier id = compoundId(action.text());
            if (id == null) {
                return Component.translatable("message.emergentstealth.ledger.bad_name", action.text());
            }
            StructurePlacement.Placed placed = placedAt(level, author, action.pos());
            if (placed != null) {
                CompoundDrafts.start(author, id, level.dimension(), placed.origin(), Compound.EMPTY);
                CompoundDrafts.onPlaced(level.getServer(), author, placed);
            } else {
                CompoundDrafts.start(author, id, level.dimension(), action.pos(), Compound.EMPTY);
            }
            BlockPos at = CompoundDrafts.get(author).origin();
            return Component.translatable("commands.emergentstealth.compound.started", id.toString(), at.getX(), at.getY(), at.getZ());
        }
        if (draft == null) {
            return Component.translatable("commands.emergentstealth.compound.no_draft");
        }
        if (!draft.dimension().equals(level.dimension())) {
            return Component.translatable("message.emergentstealth.compound.other_dimension", draft.id().toString());
        }
        String name = action.text();
        return switch (action.kind()) {
            case TOGGLE_ZONE -> {
                if (draft.compound().zones().stream().anyMatch(z -> z.name().equals(name))) {
                    CompoundDrafts.removeZone(author, draft, name);
                    yield Component.translatable("message.emergentstealth.ledger.zone_removed", name);
                }
                Optional<Zone> zone = Zones.get(level).get(name);
                if (zone.isEmpty()) {
                    yield Component.translatable("message.emergentstealth.zone.unknown", name);
                }
                CompoundDrafts.addZone(author, draft, zone.get());
                yield Component.translatable("commands.emergentstealth.compound.added_zone", name, zone.get().access().getSerializedName());
            }
            case TOGGLE_ROUTE -> {
                if (draft.compound().routes().stream().anyMatch(r -> r.name().equals(name))) {
                    CompoundDrafts.removeRoute(author, draft, name);
                    yield Component.translatable("message.emergentstealth.ledger.route_removed", name);
                }
                Optional<PatrolRoute> route = PatrolRoutes.get(level).get(name);
                if (route.isEmpty()) {
                    yield Component.translatable("commands.emergentstealth.compound.unknown_route", name);
                }
                CompoundDrafts.addRoute(author, draft, route.get());
                yield Component.translatable("commands.emergentstealth.compound.added_route", name, route.get().waypoints().size());
            }
            case REMOVE_SPAWN -> {
                if (action.index() < 0 || action.index() >= draft.compound().spawns().size()) {
                    yield Component.translatable("message.emergentstealth.ledger.no_spawn");
                }
                CompoundDrafts.removeSpawn(author, draft, action.index());
                yield Component.translatable("message.emergentstealth.ledger.spawn_removed");
            }
            case TOGGLE_LIGHT -> {
                BlockPos pos = parsePos(name);
                if (pos == null) {
                    yield Component.translatable("message.emergentstealth.ledger.no_light");
                }
                Compound.Lights lights = draft.compound().lights().toggled(draft.local(pos));
                CompoundDrafts.setLights(author, draft, lights);
                yield Component.translatable(lights.relights(draft.local(pos)) ? "commands.emergentstealth.compound.light_relit"
                        : "commands.emergentstealth.compound.light_dark", pos.getX(), pos.getY(), pos.getZ());
            }
            case LIGHT_RULE -> {
                Compound.Lights lights = draft.compound().lights().flipped();
                CompoundDrafts.setLights(author, draft, lights);
                yield Component.translatable("commands.emergentstealth.compound.lights_rule", lights.relight().getSerializedName());
            }
            case SAVE -> {
                try {
                    MutableComponent text = Component.empty();
                    List<Component> lines = savedMessages(CompoundSaver.save(level.getServer(), author, draft));
                    for (int i = 0; i < lines.size(); i++) {
                        text.append(i == 0 ? Component.empty() : Component.literal("\n")).append(lines.get(i));
                    }
                    yield text;
                } catch (IOException e) {
                    yield Component.translatable("commands.emergentstealth.compound.save_failed", String.valueOf(e.getMessage()));
                }
            }
            case DISCARD -> {
                CompoundDrafts.close(author);
                yield Component.translatable("commands.emergentstealth.compound.cancelled", draft.id().toString());
            }
            case START -> throw new IllegalStateException();
        };
    }
}
