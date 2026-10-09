package com.mcspacewizard.emergentstealth.client.tool;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Particles for the tool effects (design doc 34 §2). All visual only. Registered in {@link ToolClientEvents};
 * the server sends most of them where something happens, projectiles spawn their own trails.
 */
public final class ToolEffectParticles {
    private ToolEffectParticles() {}

    /** Light coordinates for "glows in the dark". */
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Base: a camera-facing sprite that fades out over its life. */
    abstract static class Fading extends SingleQuadParticle {
        protected float startAlpha = 1.0F;

        Fading(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
            super(level, x, y, z, sprite);
        }

        protected float life() {
            return Mth.clamp(this.age / (float) this.lifetime, 0.0F, 1.0F);
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }
    }

    /** The dark heart of a smoke cloud: bigger and greyer than the edge puffs, so the cloud reads as solid. */
    public static final class SmokeCore extends Fading {
        SmokeCore(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite) {
            super(level, x, y, z, sprite);
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            this.lifetime = 55 + this.random.nextInt(25);
            this.quadSize = 1.2F + this.random.nextFloat() * 0.7F;
            this.gravity = 0.0F;
            this.friction = 0.92F;
            this.roll = this.random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
            float grey = 0.30F + this.random.nextFloat() * 0.12F;
            this.setColor(grey, grey, grey * 1.04F);
            this.setAlpha(0.0F);
            this.hasPhysics = false;
        }

        @Override
        public void tick() {
            super.tick();
            float t = life();
            // Fade in fast, hold, fade out slowly.
            float a = t < 0.12F ? t / 0.12F : t > 0.7F ? (1.0F - t) / 0.3F : 1.0F;
            this.setAlpha(Math.max(0.0F, a) * 0.92F);
            this.oRoll = this.roll;
            this.roll -= 0.006F;
            this.quadSize += 0.006F;
        }
    }

    /** Steam from a doused light: rises, swells, fades. */
    public static final class Steam extends Fading {
        private final SpriteSet sprites;

        Steam(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
            super(level, x, y, z, sprites.first());
            this.sprites = sprites;
            this.xd = xd + (this.random.nextDouble() - 0.5) * 0.02;
            this.yd = yd + 0.04 + this.random.nextDouble() * 0.03;
            this.zd = zd + (this.random.nextDouble() - 0.5) * 0.02;
            this.lifetime = 24 + this.random.nextInt(16);
            this.quadSize = 0.15F + this.random.nextFloat() * 0.1F;
            this.gravity = -0.01F;
            this.friction = 0.9F;
            float tint = 0.9F + this.random.nextFloat() * 0.1F;
            this.setColor(tint, tint, tint);
            this.startAlpha = 0.75F;
            this.setSpriteFromAge(sprites);
            this.hasPhysics = false;
        }

        @Override
        public void tick() {
            super.tick();
            this.setSpriteFromAge(sprites);
            this.quadSize = Math.min(0.7F, this.quadSize + 0.02F);
            this.setAlpha(startAlpha * (1.0F - life()));
        }
    }

    /** A glowing ember: arcs under light gravity, flickers, shrinks. */
    public static final class Ember extends Fading {
        Ember(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite) {
            super(level, x, y, z, sprite);
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            this.lifetime = 12 + this.random.nextInt(14);
            this.quadSize = 0.05F + this.random.nextFloat() * 0.04F;
            this.gravity = 0.45F;
            this.friction = 0.94F;
            float g = 0.45F + this.random.nextFloat() * 0.4F;
            this.setColor(1.0F, g, 0.12F + this.random.nextFloat() * 0.1F);
        }

        @Override
        public void tick() {
            super.tick();
            float t = life();
            this.setAlpha((1.0F - t * t) * (0.75F + this.random.nextFloat() * 0.25F));
            this.gCol = Math.max(0.2F, this.gCol - 0.02F);
        }

        @Override
        public int getLightCoords(float a) {
            return FULL_BRIGHT;
        }
    }

    /** A "z" that drifts up and sways over a drowsy NPC. */
    public static final class Drowsy extends Fading {
        private final float sway = this.random.nextFloat() * Mth.TWO_PI;

        Drowsy(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
            super(level, x, y, z, sprite);
            this.lifetime = 30 + this.random.nextInt(10);
            this.quadSize = 0.09F + this.random.nextFloat() * 0.05F;
            this.gravity = 0.0F;
            this.hasPhysics = false;
            this.setColor(0.86F, 0.88F, 1.0F);
        }

        @Override
        public void tick() {
            super.tick();
            this.xd = Mth.sin(this.age * 0.25F + sway) * 0.012;
            this.yd = 0.022;
            float t = life();
            this.setAlpha(t < 0.2F ? t / 0.2F : 1.0F - (t - 0.2F) / 0.8F);
            this.quadSize += 0.002F;
        }
    }

