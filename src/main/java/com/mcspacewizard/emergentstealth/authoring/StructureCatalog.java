package com.mcspacewizard.emergentstealth.authoring;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The structures the viewer can browse (design doc 32 §1): every template the server knows, ours first, and a
 * compact copy of each for the client's ghost preview. Compounds get one too, all their structures in one.
 */
public final class StructureCatalog {
    private StructureCatalog() {}

    /** Preview parts stay well under the 1 MiB custom payload limit. */
    public static final int PART_BYTES = 512 * 1024;
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void");
    private static final int CACHE_SIZE = 8;

    /** Recently sent previews (the browser asks again when you flick back and forth). */
    private static final Map<Identifier, byte[]> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Identifier, byte[]> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /** Every template id, ours first, then by namespace and path. Our GameTest arena is left out. */
    public static List<Identifier> ids(MinecraftServer server) {
        return server.getStructureManager().listTemplates()
                .filter(id -> !id.equals(EmergentStealth.id("arena")))
                .sorted(Comparator.comparing((Identifier id) -> !id.getNamespace().equals(EmergentStealth.MODID))
                        .thenComparing(Identifier::getNamespace).thenComparing(Identifier::getPath))
                .toList();
    }

    /**
     * The template as compressed NBT for the ghost preview: size, palette and solid blocks only (no air, no block
     * entity data, no entities). Null if the template doesn't exist.
     */
    public static synchronized byte @Nullable [] previewBytes(MinecraftServer server, Identifier id) {
        byte[] cached = CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        StructureTemplate template = server.getStructureManager().get(id).orElse(null);
        if (template == null) {
            return null;
        }
        CompoundTag full = template.save(new CompoundTag());
        CompoundTag preview = new CompoundTag();
        preview.put("size", full.getListOrEmpty("size"));
        ListTag palette = full.contains("palette") ? full.getListOrEmpty("palette")
                : full.getListOrEmpty("palettes").getListOrEmpty(0);
        preview.put("palette", palette);
        ListTag blocks = new ListTag();
        ListTag source = full.getListOrEmpty("blocks");
        for (int i = 0; i < source.size(); i++) {
            CompoundTag block = source.getCompoundOrEmpty(i);
            int state = block.getIntOr("state", 0);
            if (AIR.contains(palette.getCompoundOrEmpty(state).getStringOr("Name", "minecraft:air"))) {
                continue;
            }
            CompoundTag slim = new CompoundTag();
            slim.put("pos", block.getListOrEmpty("pos"));
            slim.putInt("state", state);
            blocks.add(slim);
        }
        preview.put("blocks", blocks);
        byte[] bytes = compress(preview);
        CACHE.put(id, bytes);
        return bytes;
    }

