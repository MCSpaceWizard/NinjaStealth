package com.mcspacewizard.emergentstealth.client.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Whether this client wants Emergent Stealth debug data. Only a request: the server still only sends
 * debug values to operators or the singleplayer host.
 */
public final class ClientDebugState {
    private ClientDebugState() {}

    private static volatile boolean enabled;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(value
                    ? "message.emergentstealth.debug.on"
                    : "message.emergentstealth.debug.off"));
        }
    }

    public static void toggle() {
        setEnabled(!enabled);
    }
}
