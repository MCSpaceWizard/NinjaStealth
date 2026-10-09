package com.mcspacewizard.emergentstealth.authoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.registry.ESDataComponents;
import com.mcspacewizard.emergentstealth.registry.ESItems;
import com.mcspacewizard.emergentstealth.registry.ESNetwork;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Server side of the Surveyor's Rope (design doc 32 §2): shows nearby zones to game masters holding it, and
 * applies what the zone panel asks for. Making boxes is the item's job ({@link com.mcspacewizard.emergentstealth.item.SurveyorsRopeItem}).
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class ZoneTool {
    private ZoneTool() {}

    /** Zone names the tools accept: lower case, digits and {@code _ . -}. */
    public static final Pattern NAME = Pattern.compile("[a-z0-9_.-]{1,64}");

    private static final int SYNC_INTERVAL = 10;
    private static final double RANGE = 96.0;
    private static final Map<ServerPlayer, Boolean> WAS_HOLDING = new WeakHashMap<>();

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ESNetwork.PROTOCOL_VERSION);
        registrar.playToClient(ZonePayloads.Sync.TYPE, ZonePayloads.Sync.STREAM_CODEC);
        registrar.playToClient(ZonePayloads.OpenEditor.TYPE, ZonePayloads.OpenEditor.STREAM_CODEC);
        registrar.playToServer(ZonePayloads.Edit.TYPE, ZonePayloads.Edit.STREAM_CODEC, ZoneTool::handleEdit);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % SYNC_INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            ItemStack rope = heldRope(player);
            boolean holding = rope != null && player.canUseGameMasterBlocks();
            if (holding) {
                ESNetwork.sendIfSupported(player, sync(level, player, rope));
            } else if (WAS_HOLDING.getOrDefault(player, false)) {
                ESNetwork.sendIfSupported(player, ZonePayloads.Sync.EMPTY);
            }
            WAS_HOLDING.put(player, holding);
        }
    }

    public static @Nullable ItemStack heldRope(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(ESItems.SURVEYORS_ROPE.get())) {
                return stack;
            }
        }
        return null;
    }

    private static ZonePayloads.Sync sync(ServerLevel level, ServerPlayer player, ItemStack rope) {
        List<Zone> near = new ArrayList<>();
        for (Zone zone : Zones.get(level).all()) {
            BoundingBox bounds = zone.bounds();
            double dx = Math.max(0, Math.max(bounds.minX() - player.getX(), player.getX() - bounds.maxX() - 1));
            double dy = Math.max(0, Math.max(bounds.minY() - player.getY(), player.getY() - bounds.maxY() - 1));
            double dz = Math.max(0, Math.max(bounds.minZ() - player.getZ(), player.getZ() - bounds.maxZ() - 1));
            if (dx * dx + dy * dy + dz * dz <= RANGE * RANGE && zone.boxes().size() <= Zone.MAX_BOXES) {
                near.add(zone);
            }
        }
        return new ZonePayloads.Sync(rope.getOrDefault(ESDataComponents.ROPE_ZONE.get(), ""),
                Optional.ofNullable(rope.get(ESDataComponents.ROPE_CORNER.get())), near);
    }

    static void handleEdit(ZonePayloads.Edit edit, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && StructureViewer.mayAuthor(player)) {
            Component result = apply(player.level(), edit);
            if (result != null) {
                player.sendOverlayMessage(result);
            }
            ItemStack rope = heldRope(player);
            if (rope != null && edit.original().equals(rope.get(ESDataComponents.ROPE_ZONE.get()))) {
                // Follow a rename; forget a deleted zone.
                Zones zones = Zones.get(player.level());
                String keep = zones.get(edit.original()).isPresent() ? edit.original()
                        : edit.action() == ZonePayloads.Action.SAVE && zones.get(edit.name()).isPresent() ? edit.name() : null;
                if (keep != null) {
                    rope.set(ESDataComponents.ROPE_ZONE.get(), keep);
                } else {
                    rope.remove(ESDataComponents.ROPE_ZONE.get());
                }
            }
        }
    }

    /** Applies a panel edit to the level's zones; returns what to tell the author. Also used by GameTests. */
    public static Component apply(ServerLevel level, ZonePayloads.Edit edit) {
        Zones zones = Zones.get(level);
        Zone zone = zones.get(edit.original()).orElse(null);
        if (zone == null) {
            return Component.translatable("message.emergentstealth.zone.unknown", edit.original());
        }
        switch (edit.action()) {
            case DELETE -> {
                zones.remove(zone.name());
                return Component.translatable("message.emergentstealth.zone.deleted", zone.name());
            }
            case REMOVE_LAST_BOX -> {
                if (zone.boxes().size() <= 1) {
                    return Component.translatable("message.emergentstealth.zone.last_box");
                }
                zones.put(new Zone(zone.name(), zone.access(), List.copyOf(zone.boxes().subList(0, zone.boxes().size() - 1)), zone.hours()));
                return Component.translatable("message.emergentstealth.zone.box_removed", zone.name(), zone.boxes().size() - 1);
            }
            default -> {
                String name = edit.name();
                boolean renamed = !name.equals(zone.name());
                if (renamed) {
                    // A placed compound's zones keep their scoped names, so removing the copy finds them.
                    if (zone.name().contains("#")) {
                        return Component.translatable("message.emergentstealth.zone.placed_name", zone.name());
                    }
                    if (!NAME.matcher(name).matches()) {
                        return Component.translatable("message.emergentstealth.zone.bad_name", name);
                    }
                    if (zones.get(name).isPresent()) {
                        return Component.translatable("message.emergentstealth.zone.name_taken", name);
                    }
                }
                Optional<Zone.Hours> hours = edit.hours().filter(h -> h.from() >= 0 && h.from() <= 24 && h.to() >= 0 && h.to() <= 24);
                if (renamed) {
                    zones.remove(zone.name());
                }
                zones.put(new Zone(name, edit.access(), zone.boxes(), hours));
                return Component.translatable("message.emergentstealth.zone.saved", name, edit.access().getSerializedName());
            }
        }
    }
}
