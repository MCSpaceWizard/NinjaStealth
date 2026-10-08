package com.mcspacewizard.emergentstealth.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Survival-mode mock players placed in the test level (vanilla's mock server player is creative, and NPCs
 * ignore creative players). Always {@link #remove} them when the test ends.
 */
final class TestPlayers {
    private TestPlayers() {}

    static ServerPlayer spawn(GameTestHelper helper, Vec3 relativePos, float yaw) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "stealth-test"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        // The server default game mode may be creative: make abilities (invulnerability) match survival too.
        player.getAbilities().invulnerable = false;
        player.getAbilities().mayfly = false;
        player.getAbilities().instabuild = false;
        Vec3 abs = helper.absoluteVec(relativePos);
        player.snapTo(abs.x, abs.y, abs.z, yaw, 0.0F);
        player.xo = abs.x;
        player.zo = abs.z;
        return player;
    }

    static void remove(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
