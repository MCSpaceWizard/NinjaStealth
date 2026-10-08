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

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.PARTICLE_TYPE, helper -> {
            helper.register(EmergentStealth.id("smoke_cloud"), SMOKE_CLOUD);
            helper.register(EmergentStealth.id("blinding_puff"), BLINDING_PUFF);
        });
    }
}