    /** A star circling an entity's head (see {@link com.mcspacewizard.emergentstealth.registry.ESParticles#DIZZY_STAR}). */
    public static final class DizzyStar extends Fading {
        private static final float RADIUS = 0.42F;
        private final int entityId;
        private final float phase;
        private double cx;
        private double cy;
        private double cz;

        DizzyStar(ClientLevel level, double x, double y, double z, int entityId, float phase, int lifetime, TextureAtlasSprite sprite) {
            super(level, x, y, z, sprite);
            this.entityId = entityId;
            this.phase = phase;
            this.cx = x;
            this.cy = y;
            this.cz = z;
            this.lifetime = Math.clamp(lifetime, 5, 400);
            this.quadSize = 0.09F;
            this.hasPhysics = false;
            this.gravity = 0.0F;
            this.setColor(1.0F, 0.86F, 0.35F);
            place();
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
        }

        private void place() {
            Entity entity = this.level.getEntity(entityId);
            if (entity != null) {
                cx = entity.getX();
                cy = entity.getEyeY() + 0.45;
                cz = entity.getZ();
            }
            float angle = phase + this.age * 0.32F;
            setPos(cx + Mth.cos(angle) * RADIUS, cy + Mth.sin(angle * 2.0F) * 0.04, cz + Mth.sin(angle) * RADIUS);
        }

        @Override
        public void tick() {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            if (this.age++ >= this.lifetime) {
                remove();
                return;
            }
            place();
            float t = life();
            this.setAlpha(t > 0.85F ? (1.0F - t) / 0.15F : 1.0F);
        }

        @Override
        public int getLightCoords(float a) {
            return FULL_BRIGHT;
        }
    }

    /** A brief glint: grows, then shrinks away. */
    public static final class Glint extends Fading {
        Glint(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
            super(level, x, y, z, sprite);
            this.lifetime = 8 + this.random.nextInt(6);
            this.hasPhysics = false;
            this.gravity = 0.0F;
            this.quadSize = 0.0F;
            this.setColor(1.0F, 1.0F, 0.92F);
        }

        @Override
        public void tick() {
            super.tick();
            this.quadSize = 0.09F * Mth.sin(life() * Mth.PI);
        }

        @Override
        public int getLightCoords(float a) {
            return FULL_BRIGHT;
        }
    }

    /** A gold ring that grows and fades: an NPC was just tagged. */
    public static final class TagPing extends Fading {
        TagPing(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
            super(level, x, y, z, sprite);
            this.lifetime = 16;
            this.hasPhysics = false;
            this.gravity = 0.0F;
            this.quadSize = 0.3F;
            this.setColor(0.91F, 0.70F, 0.23F);
        }

        @Override
        public void tick() {
            super.tick();
            float t = life();
            this.quadSize = 0.3F + 1.1F * (1.0F - (1.0F - t) * (1.0F - t));
            this.setAlpha(1.0F - t);
        }

        @Override
        public int getLightCoords(float a) {
            return FULL_BRIGHT;
        }
    }

    // ------------------------------------------------------------------ providers

    static ParticleProvider<SimpleParticleType> smokeCore(SpriteSet sprites) {
        return (o, level, x, y, z, xd, yd, zd, random) -> new SmokeCore(level, x, y, z, xd, yd, zd, sprites.get(random));
    }

    static ParticleProvider<SimpleParticleType> steam(SpriteSet sprites) {
        return (o, level, x, y, z, xd, yd, zd, random) -> new Steam(level, x, y, z, xd, yd, zd, sprites);
    }

    static ParticleProvider<SimpleParticleType> ember(SpriteSet sprites) {
        return (o, level, x, y, z, xd, yd, zd, random) -> new Ember(level, x, y, z, xd, yd, zd, sprites.first());
    }

    static ParticleProvider<SimpleParticleType> drowsy(SpriteSet sprites) {
        return (o, level, x, y, z, xd, yd, zd, random) -> new Drowsy(level, x, y, z, sprites.first());
    }

    static ParticleProvider<SimpleParticleType> dizzyStar(SpriteSet sprites) {
        return (o, level, x, y, z, xd, yd, zd, random) -> new DizzyStar(level, x, y, z, (int) Math.round(xd), (float) yd,
                (int) Math.round(zd), sprites.first());
    }

    static ParticleProvider<SimpleParticleType> glint(SpriteSet sprites) {
        return (o, level, x, y, z, xd, yd, zd, random) -> new Glint(level, x, y, z, sprites.first());
    }

    static ParticleProvider<SimpleParticleType> tagPing(SpriteSet sprites) {
        return (o, level, x, y, z, xd, yd, zd, random) -> new TagPing(level, x, y, z, sprites.first());
    }
}
