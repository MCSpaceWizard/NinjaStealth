package com.mcspacewizard.emergentstealth.client.action;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.progression.GearStats;
import com.mcspacewizard.emergentstealth.registry.ESDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Shows stealth stats on gear tooltips (design doc 26 §4), e.g. "Footsteps -20%". */
@EventBusSubscriber(modid = EmergentStealth.MODID, value = Dist.CLIENT)
public final class GearTooltips {
    private GearTooltips() {}

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        GearStats stats = event.getItemStack().get(ESDataComponents.STEALTH_STATS.get());
        if (stats == null) {
            stats = GearStats.defaultFor(event.getItemStack());
        }
        if (stats == null) {
            return;
        }
        stats.values().forEach((stat, value) -> {
            int percent = Math.round((stat.additive() ? value : value - 1.0F) * 100.0F);
            // Lower visibility / loudness is good for you: show it green.
            boolean good = percent < 0;
            String sign = percent > 0 ? "+" : "";
            event.getToolTip().add(Component.translatable("stealth_stat.emergentstealth." + stat.getSerializedName())
                    .append(": " + sign + percent + (stat.additive() ? "" : "%"))
                    .withStyle(good ? ChatFormatting.DARK_GREEN : ChatFormatting.RED));
        });
    }
}
