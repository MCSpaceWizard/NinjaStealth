package com.mcspacewizard.emergentstealth.registry;

import com.mojang.serialization.Codec;
import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ESDataComponents {
    private ESDataComponents() {}

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, EmergentStealth.MODID);

    /** The patrol route a Patrol Baton edits. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> PATROL_ROUTE =
            COMPONENTS.registerComponentType("patrol_route", b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
}