    /** Recently sent compound previews. */
    private static final Map<Identifier, byte[]> COMPOUND_CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Identifier, byte[]> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /**
     * A compound's preview: every module's solid blocks, turned and moved as the compound places them, in one
     * palette (relative to the compound's origin, so positions can be negative), plus a {@code compound} tag with
     * what it brings (modules, zones, routes, spawns, NPCs). Null if the compound doesn't exist. Modules whose
     * template is missing are left out.
     */
    public static synchronized byte @Nullable [] compoundPreviewBytes(MinecraftServer server, Identifier id) {
        byte[] cached = COMPOUND_CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        Compound compound = Compounds.get(id);
        if (compound == null) {
            return null;
        }
        ListTag palette = new ListTag();
        Map<CompoundTag, Integer> paletteIndex = new HashMap<>();
        Long2IntLinkedOpenHashMap blocks = new Long2IntLinkedOpenHashMap();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int missing = 0;
        for (Compound.Module module : compound.structures()) {
            StructureTemplate template = server.getStructureManager().get(module.template()).orElse(null);
            if (template == null) {
                missing++;
                continue;
            }
            CompoundTag full = template.save(new CompoundTag());
            ListTag own = full.contains("palette") ? full.getListOrEmpty("palette") : full.getListOrEmpty("palettes").getListOrEmpty(0);
            int[] remap = new int[own.size()];
            for (int i = 0; i < remap.length; i++) {
                CompoundTag state = own.getCompoundOrEmpty(i);
                if (AIR.contains(state.getStringOr("Name", "minecraft:air"))) {
                    remap[i] = -1;
                    continue;
                }
                remap[i] = paletteIndex.computeIfAbsent(state, s -> {
                    palette.add(s);
                    return palette.size() - 1;
                });
            }
            Transform transform = module.transform();
            ListTag source = full.getListOrEmpty("blocks");
            for (int i = 0; i < source.size(); i++) {
                CompoundTag block = source.getCompoundOrEmpty(i);
                int state = block.getIntOr("state", 0);
                if (state < 0 || state >= remap.length || remap[state] < 0) {
                    continue;
                }
                ListTag p = block.getListOrEmpty("pos");
                BlockPos at = transform.apply(new BlockPos(p.getInt(0).orElse(0), p.getInt(1).orElse(0), p.getInt(2).orElse(0))).offset(module.offset());
                blocks.put(at.asLong(), remap[state]);
                minX = Math.min(minX, at.getX());
                minY = Math.min(minY, at.getY());
                minZ = Math.min(minZ, at.getZ());
                maxX = Math.max(maxX, at.getX());
                maxY = Math.max(maxY, at.getY());
                maxZ = Math.max(maxZ, at.getZ());
            }
        }
        CompoundTag preview = new CompoundTag();
        ListTag size = new ListTag();
        boolean empty = blocks.isEmpty();
        size.add(IntTag.valueOf(empty ? 0 : maxX - minX + 1));
        size.add(IntTag.valueOf(empty ? 0 : maxY - minY + 1));
        size.add(IntTag.valueOf(empty ? 0 : maxZ - minZ + 1));
        preview.put("size", size);
        preview.put("palette", palette);
        ListTag list = new ListTag();
        for (Long2IntMap.Entry entry : blocks.long2IntEntrySet()) {
            BlockPos at = BlockPos.of(entry.getLongKey());
            CompoundTag slim = new CompoundTag();
            ListTag pos = new ListTag();
            pos.add(IntTag.valueOf(at.getX()));
            pos.add(IntTag.valueOf(at.getY()));
            pos.add(IntTag.valueOf(at.getZ()));
            slim.put("pos", pos);
            slim.putInt("state", entry.getIntValue());
            list.add(slim);
        }
        preview.put("blocks", list);
        CompoundTag info = new CompoundTag();
        info.putInt("modules", compound.structures().size());
        info.putInt("missing", missing);
        info.putInt("zones", compound.zones().size());
        info.putInt("routes", compound.routes().size());
        info.putInt("spawns", compound.spawns().size());
        info.putInt("npcs", compound.spawns().stream().mapToInt(Compound.Spawn::count).sum());
        preview.put("compound", info);
        byte[] bytes = compress(preview);
        COMPOUND_CACHE.put(id, bytes);
        return bytes;
    }

    private static byte[] compress(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Splits bytes into parts of at most {@code partBytes} (at least one part, even when empty). */
    public static List<byte[]> split(byte[] bytes, int partBytes) {
        List<byte[]> parts = new ArrayList<>();
        for (int start = 0; start < bytes.length || parts.isEmpty(); start += partBytes) {
            parts.add(Arrays.copyOfRange(bytes, start, Math.min(bytes.length, start + partBytes)));
        }
        return parts;
    }

    /** Joins parts back together, or null if any is missing. */
    public static byte @Nullable [] join(byte[][] parts) {
        int length = 0;
        for (byte[] part : parts) {
            if (part == null) {
                return null;
            }
            length += part.length;
        }
        byte[] out = new byte[length];
        int at = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
    }

    /** Forgets cached previews (after a datapack reload). */
    public static synchronized void clearCache() {
        CACHE.clear();
        COMPOUND_CACHE.clear();
    }
}
