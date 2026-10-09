package com.mcspacewizard.emergentstealth.tool;

import java.util.ArrayList;
import java.util.List;

import com.mcspacewizard.emergentstealth.registry.ESMenus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The toolbelt's slots over the player's inventory. Every change is written straight back into the belt stack
 * (server side; the client copy follows by sync). The belt's own inventory slot is locked while it's open.
 */
public class ToolbeltMenu extends AbstractContainerMenu {
    public static final int BELT_X = 17;
    public static final int BELT_Y = 20;
    public static final int INVENTORY_Y = 51;
    public static final int HOTBAR_Y = 109;

    private final Player player;
    private final int beltSlot;
    private final ItemStack belt;
    private final SimpleContainer container;
    private boolean loading;

    public static ToolbeltMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        return new ToolbeltMenu(containerId, inventory, data.readVarInt());
    }

    public ToolbeltMenu(int containerId, Inventory inventory, int beltSlot) {
        super(ESMenus.TOOLBELT.get(), containerId);
        this.player = inventory.player;
        this.beltSlot = beltSlot;
        this.belt = inventory.getItem(beltSlot);
        this.container = new SimpleContainer(Toolbelt.SIZE) {
            @Override
            public void setChanged() {
                super.setChanged();
                save();
            }
        };
        loading = true;
        List<ItemStack> contents = Toolbelt.contents(belt);
        for (int i = 0; i < Toolbelt.SIZE; i++) {
            container.setItem(i, contents.get(i));
        }
        loading = false;

        for (int i = 0; i < Toolbelt.SIZE; i++) {
            addSlot(new Slot(container, i, BELT_X + i * 18, BELT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return Toolbelt.accepts(stack);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(inventorySlot(inventory, column + row * 9 + 9, 8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(inventorySlot(inventory, column, 8 + column * 18, HOTBAR_Y));
        }
    }

    private Slot inventorySlot(Inventory inventory, int index, int x, int y) {
        if (index != beltSlot) {
            return new Slot(inventory, index, x, y);
        }
        return new Slot(inventory, index, x, y) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        };
    }

    private void save() {
        if (loading || player.level().isClientSide()) {
            return;
        }
        List<ItemStack> items = new ArrayList<>(Toolbelt.SIZE);
        for (int i = 0; i < Toolbelt.SIZE; i++) {
            items.add(container.getItem(i));
        }
        Toolbelt.store(belt, items);
    }

    /** The inventory slot the open belt sits in. */
    public int beltSlot() {
        return beltSlot;
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        // Number keys and F swap with a hotbar or offhand slot: never with the belt itself.
        if (input == ContainerInput.SWAP && buttonNum == beltSlot) {
            return;
        }
        super.clicked(slotIndex, buttonNum, input, player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        save();
    }

    @Override
    public boolean stillValid(Player player) {
        return !belt.isEmpty() && player.getInventory().getItem(beltSlot) == belt;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        if (slotIndex < Toolbelt.SIZE) {
            if (!moveItemStackTo(stack, Toolbelt.SIZE, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!Toolbelt.accepts(stack) || !moveItemStackTo(stack, 0, Toolbelt.SIZE, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }
}
