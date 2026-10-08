package com.mcspacewizard.emergentstealth.tool;

import com.mcspacewizard.emergentstealth.entity.ThrownItem;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseEvent;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseKind;
import com.mcspacewizard.emergentstealth.stealth.sound.Noises;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Pebble (design doc 21 §2): the classic distraction. Impact noise 10 at the landing spot, pointing at the spot,
 * not at the thrower, and no trace: the pebble is lost among the stones (S-05).
 */
public class PebbleItem extends ThrowableToolItem {
    public static final float IMPACT_LOUDNESS = 10.0F;

    public PebbleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onImpact(ServerLevel level, ThrownItem thrown, Vec3 at, HitResult hit) {
        Noises.emit(level, new NoiseEvent(at, IMPACT_LOUDNESS, NoiseKind.IMPACT, null, null));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_HIT, SoundSource.NEUTRAL, 1.0F,
                1.3F + level.getRandom().nextFloat() * 0.3F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState()),
                at.x, at.y, at.z, 6, 0.08, 0.08, 0.08, 0.05);
    }
}
