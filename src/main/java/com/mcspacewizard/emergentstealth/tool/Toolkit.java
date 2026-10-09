package com.mcspacewizard.emergentstealth.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.network.SelectToolPayload;
import com.mcspacewizard.emergentstealth.network.SmokeDebugPayload;
import com.mcspacewizard.emergentstealth.network.UseToolPayload;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.registry.ESDebugSubscriptions;
import com.mcspacewizard.emergentstealth.registry.ESNetwork;
import com.mcspacewizard.emergentstealth.stealth.SmokeVolumes;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Server side of the tool wheel and quick use (design doc 21 §1): validates the client's intents, keeps the
 * {@link ESAttachments#ACTIVE_TOOL active tool}, and uses it from the {@link Toolbelt} without touching the held item.
 * Also prunes smoke volumes and sends them to the AI debug view. Registers its own payloads.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class Toolkit {
    private Toolkit() {}

    /** Every stealth tool the wheel lists. Part B adds ranged tools through {@code #emergentstealth:tools_ranged}. */
    public static final TagKey<Item> TOOLS = TagKey.create(Registries.ITEM, EmergentStealth.id("tools"));

    private static final int DEBUG_INTERVAL = 10;
    private static final double DEBUG_RANGE = 96.0;
    /** Levels that sent smoke to the debug view last time (so watchers get one final empty update). */
    private static final Set<ServerLevel> DEBUG_HAD_SMOKE = Collections.newSetFromMap(new WeakHashMap<>());

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ESNetwork.PROTOCOL_VERSION);
        registrar.playToServer(SelectToolPayload.TYPE, SelectToolPayload.STREAM_CODEC, Toolkit::handleSelect);
        registrar.playToServer(UseToolPayload.TYPE, UseToolPayload.STREAM_CODEC, Toolkit::handleUse);
        registrar.playToClient(SmokeDebugPayload.TYPE, SmokeDebugPayload.STREAM_CODEC);
    }

    static void handleSelect(SelectToolPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            select(player, payload.tool());
        }
    }

    static void handleUse(UseToolPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            quickUse(player, payload.charge());
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Public API

    public static ActiveTool activeTool(ServerPlayer player) {
        return player.getData(ESAttachments.ACTIVE_TOOL);
    }

    /**
     * Makes {@code tool} the player's active tool if it's a stealth tool in their {@link Toolbelt}.
     * {@link ActiveTool#NONE} clears it.
     *
     * @return whether the selection was accepted
     */
    public static boolean select(ServerPlayer player, ActiveTool tool) {
        if (tool.isNone()) {
            player.setData(ESAttachments.ACTIVE_TOOL, ActiveTool.NONE);
            return true;
        }
        ItemStack belt = Toolbelt.belt(player.getInventory());
        if (!tool.item().builtInRegistryHolder().is(TOOLS) || Toolbelt.slotOf(belt, tool.item()) < 0) {
            return false;
        }
        player.setData(ESAttachments.ACTIVE_TOOL, tool);
        return true;
    }

    /**
     * Quick use (V): uses one of the active tool from the toolbelt, without changing the held item. Throwables are
     * thrown ({@code charge} 0 = lob, 1 = long throw). A tool that isn't a {@link StealthTool} (blowgun, spyglass,
     * lockpick, caltrops) is taken out of the belt into the main hand instead.
     *
     * @return whether anything happened
     */
    public static boolean quickUse(ServerPlayer player, float charge) {
        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }
        ActiveTool tool = activeTool(player);
        if (tool.isNone()) {
            player.sendOverlayMessage(Component.translatable("message.emergentstealth.tool.none"));
            return false;
        }
        Inventory inventory = player.getInventory();
        ItemStack belt = Toolbelt.belt(inventory);
        if (belt.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.emergentstealth.tool.no_belt"));
            return false;
        }
        int slot = Toolbelt.slotOf(belt, tool.item());
        if (slot < 0) {
            player.sendOverlayMessage(Component.translatable("message.emergentstealth.tool.out",
                    tool.item().getName(new ItemStack(tool.item()))));
            return false;
        }
        float t = Float.isNaN(charge) ? 0.0F : Mth.clamp(charge, 0.0F, 1.0F);
        NonNullList<ItemStack> contents = Toolbelt.contents(belt);
        if (tool.item() instanceof StealthTool stealthTool) {
            boolean used = stealthTool.quickUse(player, contents.get(slot), t);
            if (used) {
                Toolbelt.store(belt, contents);
                inventory.setChanged();
            }
            return used;
        }
        return equip(player, belt, contents, slot);
    }

    /** How many of {@code item} the player's toolbelt holds. */
    public static int count(Inventory inventory, Item item) {
        return Toolbelt.count(Toolbelt.belt(inventory), item);
    }

    /**
     * Hand tools are used from the hand: take the tool out of the belt into the main hand. The held item goes into
     * the belt slot if it fits there, else into a free inventory slot; with neither, nothing happens.
     */
    private static boolean equip(ServerPlayer player, ItemStack belt, NonNullList<ItemStack> contents, int slot) {
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        ItemStack held = inventory.getItem(selected);
        ItemStack tool = contents.get(slot);
        int free = -1;
        if (held.isEmpty() || Toolbelt.accepts(held)) {
            contents.set(slot, held);
        } else {
            free = inventory.getFreeSlot();
            if (free < 0) {
                player.sendOverlayMessage(Component.translatable("message.emergentstealth.tool.hands_full"));
                return false;
            }
            contents.set(slot, ItemStack.EMPTY);
        }
        // Store first: the held item may be the belt itself, and moving it must carry the new contents.
        Toolbelt.store(belt, contents);
        if (free >= 0) {
            inventory.setItem(free, held);
        }
        inventory.setItem(selected, tool);
        inventory.setChanged();
        return true;
    }

    // ------------------------------------------------------------------------------------------------
    // Smoke upkeep and debug view

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        SmokeVolumes.prune(level);
        if (level.getGameTime() % DEBUG_INTERVAL == 0) {
            syncDebug(level);
        }
    }

    @SubscribeEvent
    static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            SmokeVolumes.clear(level);
        }
    }

    private static void syncDebug(ServerLevel level) {
        List<SmokeVolumes.Volume> volumes = SmokeVolumes.active(level);
        boolean had = DEBUG_HAD_SMOKE.contains(level);
        if (volumes.isEmpty() && !had) {
            return;
        }
        if (volumes.isEmpty()) {
            DEBUG_HAD_SMOKE.remove(level);
        } else {
            DEBUG_HAD_SMOKE.add(level);
        }
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            if (!player.debugSubscriptions().contains(ESDebugSubscriptions.NPC.get())) {
                continue;
            }
            List<SmokeDebugPayload.Smoke> near = new ArrayList<>();
            for (SmokeVolumes.Volume volume : volumes) {
                if (near.size() < 64 && volume.center().distanceToSqr(player.position()) <= DEBUG_RANGE * DEBUG_RANGE) {
                    near.add(new SmokeDebugPayload.Smoke(volume.center(), volume.radius(), (int) (volume.expiresAt() - now)));
                }
            }
            ESNetwork.sendIfSupported(player, new SmokeDebugPayload(near));
        }
    }

    /** For debugging and tests: the player's active tool item, or null. */
    public static @Nullable Item activeItem(ServerPlayer player) {
        ActiveTool tool = activeTool(player);
        return tool.isNone() ? null : tool.item();
    }
}
