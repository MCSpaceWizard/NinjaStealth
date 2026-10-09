package com.mcspacewizard.emergentstealth.authoring;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds a template's ground line (design doc 32 decision 6): the highest layer that is mostly ground or water
 * round its edges, which is the layer that should sit at terrain level. The edges are those of the blocks the
 * template really holds (structure voids round it don't count); a template with nothing at its edges uses whole
 * layers. A template with no such layer has its ground line at 0, its bottom.
 */
public final class GroundLine {
    private GroundLine() {}

    /** The ground line of a template the server knows, or 0. */
    public static int of(MinecraftServer server, Identifier template) {
        return server.getStructureManager().get(template)
                .map(t -> find(TemplateBlocks.of(t, server.registryAccess().lookupOrThrow(Registries.BLOCK))))
                .orElse(0);
    }

    static int find(TemplateBlocks blocks) {
        if (blocks.size() == 0) {
            return 0;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE, maxY = 0;
        for (BlockPos p : blocks.positions()) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
            maxY = Math.max(maxY, p.getY());
        }
        int[] ground = new int[maxY + 1];
        int[] total = new int[maxY + 1];
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos p = blocks.positions()[i];
            if (p.getY() < 0 || (p.getX() != minX && p.getX() != maxX && p.getZ() != minZ && p.getZ() != maxZ)) {
                continue;
            }
            total[p.getY()]++;
            if (isGround(blocks.states()[i])) {
                ground[p.getY()]++;
            }
        }
        int line = 0;
        for (int y = 0; y <= maxY; y++) {
            if (total[y] > 0 && ground[y] * 2 > total[y]) {
                line = y;
            }
        }
        return line;
    }

    /** Natural ground: soil, sand, gravel, bare stone, water. Paving and building blocks aren't. */
    public static boolean isGround(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.MUD) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.getFluidState().is(FluidTags.WATER);
    }
}
