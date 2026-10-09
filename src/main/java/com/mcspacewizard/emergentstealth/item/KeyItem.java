package com.mcspacewizard.emergentstealth.item;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.registry.ESDataComponents;
import com.mcspacewizard.emergentstealth.registry.ESItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A key (design doc 21 §3). Its {@code emergentstealth:key_id} component names the lock it opens; it shows as
 * "Key: &lt;name&gt;". Carrying it anywhere in your inventory is enough. Guards carry keys in their equipment
 * (archetype {@code equipment}, e.g. {@code "body": {"id": "emergentstealth:key", "components":
 * {"emergentstealth:key_id": "gatehouse"}}}).
 */
public class KeyItem extends Item {
    public KeyItem(Properties properties) {
        super(properties);
    }

    /** A key for {@code keyId}. */
    public static ItemStack create(String keyId) {
        ItemStack stack = new ItemStack(ESItems.KEY.get());
        stack.set(ESDataComponents.KEY_ID.get(), keyId);
        return stack;
    }

    /** The key id of a key item, or null for anything else (or a blank key). */
    public static @Nullable String keyId(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(ESDataComponents.KEY_ID.get());
    }

    @Override
    public Component getName(ItemStack itemStack) {
        String id = keyId(itemStack);
        return id == null ? super.getName(itemStack) : Component.translatable("item.emergentstealth.key.named", id);
    }
}
