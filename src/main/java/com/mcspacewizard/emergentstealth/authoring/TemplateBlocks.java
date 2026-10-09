package com.mcspacewizard.emergentstealth.authoring;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A template's blocks read from its saved form (the first palette), untransformed. Structure voids are left out,
 * air is kept. Used to compare a template with the world and to find its ground line.
 */
record TemplateBlocks(BlockPos[] positions, BlockState[] states) {
    static TemplateBlocks of(StructureTemplate template, HolderGetter<Block> blocks) {
        CompoundTag tag = template.save(new CompoundTag());
        ListTag paletteTag = tag.contains("palette") ? tag.getListOrEmpty("palette") : tag.getListOrEmpty("palettes").getListOrEmpty(0);
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = NbtUtils.readBlockState(blocks, paletteTag.getCompoundOrEmpty(i));
        }
        ListTag list = tag.getListOrEmpty("blocks");
        BlockPos[] positions = new BlockPos[list.size()];
        BlockState[] states = new BlockState[list.size()];
        int n = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag block = list.getCompoundOrEmpty(i);
            int index = block.getIntOr("state", 0);
            if (index < 0 || index >= palette.length || palette[index].is(Blocks.STRUCTURE_VOID)) {
                continue;
            }
            ListTag p = block.getListOrEmpty("pos");
            positions[n] = new BlockPos(p.getInt(0).orElse(0), p.getInt(1).orElse(0), p.getInt(2).orElse(0));
            states[n] = palette[index];
            n++;
        }
        return new TemplateBlocks(java.util.Arrays.copyOf(positions, n), java.util.Arrays.copyOf(states, n));
    }

    int size() {
        return positions.length;
    }
}
