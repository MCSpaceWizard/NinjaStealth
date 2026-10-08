package com.mcspacewizard.emergentstealth.client.ui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** UI sounds for Sumi widgets: vanilla clicks and page turns, quiet so they suit paper. */
public final class SumiSounds {
    private SumiSounds() {}

    public static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.6F));
    }

    public static void tick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F, 0.25F));
    }

    public static void page() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.8F));
    }

    public static void stamp() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PUT, 0.9F, 1.0F));
    }
}
