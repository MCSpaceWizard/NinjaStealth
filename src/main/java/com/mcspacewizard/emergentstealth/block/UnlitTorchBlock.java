package com.mcspacewizard.emergentstealth.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A standing torch that has been put out: same shape and placement rules, no light, no flame. */
public class UnlitTorchBlock extends TorchBlock {
    public static final MapCodec<UnlitTorchBlock> CODEC = simpleCodec(UnlitTorchBlock::new);

    public UnlitTorchBlock(Properties properties) {
        super(ParticleTypes.SMOKE, properties);
    }

    @Override
    public MapCodec<? extends TorchBlock> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    }
}
