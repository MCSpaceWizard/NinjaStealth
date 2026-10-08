package com.mcspacewizard.emergentstealth.action;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The crawl toggle (design doc 17 §1). The server owns the {@link Stance} (synced attachment); both sides apply
 * vanilla's crawl pose from it, so the hitbox, camera height and speed are vanilla's crawl.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class Crawling {
    private Crawling() {}

    public static boolean isCrawling(Player player) {
        return player.getData(ESAttachments.STANCE) == Stance.CRAWLING;
    }

    /** Server: the crawl key was pressed. */
    public static void handleToggle(ToggleCrawlPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            toggle(player);
        }
    }

    public static void toggle(ServerPlayer player) {
        if (isCrawling(player)) {
            if (canStand(player)) {
                setStance(player, Stance.STANDING);
            } else {
                player.sendOverlayMessage(Component.translatable("message.emergentstealth.crawl.no_room"));
            }
        } else if (canCrawl(player)) {
            setStance(player, Stance.CRAWLING);
        }
    }

    public static void setStance(Player player, Stance stance) {
        player.setData(ESAttachments.STANCE, stance);
        applyPose(player);
    }

    /** Crawling makes no sense swimming, climbing, flying, riding or sleeping. */
    private static boolean canCrawl(Player player) {
        return !player.isInWater() && !player.onClimbable() && !player.getAbilities().flying && !player.isPassenger()
                && !player.isSleeping() && !player.isSpectator() && player.isAlive();
    }

    /** Room to stand up: the standing hitbox fits here. */
    public static boolean canStand(Player player) {
        return player.level().noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()).deflate(1.0E-7));
    }

    private static void applyPose(Player player) {
        if (isCrawling(player)) {
            player.setForcedPose(Pose.SWIMMING);
            player.setSprinting(false);
        } else if (player.getForcedPose() == Pose.SWIMMING) {
            player.setForcedPose(null);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide() && isCrawling(player) && !canCrawl(player)) {
            // Swimming, a ladder, flying...: stand up (or stay down only if there's no room).
            if (canStand(player) || player.isInWater()) {
                setStance(player, Stance.STANDING);
            }
        }
        applyPose(player);
    }
}
