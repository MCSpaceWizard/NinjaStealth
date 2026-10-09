package com.mcspacewizard.emergentstealth.registry;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.tool.ToolbeltMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Container menus. Screens are bound on the client in {@code ToolClientEvents}. */
public final class ESMenus {
    private ESMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, EmergentStealth.MODID);

    /** The toolbelt's 8 slots. Extra data: the inventory slot the belt sits in. */
    public static final DeferredHolder<MenuType<?>, MenuType<ToolbeltMenu>> TOOLBELT = MENUS.register("toolbelt",
            () -> IMenuTypeExtension.create(ToolbeltMenu::client));
}
