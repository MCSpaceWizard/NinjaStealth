package com.mcspacewizard.emergentstealth.client.light;

import java.lang.reflect.Method;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.neoforged.fml.ModList;

/**
 * Whether an Iris shader pack is active. Reflection keeps Iris an optional, compile-free dependency:
 * {@code IrisApi.getInstance().isShaderPackInUse()} is Iris's stable public API.
 */
final class ShaderPacks {
    private ShaderPacks() {}

    private static boolean resolved;
    private static @Nullable Object api;
    private static @Nullable Method inUse;

    static boolean inUse() {
        if (!resolved) {
            resolved = true;
            if (ModList.get().isLoaded("iris")) {
                try {
                    Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    api = type.getMethod("getInstance").invoke(null);
                    inUse = type.getMethod("isShaderPackInUse");
                } catch (ReflectiveOperationException | LinkageError e) {
                    EmergentStealth.LOGGER.warn("Iris is installed but its API couldn't be read; assuming no shader pack", e);
                }
            }
        }
        if (api == null || inUse == null) {
            return false;
        }
        try {
            return (Boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
