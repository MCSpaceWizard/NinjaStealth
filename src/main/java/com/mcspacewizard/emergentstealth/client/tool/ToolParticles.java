package com.mcspacewizard.emergentstealth.client.tool;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Client particles for the stealth tools (design doc 21). Textures are vanilla's smoke and puff sprites. */
public final class ToolParticles {
    private ToolParticles() {}

    /**
     * A big, slow grey puff. Many overlapping ones (fed by the cloud entity every tick) make the smoke bomb's
     * cloud a thick, readable wall: a guard's view through it is blocked, and it should look that way.
     */
    public static final class SmokeCloudParticle extends SingleQuadParticle {
        private static final float MAX_ALPHA = 0.88F;
        private static final int FADE_IN = 6;
        private static final int FADE_OUT = 16;

        SmokeCloudParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite) {
            super(level, x, y, z, sprite);
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            this.lifetime = 40 + this.random.nextInt(25);
            this.quadSize = 0.85F + this.random.nextFloat() * 0.6F;
            this.gravity = 0.0F;
            this.friction = 0.94F;
            this.roll = this.random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
            float grey = 0.52F + this.random.nextFloat() * 0.2F;
            this.setColor(grey, grey, grey * 1.02F);
            this.setAlpha(0.0F);
            this.setSize(0.4F, 0.4F);
        }

        @Override
        public void tick() {
            super.tick();
            float a;
            if (this.age < FADE_IN) {
                a = this.age / (float) FADE_IN;
            } else if (this.age > this.lifetime - FADE_OUT) {
                a = Math.max(0.0F, (this.lifetime - this.age) / (float) FADE_OUT);
            } else {
                a = 1.0F;
            }
            this.setAlpha(a * MAX_ALPHA);
            this.oRoll = this.roll;
            this.roll += 0.004F;
            this.quadSize += 0.004F;
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }
    }

    public static final class SmokeCloudProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public SmokeCloudProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd, RandomSource random) {
            return new SmokeCloudParticle(level, x, y, z, xd, yd, zd, this.sprites.get(random));
        }
    }

    /** A pale, glittering puff that shoots out, slows quickly, grows and fades: blinding powder. */
    public static final class BlindingPuffParticle extends SingleQuadParticle {
        private final SpriteSet sprites;

        BlindingPuffParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
            super(level, x, y, z, sprites.first());
            this.sprites = sprites;
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            this.lifetime = 18 + this.random.nextInt(12);
            this.quadSize = 0.12F + this.random.nextFloat() * 0.1F;
            this.gravity = -0.002F;
            this.friction = 0.86F;
            float tint = this.random.nextFloat() * 0.12F;
            this.setColor(1.0F, 0.96F - tint * 0.3F, 0.72F + tint);
            this.setAlpha(0.9F);
            this.setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            this.setSpriteFromAge(this.sprites);
            this.quadSize = Math.min(0.55F, this.quadSize + 0.018F);
            this.setAlpha(0.9F * (1.0F - (float) this.age / this.lifetime));
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }
    }

    public static final class BlindingPuffProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public BlindingPuffProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd, RandomSource random) {
            return new BlindingPuffParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
