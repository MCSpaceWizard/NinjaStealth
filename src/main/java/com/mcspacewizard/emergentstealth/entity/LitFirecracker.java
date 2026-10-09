package com.mcspacewizard.emergentstealth.entity;

import com.mcspacewizard.emergentstealth.registry.ESEntities;
import com.mcspacewizard.emergentstealth.registry.ESItems;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseEvent;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseKind;
import com.mcspacewizard.emergentstealth.stealth.sound.Noises;
import com.mcspacewizard.emergentstealth.tool.ToolEffects;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A firecracker that has landed (design doc 21 §2): a 2 s fizzing fuse, then a string of bangs for 3 s. Every bang
 * is a noise of 24 with no cause, so guards come to the spot, not to the thrower. It hops around a little as it
 * goes off. Rendered as the firecracker item.
 */
public class LitFirecracker extends Entity implements ItemSupplier {
    public static final int FUSE_TICKS = 40;
    public static final int BANG_TICKS = 60;
    public static final float BANG_LOUDNESS = 24.0F;

    private int age;
    private int nextBang = FUSE_TICKS;
    private int bangs;

    public LitFirecracker(EntityType<? extends LitFirecracker> type, Level level) {
        super(type, level);
    }

    public static LitFirecracker create(ServerLevel level, Vec3 pos) {
        LitFirecracker firecracker = new LitFirecracker(ESEntities.LIT_FIRECRACKER.get(), level);
        firecracker.setPos(pos);
        return firecracker;
    }

    /** Bangs so far (tests). */
    public int bangs() {
        return bangs;
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ESItems.FIRECRACKER.get());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = this.getDeltaMovement();
        if (!this.isNoGravity()) {
            motion = motion.add(0.0, -0.04, 0.0);
        }
        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, motion);
        motion = this.getDeltaMovement().scale(0.98);
        if (this.onGround()) {
            motion = motion.multiply(0.6, -0.4, 0.6);
        }
        this.setDeltaMovement(motion);

        if (this.level() instanceof ServerLevel level) {
            age++;
            if (age == 1) {
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.TNT_PRIMED, SoundSource.NEUTRAL, 0.5F, 1.8F);
            }
            if (age >= nextBang && age < FUSE_TICKS + BANG_TICKS) {
                bang(level);
                nextBang = age + 4 + this.random.nextInt(7);
            }
            if (age >= FUSE_TICKS + BANG_TICKS) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.1, getZ(), 6, 0.1, 0.1, 0.1, 0.01);
                this.discard();
            }
        } else if (this.tickCount < FUSE_TICKS && this.tickCount % 2 == 0) {
            // The fuse fizzing (client only; joiners mid-fuse just see a few extra sparks).
            this.level().addParticle(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.2, getZ(), 0.0, 0.02, 0.0);
            this.level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.25, getZ(), 0.0, 0.03, 0.0);
            ToolEffects.fuseSparks(this.level(), this);
        }
    }

    private void bang(ServerLevel level) {
        bangs++;
        Vec3 at = this.position().add(0.0, 0.2, 0.0);
        Noises.emit(level, new NoiseEvent(at, BANG_LOUDNESS, NoiseKind.EXPLOSION, null, null));
        boolean big = this.random.nextInt(3) == 0;
        level.playSound(null, at.x, at.y, at.z, big ? SoundEvents.FIREWORK_ROCKET_LARGE_BLAST : SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.NEUTRAL, 2.5F, 0.9F + this.random.nextFloat() * 0.5F);
        level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, 10, 0.05, 0.05, 0.05, 0.12);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.3);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 4, 0.1, 0.05, 0.1, 0.02);
        ToolEffects.crackle(level, at, this.random);
        if (big) {
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        // Hop.
        this.setDeltaMovement((this.random.nextDouble() - 0.5) * 0.25, 0.18 + this.random.nextDouble() * 0.12,
                (this.random.nextDouble() - 0.5) * 0.25);
        this.needsSync = true;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        age = input.getIntOr("Age", 0);
        nextBang = input.getIntOr("NextBang", FUSE_TICKS);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Age", age);
        output.putInt("NextBang", nextBang);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }
}
