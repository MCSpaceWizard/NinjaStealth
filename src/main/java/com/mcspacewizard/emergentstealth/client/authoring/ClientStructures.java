package com.mcspacewizard.emergentstealth.client.authoring;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.authoring.StructureCatalog;
import com.mcspacewizard.emergentstealth.authoring.StructurePayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What the client knows about structures (design doc 32 §1): the structure and compound id lists from the server,
 * and previews reassembled from their parts. Previews are small copies: solid blocks only (a compound's: all its
 * structures', placed as the compound places them).
 */
public final class ClientStructures {
    private ClientStructures() {}

    /** A structure or a compound. */
    public record Key(Identifier id, boolean compound) {}

    /** What a compound brings besides its blocks. */
    public record CompoundInfo(int modules, int missing, int zones, int routes, int spawns, int npcs) {}

    /** A structure's (or compound's) solid blocks, untransformed, for the browser and the ghost. */
    public record Preview(Key key, Vec3i size, BlockPos[] positions, BlockState[] states, List<Map.Entry<Block, Integer>> commonest,
                          @Nullable CompoundInfo info) {
        public int solid() {
            return positions.length;
        }

        public Identifier id() {
            return key.id();
        }
    }

    private static final int KEEP = 4;
    private static List<Identifier> ids = List.of();
    private static List<Identifier> compounds = List.of();
    private static final Map<Key, Preview> PREVIEWS = new LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Preview> eldest) {
            return size() > KEEP;
        }
    };
    /** Parts received so far, per structure. */
    private static final Map<Key, byte[][]> PENDING = new HashMap<>();
    /** When each preview was last asked for: the server may refuse (no permission, unknown id), so ask again later. */
    private static final Map<Key, Long> ASKED = new HashMap<>();
    private static final long RETRY_MS = 3000L;

    public static List<Identifier> ids() {
        return ids;
    }

    public static List<Identifier> compounds() {
        return compounds;
    }

    public static @Nullable Preview preview(Key key) {
        return PREVIEWS.get(key);
    }

    /** Asks the server for a preview unless it's here, arriving, or was asked for in the last few seconds. */
    public static void request(Key key) {
        long now = net.minecraft.util.Util.getMillis();
        Long asked = ASKED.get(key);
        if (PREVIEWS.containsKey(key) || PENDING.containsKey(key) || (asked != null && now - asked < RETRY_MS)) {
            return;
        }
        ASKED.put(key, now);
        ClientPacketDistributor.sendToServer(new StructurePayloads.RequestPreview(key.id(), key.compound()));
    }

    public static void handleList(StructurePayloads.StructureList payload, IPayloadContext context) {
        ids = List.copyOf(payload.ids());
        compounds = List.copyOf(payload.compounds());
        // A compound may have been saved again since its preview came: ask afresh.
        PREVIEWS.keySet().removeIf(Key::compound);
        if (payload.open()) {
            Minecraft.getInstance().setScreen(new StructureBrowserScreen());
        }
    }

    public static void handlePart(StructurePayloads.PreviewPart payload, IPayloadContext context) {
        Key key = new Key(payload.id(), payload.compound());
        byte[][] parts = PENDING.get(key);
        if (parts == null || parts.length != payload.parts()) {
            parts = new byte[payload.parts()][];
            PENDING.put(key, parts);
        }
        parts[payload.part()] = payload.data();
        byte[] whole = StructureCatalog.join(parts);
        if (whole == null) {
            return;
        }
        PENDING.remove(key);
        ASKED.remove(key);
        try {
            PREVIEWS.put(key, parse(key, whole));
        } catch (Exception e) {
            EmergentStealth.LOGGER.warn("Structure viewer: couldn't read the preview of {}", payload.id(), e);
        }
    }

    private static Preview parse(Key key, byte[] bytes) throws java.io.IOException {
        CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
        ListTag size = tag.getListOrEmpty("size");
        HolderGetter<Block> blocks = Minecraft.getInstance().level.holderLookup(Registries.BLOCK);
        ListTag paletteTag = tag.getListOrEmpty("palette");
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = NbtUtils.readBlockState(blocks, paletteTag.getCompoundOrEmpty(i));
        }
        ListTag list = tag.getListOrEmpty("blocks");
        BlockPos[] positions = new BlockPos[list.size()];
        BlockState[] states = new BlockState[list.size()];
        Map<Block, Integer> counts = new HashMap<>();
        for (int i = 0; i < positions.length; i++) {
            CompoundTag block = list.getCompoundOrEmpty(i);
            ListTag pos = block.getListOrEmpty("pos");
            positions[i] = new BlockPos(pos.getInt(0).orElse(0), pos.getInt(1).orElse(0), pos.getInt(2).orElse(0));
            states[i] = palette[Math.clamp(block.getIntOr("state", 0), 0, palette.length - 1)];
            counts.merge(states[i].getBlock(), 1, Integer::sum);
        }
        List<Map.Entry<Block, Integer>> commonest = new ArrayList<>(counts.entrySet());
        commonest.sort(Map.Entry.<Block, Integer>comparingByValue(Comparator.reverseOrder()));
        CompoundInfo info = null;
        if (tag.contains("compound")) {
            CompoundTag c = tag.getCompoundOrEmpty("compound");
            info = new CompoundInfo(c.getIntOr("modules", 0), c.getIntOr("missing", 0), c.getIntOr("zones", 0), c.getIntOr("routes", 0),
                    c.getIntOr("spawns", 0), c.getIntOr("npcs", 0));
        }
        return new Preview(key, new Vec3i(size.getInt(0).orElse(0), size.getInt(1).orElse(0), size.getInt(2).orElse(0)),
                positions, states, List.copyOf(commonest.subList(0, Math.min(6, commonest.size()))), info);
    }

    public static void clear() {
        ids = List.of();
        compounds = List.of();
        PREVIEWS.clear();
        PENDING.clear();
        ASKED.clear();
    }
}
