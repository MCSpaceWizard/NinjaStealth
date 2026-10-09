package com.mcspacewizard.emergentstealth.gametest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.authoring.StructureCatalog;
import com.mcspacewizard.emergentstealth.authoring.StructurePlacement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Imported structures: the builder's ({@code tools/structures/import_structures.py}) and the Cherry Grove modules
 * ({@code import_schematics.py}). Every one loads, the game's
 * data fixer turned no block into air (which would mean an unknown stand-in in {@code block_map.json}), the
 * spawners are gone, and every chest's loot table exists.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class StructureTests {
    private StructureTests() {}

    private static final Identifier ARENA = EmergentStealth.id("arena");
    private static final Identifier TEST = EmergentStealth.id("structures/imports_load");
    /** Imported folders and how many structures each should hold (2026-10-09). */
    private static final Map<String, Integer> EXPECTED = Map.of("structure/edo", 21, "structure/cherrygrove", 17, "structure/sites", 1);
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    private static final Map<Identifier, java.util.function.Consumer<GameTestHelper>> TESTS = Map.of(
            TEST, StructureTests::importsLoad,
            EmergentStealth.id("structures/place_and_undo"), StructureTests::placeAndUndo,
            EmergentStealth.id("structures/preview_data"), StructureTests::previewData);

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        TESTS.forEach((id, function) -> event.register(Registries.TEST_FUNCTION, id, () -> function));
    }

    @SubscribeEvent
    static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(EmergentStealth.id("structures"));
        for (Identifier id : TESTS.keySet()) {
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(environment, ARENA, 20, 0, true)));
        }
    }

    /** Placing a structure and undoing it puts every block (and block entity) back; the structure's chest is gone. */
    static void placeAndUndo(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        Identifier shrine = EmergentStealth.id("edo/small_shrine_v1");
        // High above the arena, clear of neighbouring tests.
        BlockPos origin = helper.absolutePos(new BlockPos(0, 20, 0));
        java.util.UUID author = java.util.UUID.randomUUID();
        BlockPos marker = origin.offset(2, 2, 2);
        level.setBlock(marker, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        StructureTemplate template = server(helper).getStructureManager().get(shrine).orElseThrow();
        var box = StructurePlacement.place(level, author, shrine, origin, net.minecraft.world.level.block.Rotation.CLOCKWISE_90,
                net.minecraft.world.level.block.Mirror.NONE);
        helper.assertTrue(box != null, "The shrine should place");
        int chests = 0;
        int solid = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (!level.getBlockState(pos).isAir()) {
                solid++;
            }
            if (level.getBlockState(pos).is(Blocks.CHEST)) {
                chests++;
            }
        }
        helper.assertTrue(solid > 100 && chests == 1, "Placed: " + solid + " solid blocks and " + chests + " chests");
        helper.assertTrue(box.getXSpan() == template.getSize().getZ() && box.getZSpan() == template.getSize().getX(),
                "Rotating 90° swaps the footprint");
        // Water that "flowed" out of the structure, and petals standing on it, which mustn't pop off as items.
        BlockPos spill = new BlockPos(box.maxX() + 3, box.minY(), box.minZ());
        level.setBlock(spill, Blocks.WATER.defaultBlockState(), 3);
        BlockPos petals = new BlockPos(box.minX(), box.maxY() + 1, box.minZ());
        level.setBlock(petals.below(), Blocks.MOSS_BLOCK.defaultBlockState(), 3);
        level.setBlock(petals, Blocks.PINK_PETALS.defaultBlockState(), 3);
        helper.assertTrue(StructurePlacement.undo(author) != null, "Undo should restore");
        helper.assertTrue(level.getBlockState(spill).isAir(), "Undo should take back water that flowed out, found " + level.getBlockState(spill));
        helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, net.minecraft.world.phys.AABB.of(box).inflate(2)).isEmpty(),
                "Undo shouldn't drop items");
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            boolean expectGold = pos.equals(marker);
            helper.assertTrue(level.getBlockState(pos).is(expectGold ? Blocks.GOLD_BLOCK : Blocks.AIR),
                    "After undo " + pos + " should be " + (expectGold ? "gold" : "air") + ", is " + level.getBlockState(pos));
            helper.assertTrue(level.getBlockEntity(pos) == null, "No block entity left at " + pos);
        }
        helper.assertTrue(StructurePlacement.undo(author) == null, "Only one level of undo");
        helper.succeed();
    }

    /** The ghost preview's data has no air, splits under the payload limit and joins back exactly. */
    static void previewData(GameTestHelper helper) {
        Identifier id = EmergentStealth.id("cherrygrove/teahouse");
        byte[] bytes = StructureCatalog.previewBytes(server(helper), id);
        helper.assertTrue(bytes != null && bytes.length > 0, "Preview bytes");
        CompoundTag tag;
        try {
            tag = NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        ListTag palette = tag.getListOrEmpty("palette");
        ListTag blocks = tag.getListOrEmpty("blocks");
        for (int i = 0; i < blocks.size(); i++) {
            String name = palette.getCompoundOrEmpty(blocks.getCompoundOrEmpty(i).getIntOr("state", 0)).getStringOr("Name", "");
            helper.assertTrue(!AIR.contains(name), "Preview should hold no air, found " + name);
        }
        StructureTemplate template = server(helper).getStructureManager().get(id).orElseThrow();
        StructurePlaceSettings settings = new StructurePlaceSettings();
        int air = template.filterBlocks(BlockPos.ZERO, settings, Blocks.AIR).size();
        int total = template.getSize().getX() * template.getSize().getY() * template.getSize().getZ();
        helper.assertTrue(blocks.size() == total - air, "Preview has " + blocks.size() + " blocks, the template " + (total - air) + " solid");
        List<byte[]> parts = StructureCatalog.split(bytes, 1000);
        helper.assertTrue(parts.size() == (bytes.length + 999) / 1000, "Split into 1000-byte parts");
        helper.assertTrue(java.util.Arrays.equals(StructureCatalog.join(parts.toArray(new byte[0][])), bytes), "Join restores the bytes");
        byte[][] missing = parts.toArray(new byte[0][]);
        missing[1] = null;
        helper.assertTrue(StructureCatalog.join(missing) == null, "A missing part means not complete yet");
        helper.succeed();
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    static void importsLoad(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<String> problems = new ArrayList<>();
        Map<Identifier, Resource> files = new java.util.HashMap<>();
        EXPECTED.forEach((folder, expected) -> {
            Map<Identifier, Resource> found = server.getResourceManager().listResources(folder, path -> path.getPath().endsWith(".nbt"));
            if (found.size() != expected) {
                problems.add("expected " + expected + " structures in " + folder + ", found " + found.size());
            }
            files.putAll(found);
        });
        for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
            String path = file.getKey().getPath();
            Identifier id = file.getKey().withPath(path.substring("structure/".length(), path.length() - ".nbt".length()));
            CompoundTag raw;
            try (InputStream in = file.getValue().open()) {
                raw = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            } catch (Exception e) {
                problems.add(id + ": unreadable (" + e.getMessage() + ")");
                continue;
            }
            StructureTemplate template = server.getStructureManager().get(id).orElse(null);
            if (template == null) {
                problems.add(id + ": doesn't load");
                continue;
            }
            // Air before and after the data fixer: any extra air is a block the game doesn't know.
            ListTag palette = raw.getListOrEmpty("palette");
            ListTag blocks = raw.getListOrEmpty("blocks");
            Map<BlockPos, String> rawNames = new java.util.HashMap<>();
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag block = blocks.getCompoundOrEmpty(i);
                String name = palette.getCompoundOrEmpty(block.getIntOr("state", 0)).getStringOr("Name", "");
                ListTag pos = block.getListOrEmpty("pos");
                rawNames.put(new BlockPos(pos.getInt(0).orElse(0), pos.getInt(1).orElse(0), pos.getInt(2).orElse(0)), name);
                String table = block.getCompoundOrEmpty("nbt").getStringOr("LootTable", "");
                if (!table.isEmpty() && server.reloadableRegistries().getLootTable(
                        ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(table))) == LootTable.EMPTY) {
                    problems.add(id + ": missing loot table " + table);
                }
            }
            // Blocks that are air after loading but weren't in the file: names the game doesn't know.
            StructurePlaceSettings settings = new StructurePlaceSettings();
            java.util.TreeMap<String, Integer> lost = new java.util.TreeMap<>();
            for (var air : List.of(Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR)) {
                for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, air)) {
                    String name = rawNames.getOrDefault(info.pos(), "?");
                    if (!AIR.contains(name)) {
                        lost.merge(name, 1, Integer::sum);
                    }
                }
            }
            if (!lost.isEmpty()) {
                problems.add(id + ": blocks became air " + lost);
            }
            if (!template.filterBlocks(BlockPos.ZERO, settings, Blocks.SPAWNER).isEmpty()) {
                problems.add(id + ": still has spawners");
            }
        }
        helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
        helper.succeed();
    }
}
