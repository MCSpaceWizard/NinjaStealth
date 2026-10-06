package com.mcspacewizard.emergentstealth.ai.brain;

import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.network.BarkPayload;
import com.mcspacewizard.emergentstealth.registry.ESNetwork;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;


/** Text barks above NPC heads (A-16, design doc 14 §6). Only players close enough to hear get them. */
public final class Barks {
    private Barks() {}

    /** Players within this many blocks see the bark. */
    public static final double RANGE = 16.0;
    /** One NPC never barks more often than this. */
    private static final long MIN_GAP_TICKS = 40;

    public static void say(ServerLevel level, StealthNpc npc, String situation) {
        StealthBrain brain = npc.stealthBrain();
        long now = level.getGameTime();
        if (brain.lastBarkTick != Long.MIN_VALUE && now - brain.lastBarkTick < MIN_GAP_TICKS) {
            return;
        }
        brain.lastBarkTick = now;
        brain.lastBark = situation;
        Vec3 pos = npc.position();
        BarkPayload payload = new BarkPayload(npc.getId(), situation, npc.getRandom().nextInt(1 << 16));
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) <= RANGE * RANGE) {
                ESNetwork.sendIfSupported(player, payload);
            }
        }
    }
}
