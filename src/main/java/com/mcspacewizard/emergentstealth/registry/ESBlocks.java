package com.mcspacewizard.emergentstealth.registry;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.block.UnlitTorchBlock;
import com.mcspacewizard.emergentstealth.block.UnlitWallTorchBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ESBlocks {
    private ESBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EmergentStealth.MODID);

    // Put-out lights (design doc 13 §3). They keep the lit block's shape and placement but give no light.
    public static final DeferredBlock<UnlitTorchBlock> UNLIT_TORCH = BLOCKS.registerBlock("unlit_torch",
            UnlitTorchBlock::new, () -> unlit(Blocks.TORCH));
    public static final DeferredBlock<UnlitWallTorchBlock> UNLIT_WALL_TORCH = BLOCKS.registerBlock("unlit_wall_torch",
            UnlitWallTorchBlock::new, () -> unlit(Blocks.WALL_TORCH));
    public static final DeferredBlock<UnlitTorchBlock> UNLIT_SOUL_TORCH = BLOCKS.registerBlock("unlit_soul_torch",
            UnlitTorchBlock::new, () -> unlit(Blocks.SOUL_TORCH));
    public static final DeferredBlock<UnlitWallTorchBlock> UNLIT_SOUL_WALL_TORCH = BLOCKS.registerBlock("unlit_soul_wall_torch",
            UnlitWallTorchBlock::new, () -> unlit(Blocks.SOUL_WALL_TORCH));
    public static final DeferredBlock<UnlitTorchBlock> UNLIT_COPPER_TORCH = BLOCKS.registerBlock("unlit_copper_torch",
            UnlitTorchBlock::new, () -> unlit(Blocks.COPPER_TORCH));
    public static final DeferredBlock<UnlitWallTorchBlock> UNLIT_COPPER_WALL_TORCH = BLOCKS.registerBlock("unlit_copper_wall_torch",
            UnlitWallTorchBlock::new, () -> unlit(Blocks.COPPER_WALL_TORCH));
    public static final DeferredBlock<LanternBlock> UNLIT_LANTERN = BLOCKS.registerBlock("unlit_lantern",
            LanternBlock::new, () -> unlit(Blocks.LANTERN));
    public static final DeferredBlock<LanternBlock> UNLIT_SOUL_LANTERN = BLOCKS.registerBlock("unlit_soul_lantern",
            LanternBlock::new, () -> unlit(Blocks.SOUL_LANTERN));

    /** Scattered caltrops (design doc 21): thrown, never placed by hand, gone after 60 s. No item, no drops. */
    public static final DeferredBlock<com.mcspacewizard.emergentstealth.block.CaltropsBlock> CALTROPS = BLOCKS.registerBlock("caltrops",
            com.mcspacewizard.emergentstealth.block.CaltropsBlock::new, () -> BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                    .noCollision().noOcclusion().instabreak().noLootTable()
                    .sound(net.minecraft.world.level.block.SoundType.CHAIN)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    private static BlockBehaviour.Properties unlit(Block lit) {
        return BlockBehaviour.Properties.ofFullCopy(lit).lightLevel(state -> 0);
    }
}
