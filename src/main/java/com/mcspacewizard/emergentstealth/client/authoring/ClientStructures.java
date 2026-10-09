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
 * What the client knows about structures (design doc 32 §1): the id list from the server, and previews
 * reassembled from their parts. Previews are small copies: solid blocks only.
 */
public final class ClientStructures {
    private ClientStructures() {}

    /** A structure's solid blocks, untransformed, for the browser and the ghost. */
    public record Preview(Identifier id, Vec3i size, BlockPos[] positions, BlockState[] states, List<Map.Entry<Block, Integer>> commonest) {
        public int solid() {
            return positions.length;
        }
    }

    private static final int KEEP = 4;
    private static List<Identifier> ids = List.of();
    private static final Map<Identifier, Preview> PREVIEWS = new LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Identifier, Preview> eldest) {
            return size() > KEEP;
        }
    };
    /** Parts received so far, per structure. */
    private static final Map<Identifier, byte[][]> PENDING = new HashMap<>();
    /** When each preview was last asked for: the server may refuse (no permission, unknown id), so ask again later. */
    private static final Map<Identifier, Long> ASKED = new HashMap<>();
    private static final long RETRY_MS = 3000L;

    public static List<Identifier> ids() {
        return ids;
    }

    public static @Nullable Preview preview(Identifier id) {
        return PREVIEWS.get(id);
    }

    /** Asks the server for a preview unless it's here, arriving, or was asked for in the last few seconds. */
    public static void request(Identifier id) {
        long now = net.minecraft.util.Util.getMillis();
        Long asked = ASKED.get(id);
        if (PREVIEWS.containsKey(id) || PENDING.containsKey(id) || (asked != null && now - asked < RETRY_MS)) {
            return;
        }
        ASKED.put(id, now);
        ClientPacketDistributor.sendToServer(new StructurePayloads.RequestPreview(id));
    }

    public static void handleList(StructurePayloads.StructureList payload, IPayloadContext context) {
        ids = List.copyOf(payload.ids());
        if (payload.open()) {
            Minecraft.getInstance().setScreen(new StructureBrowserScreen());
        }
    }

    public static void handlePart(StructurePayloads.PreviewPart payload, IPayloadContext context) {
        byte[][] parts = PENDING.get(payload.id());
        if (parts == null || parts.length != payload.parts()) {
            parts = new byte[payload.parts()][];
            PENDING.put(payload.id(), parts);
        }
        parts[payload.part()] = payload.data();
        byte[] whole = StructureCatalog.join(parts);
        if (whole == null) {
            return;
        }
        PENDING.remove(payload.id());
        ASKED.remove(payload.id());
        try {
            PREVIEWS.put(payload.id(), parse(payload.id(), whole));
        } catch (Exception e) {
            EmergentStealth.LOGGER.warn("Structure viewer: couldn't read the preview of {}", payload.id(), e);
        }
    }

    private static Preview parse(Identifier id, byte[] bytes) throws java.io.IOException {
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
        return new Preview(id, new Vec3i(size.getInt(0).orElse(0), size.getInt(1).orElse(0), size.getInt(2).orElse(0)),
                positions, states, List.copyOf(commonest.subList(0, Math.min(6, commonest.size()))));
    }

    public static void clear() {
        ids = List.of();
        PREVIEWS.clear();
        PENDING.clear();
        ASKED.clear();
    }
}
