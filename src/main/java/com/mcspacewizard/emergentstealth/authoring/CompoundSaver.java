package com.mcspacewizard.emergentstealth.authoring;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mcspacewizard.emergentstealth.stealth.light.Snuffing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Saving a compound draft (design doc 32 §3): linked zones and routes are copied again, and every module whose
 * blocks were edited in the world since it was placed is saved again as a template of its own, so the compound
 * keeps the edits. The command and the Compound Ledger both save through here.
 */
public final class CompoundSaver {
    private CompoundSaver() {}

    /**
     * A saved compound.
     *
     * @param resaved the templates written for edited modules
     * @param skipped modules that couldn't be checked (not loaded)
     */
    public record Saved(Identifier id, Path file, List<Identifier> resaved, int skipped) {}

    public static Saved save(MinecraftServer server, UUID author, CompoundDrafts.Draft draft) throws IOException {
        draft = CompoundDrafts.refreshLinks(server, author, draft);
        ServerLevel level = server.getLevel(draft.dimension());
        List<Identifier> resaved = new ArrayList<>();
        int skipped = 0;
        if (level != null) {
            Compound compound = draft.compound();
            for (int m = 0; m < compound.structures().size(); m++) {
                Compound.Module module = compound.structures().get(m);
                Edit edit = edited(level, draft.origin(), module);
                if (edit == Edit.UNLOADED) {
                    skipped++;
                } else if (edit == Edit.EDITED) {
                    Compound.Module saved = resave(level, draft, module, m);
                    compound = compound.withModule(m, saved);
                    resaved.add(saved.template());
                }
            }
            if (!resaved.isEmpty()) {
                draft = CompoundDrafts.replace(author, draft.with(compound));
            }
        }
        Path file = Compounds.saveToWorld(server, draft.id(), draft.compound());
        StructureCatalog.clearCache(); // the browser's preview of this compound (and of re-saved templates) is stale
        return new Saved(draft.id(), file, List.copyOf(resaved), skipped);
    }

    /** The template an edited module of a compound is saved as: {@code <namespace>:compound/<path>/<module>}. */
    public static Identifier moduleTemplate(Identifier compound, int module) {
        return Identifier.fromNamespaceAndPath(compound.getNamespace(), "compound/" + compound.getPath() + "/" + module);
    }

    enum Edit { SAME, EDITED, UNLOADED, MISSING }

    /**
     * Whether the world still matches a module's template where it was placed. Blocks are compared by kind (not
     * state), a snuffed light counts as its lit form, water that flowed into the template's air is ignored, and so
     * is a block missing where it couldn't stand anyway.
     */
    static Edit edited(ServerLevel level, BlockPos draftOrigin, Compound.Module module) {
        StructureTemplate template = level.getServer().getStructureManager().get(module.template()).orElse(null);
        if (template == null) {
            return Edit.MISSING;
        }
        BlockPos origin = draftOrigin.offset(module.offset());
        BoundingBox box = StructurePlacement.box(template, origin, module.rotation(), module.mirror());
        if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
            return Edit.UNLOADED;
        }
        TemplateBlocks blocks = TemplateBlocks.of(template, level.holderLookup(Registries.BLOCK));
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos at = StructureTemplate.transform(blocks.positions()[i], module.mirror(), module.rotation(), BlockPos.ZERO).offset(origin);
            BlockState expected = blocks.states()[i].mirror(module.mirror()).rotate(module.rotation());
            BlockState actual = level.getBlockState(at);
            // A block that can't stand where the template puts it (a lantern on a slab edge) is never there.
            boolean couldNotStay = actual.isAir() && !expected.canSurvive(level, at);
            if (!couldNotStay && !same(expected.getBlock(), actual)) {
                return Edit.EDITED;
            }
        }
        return Edit.SAME;
    }

    private static boolean same(Block expected, BlockState actual) {
        if (expected.defaultBlockState().isAir()) {
            return actual.isAir() || (actual.getBlock() instanceof LiquidBlock && !actual.getFluidState().isSource());
        }
        return Snuffing.litForm(actual.getBlock()) == Snuffing.litForm(expected);
    }

    /** Saves the module's box as it stands in the world and returns the module pointing at the new template, unturned. */
    private static Compound.Module resave(ServerLevel level, CompoundDrafts.Draft draft, Compound.Module module, int index) {
        StructureTemplateManager manager = level.getServer().getStructureManager();
        StructureTemplate template = manager.get(module.template()).orElseThrow();
        BlockPos origin = draft.origin().offset(module.offset());
        BoundingBox box = StructurePlacement.box(template, origin, module.rotation(), module.mirror());
        BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
        Identifier id = moduleTemplate(draft.id(), index);
        StructureTemplate copy = manager.getOrCreate(id);
        copy.fillFromWorld(level, min, new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan()), false, List.of(Blocks.STRUCTURE_VOID));
        manager.save(id);
        return new Compound.Module(id, draft.local(min), Rotation.NONE, Mirror.NONE, module.ground());
    }
}
