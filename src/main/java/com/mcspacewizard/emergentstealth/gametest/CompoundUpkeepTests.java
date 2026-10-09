package com.mcspacewizard.emergentstealth.gametest;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoutes;
import com.mcspacewizard.emergentstealth.authoring.Compound;
import com.mcspacewizard.emergentstealth.authoring.CompoundDrafts;
import com.mcspacewizard.emergentstealth.authoring.CompoundPlacer;
import com.mcspacewizard.emergentstealth.authoring.CompoundSaver;
import com.mcspacewizard.emergentstealth.authoring.Compounds;
import com.mcspacewizard.emergentstealth.authoring.GroundLine;
import com.mcspacewizard.emergentstealth.authoring.PlacedCompounds;
import com.mcspacewizard.emergentstealth.authoring.StructureCatalog;
import com.mcspacewizard.emergentstealth.authoring.StructurePlacement;
import com.mcspacewizard.emergentstealth.authoring.Transform;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.stealth.light.Snuffing;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Looking after placed compounds and saving them (design doc 32 §3): reset brings the garrison and markers back,
 * saving keeps edits made to a module in the world, ground lines are found, the light rule reaches the world, and
 * the browser's compound preview holds every module.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class CompoundUpkeepTests {
    private CompoundUpkeepTests() {}

    private static final Identifier ARENA = EmergentStealth.id("arena");

    private static final Map<Identifier, Consumer<GameTestHelper>> TESTS = Map.of(
            EmergentStealth.id("compounds/reset"), CompoundUpkeepTests::reset,
            EmergentStealth.id("compounds/resave_edited"), CompoundUpkeepTests::resaveEdited,
            EmergentStealth.id("compounds/ground_lines"), CompoundUpkeepTests::groundLines,
            EmergentStealth.id("compounds/lights"), CompoundUpkeepTests::lights,
            EmergentStealth.id("compounds/browser_preview"), CompoundUpkeepTests::browserPreview);

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        TESTS.forEach((id, function) -> event.register(Registries.TEST_FUNCTION, id, () -> function));
    }

    @SubscribeEvent
    static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(EmergentStealth.id("compound_upkeep"));
        for (Identifier id : TESTS.keySet()) {
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(environment, ARENA, 40, 0, true)));
        }
    }

    private static BlockPos chest(ServerLevel level) {
        StructureTemplate template = level.getServer().getStructureManager().get(CompoundTests.SHRINE).orElseThrow();
        return template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST).getFirst().pos();
    }

    /** A missing guard is respawned, a knocked-out one cleared and replaced, a standing one kept; markers come back. */
    static void reset(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Compound compound = CompoundTests.sample(chest(level));
        Identifier id = EmergentStealth.id("test/reset_shrine");
        BlockPos origin = helper.absolutePos(new BlockPos(8, 20, 8));
        Transform t = new Transform(Mirror.NONE, Rotation.CLOCKWISE_90);
        PlacedCompounds.Copy copy = CompoundPlacer.place(level, UUID.randomUUID(), id, compound, origin, t).copy();
        helper.assertTrue(copy != null && copy.npcs().size() == 1 && copy.npcSpawns().equals(List.of(0)), "Placed with one guard of spawn 0: " + copy);
        String route = copy.routes().getFirst();

        // Gone (removed) and its route deleted: both come back.
        level.getEntity(copy.npcs().getFirst()).discard();
        PatrolRoutes.get(level).remove(route);
        CompoundPlacer.ResetResult first = CompoundPlacer.reset(level, copy, compound);
        helper.assertTrue(first.respawned() == 1 && first.kept() == 0, "A missing guard is respawned: " + first);
        helper.assertTrue(PatrolRoutes.get(level).get(route).isPresent(), "The deleted route is back");
        copy = PlacedCompounds.get(level).get(copy.id()).orElseThrow();
        StealthNpc guard = (StealthNpc) level.getEntity(copy.npcs().getFirst());
        helper.assertTrue(guard != null && guard.blockPosition().equals(t.apply(compound.spawns().getFirst().pos()).offset(origin)),
                "The new guard stands on its spawn");

        // Knocked out: the body is cleared and a fresh guard takes its place.
        guard.knockOut(level, null);
        CompoundPlacer.ResetResult second = CompoundPlacer.reset(level, copy, compound);
        helper.assertTrue(second.cleared() == 1 && second.respawned() == 1, "A body is cleared and replaced: " + second);
        helper.assertTrue(guard.isRemoved(), "The body is gone");

        // Standing: nothing to do.
        copy = PlacedCompounds.get(level).get(copy.id()).orElseThrow();
        CompoundPlacer.ResetResult third = CompoundPlacer.reset(level, copy, compound);
        helper.assertTrue(third.kept() == 1 && third.respawned() == 0 && third.cleared() == 0, "A standing guard is kept: " + third);
        helper.succeed();
    }

    /** Saving a draft keeps a module as it was until a block in it changes; then the edit is saved as its own template. */
    static void resaveEdited(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID author = UUID.randomUUID();
        Identifier id = EmergentStealth.id("test/edited_shrine");
        BlockPos origin = helper.absolutePos(new BlockPos(8, 20, 8));
        try {
            CompoundDrafts.start(author, id, level.dimension(), origin, Compound.EMPTY);
            StructurePlacement.place(level, author, CompoundTests.SHRINE, origin.offset(2, 0, 1), Rotation.CLOCKWISE_90, Mirror.NONE);
            CompoundDrafts.onPlaced(level.getServer(), author, StructurePlacement.last(author));

            CompoundSaver.Saved clean = CompoundSaver.save(level.getServer(), author, CompoundDrafts.get(author));
            helper.assertTrue(clean.resaved().isEmpty(), "An untouched module isn't saved again: " + clean.resaved());

            // Snuffing a light isn't an edit.
            List<BlockPos> lights = CompoundDrafts.lightsIn(level, CompoundDrafts.get(author));
            helper.assertTrue(!lights.isEmpty(), "The shrine's lights are found");
            Snuffing.snuff(level, lights.getFirst(), null);
            helper.assertTrue(CompoundSaver.save(level.getServer(), author, CompoundDrafts.get(author)).resaved().isEmpty(),
                    "A snuffed light doesn't count as an edit");

            BlockPos chestAt = new Transform(Mirror.NONE, Rotation.CLOCKWISE_90).apply(chest(level)).offset(origin.offset(2, 0, 1));
            helper.assertTrue(level.getBlockState(chestAt).is(Blocks.CHEST), "The chest is where the template puts it");
            level.setBlock(chestAt, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            CompoundSaver.Saved edited = CompoundSaver.save(level.getServer(), author, CompoundDrafts.get(author));
            Identifier own = CompoundSaver.moduleTemplate(id, 0);
            helper.assertTrue(edited.resaved().equals(List.of(own)), "The edited module is saved as " + own + ": " + edited.resaved());
            Compound.Module module = Compounds.get(id).structures().getFirst();
            helper.assertTrue(module.template().equals(own) && module.rotation() == Rotation.NONE && module.mirror() == Mirror.NONE,
                    "The module now uses its own template, unturned: " + module);

            // Placing the saved compound elsewhere brings the gold block along, on the same spot. Exact: right above the
            // draft, fitting the terrain would fill down into the draft's own box.
            BlockPos elsewhere = origin.offset(0, 12, 0);
            PlacedCompounds.Copy copy = CompoundPlacer.place(level, author, id, Compounds.get(id), elsewhere, Transform.IDENTITY,
                    CompoundTests.EXACT).copy();
            helper.assertTrue(copy != null, "The saved compound places");
            BlockPos gold = chestAt.subtract(origin).offset(elsewhere);
            helper.assertTrue(level.getBlockState(gold).is(Blocks.GOLD_BLOCK), "The edit is in the new copy at " + gold + ": " + level.getBlockState(gold));

            CompoundSaver.Saved again = CompoundSaver.save(level.getServer(), author, CompoundDrafts.get(author));
            helper.assertTrue(again.resaved().isEmpty(), "Saved again, the module matches its own template: " + again.resaved());
        } catch (java.io.IOException e) {
            helper.fail("Saving failed: " + e);
        } finally {
            CompoundDrafts.close(author);
        }
        helper.succeed();
    }

    /** Found ground lines match the ones set by hand for the examples (the Cherry Grove modules' rock bases). */
    static void groundLines(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Map<String, Integer> expected = Map.of("cherrygrove/gatehouse", 7, "cherrygrove/teahouse", 7, "edo/samurai_mini_fort", 0);
        expected.forEach((path, line) -> {
            int found = GroundLine.of(server, EmergentStealth.id(path));
            helper.assertTrue(found == line, path + ": ground line " + line + ", found " + found);
        });
        helper.succeed();
    }

    /** "relight none" with one exception: placed and turned, only the exception may be relit, and only inside the copy. */
    static void lights(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chest = chest(level);
        BlockPos lamp = chest.offset(0, 1, 0);
        Compound compound = CompoundTests.sample(chest).withLights(new Compound.Lights(Compound.Relight.NONE, List.of(lamp)));
        String json = Compounds.toJson(compound);
        helper.assertTrue(json.contains("\"relight\": \"none\"") && Compound.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString(json)).getOrThrow().lights().equals(compound.lights()), "Lights round trip:\n" + json);
        helper.assertTrue(!Compounds.toJson(CompoundTests.sample(chest)).contains("lights"), "The default rule isn't written");

        BlockPos origin = helper.absolutePos(new BlockPos(8, 20, 8));
        Transform t = new Transform(Mirror.FRONT_BACK, Rotation.CLOCKWISE_180);
        PlacedCompounds.Copy copy = CompoundPlacer.place(level, UUID.randomUUID(), EmergentStealth.id("test/dark_shrine"), compound, origin, t).copy();
        PlacedCompounds placed = PlacedCompounds.get(level);
        BlockPos lampAt = t.apply(lamp).offset(origin);
        BlockPos otherAt = t.apply(chest.offset(1, 1, 0)).offset(origin);
        helper.assertTrue(placed.mayRelight(lampAt), "The exception is relit at " + lampAt);
        helper.assertTrue(!placed.mayRelight(otherAt), "Other lights in the copy stay dark");
        helper.assertTrue(placed.mayRelight(copy.box().orElseThrow().getCenter().offset(0, 200, 0)), "Lights outside the copy are relit");
        CompoundPlacer.remove(level, copy.id());
        helper.assertTrue(placed.mayRelight(otherAt), "A removed copy no longer holds its lights dark");
        helper.succeed();
    }

    /** The browser's preview of a compound holds every module's blocks and says what the compound brings. */
    static void browserPreview(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Identifier id = EmergentStealth.id("examples/cherry_grove_estate");
        Compound compound = Compounds.get(id);
        byte[] bytes = StructureCatalog.compoundPreviewBytes(server, id);
        helper.assertTrue(compound != null && bytes != null, "The example has a preview");
        try {
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
            CompoundTag info = tag.getCompoundOrEmpty("compound");
            helper.assertTrue(info.getIntOr("modules", 0) == compound.structures().size() && info.getIntOr("missing", 1) == 0,
                    "Every module is in: " + info);
            int expected = 0;
            for (Compound.Module module : compound.structures()) {
                byte[] one = StructureCatalog.previewBytes(server, module.template());
                expected += NbtIo.readCompressed(new ByteArrayInputStream(one), NbtAccounter.unlimitedHeap()).getListOrEmpty("blocks").size();
            }
            int got = tag.getListOrEmpty("blocks").size();
            // Modules may overlap by a few blocks; then one block stands for both.
            helper.assertTrue(got <= expected && got > expected * 9 / 10, "The preview holds the modules' blocks: " + got + " of " + expected);
        } catch (java.io.IOException e) {
            helper.fail("Unreadable preview: " + e);
        }
        helper.assertTrue(StructureCatalog.compoundPreviewBytes(server, EmergentStealth.id("no_such_compound")) == null, "Unknown compound");
        helper.succeed();
    }
}
