package com.mcspacewizard.emergentstealth.registry;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Particle types for the stealth tools (design doc 21). Registered from here with a {@link RegisterEvent} so the
 * mod constructor stays untouched. Client providers live in {@code client.tool.ToolParticles}.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class ESParticles {
    private ESParticles() {}

    /** A big, slow, long-lived grey puff: smoke bomb clouds. */
    public static final SimpleParticleType SMOKE_CLOUD = new SimpleParticleType(false);
    /** A pale glittering puff that spreads and fades: blinding powder. */
    public static final SimpleParticleType BLINDING_PUFF = new SimpleParticleType(false);

    // Tool effects (design doc 34 §2). Visual only.

    /** A darker, bigger, longer puff for the heart of a smoke cloud. */
    public static final SimpleParticleType SMOKE_CORE = new SimpleParticleType(false);
    /** A white puff that rises and spreads: a light put out by a water arrow. */
    public static final SimpleParticleType STEAM = new SimpleParticleType(false);
    /** A small glowing ember that arcs and fades: fire arrows, firecracker fuses, a slipping lockpick. */
    public static final SimpleParticleType EMBER = new SimpleParticleType(false);
    /** A drifting "z" over a drowsy NPC (sleep darts). */
    public static final SimpleParticleType DROWSY = new SimpleParticleType(false);
    /**
     * A star circling a dazed head. Sent with count 0 so the "velocity" carries its data: x = the entity id to
     * follow, y = the starting angle (radians), z = the lifetime (ticks).
     */
    public static final SimpleParticleType DIZZY_STAR = new SimpleParticleType(false);
    /** A brief four-point glint: caltrops at night, blinded eyes. */
    public static final SimpleParticleType GLINT = new SimpleParticleType(false);
    /** A gold ring that grows and fades round a newly tagged NPC (sent to the tagger only). */
    public static final SimpleParticleType TAG_PING = new SimpleParticleType(true);

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.PARTICLE_TYPE, helper -> {
            helper.register(EmergentStealth.id("smoke_cloud"), SMOKE_CLOUD);
            helper.register(EmergentStealth.id("blinding_puff"), BLINDING_PUFF);
            helper.register(EmergentStealth.id("smoke_core"), SMOKE_CORE);
            helper.register(EmergentStealth.id("steam"), STEAM);
            helper.register(EmergentStealth.id("ember"), EMBER);
            helper.register(EmergentStealth.id("drowsy"), DROWSY);
            helper.register(EmergentStealth.id("dizzy_star"), DIZZY_STAR);
            helper.register(EmergentStealth.id("glint"), GLINT);
            helper.register(EmergentStealth.id("tag_ring"), TAG_PING);
        });
    }
}
