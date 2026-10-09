package com.mcspacewizard.emergentstealth.world.lock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.item.KeyItem;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Lock commands (design doc 21 §2), op only. Registered under {@code /emergentstealth} and therefore also
 * {@code /es} (Brigadier merges literal nodes of the same name; this listener runs after the main command
 * tree is registered so the {@code /es} redirect sees the merged node).
 * <ul>
 *   <li>{@code /es lock <pos> <key> [difficulty]}: lock a door/gate/trapdoor/chest/barrel and get its key</li>
 *   <li>{@code /es lock list}: every lock in this dimension</li>
 *   <li>{@code /es unlock <pos>}</li>
 *   <li>{@code /es key give <key>}: a key for yourself</li>
 *   <li>{@code /es key npc <npcs> <key>} / {@code /es key npc <npcs> clear}: NPCs carry (or drop) key ids
 *       without an item in their equipment</li>
 * </ul>
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class LockCommands {
    private LockCommands() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("emergentstealth")
                .then(Commands.literal("lock")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("list").executes(LockCommands::list))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .executes(ctx -> lock(ctx, LockData.Lock.MIN_DIFFICULTY))
                                        .then(Commands.argument("difficulty", IntegerArgumentType.integer(LockData.Lock.MIN_DIFFICULTY, LockData.Lock.MAX_DIFFICULTY))
                                                .executes(ctx -> lock(ctx, IntegerArgumentType.getInteger(ctx, "difficulty")))))))
                .then(Commands.literal("unlock")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(LockCommands::unlock)))
                .then(Commands.literal("key")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("give")
                                .then(Commands.argument("key", StringArgumentType.word()).executes(LockCommands::giveKey)))
                        .then(Commands.literal("npc")
                                .then(Commands.argument("npcs", EntityArgument.entities())
                                        .then(Commands.literal("clear").executes(ctx -> npcKeys(ctx, null)))
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .executes(ctx -> npcKeys(ctx, StringArgumentType.getString(ctx, "key")))))));
        // Merges into the existing /emergentstealth node (the /es alias redirects to that node).
        dispatcher.register(root);
    }

    private static int lock(CommandContext<CommandSourceStack> ctx, int difficulty) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        String key = StringArgumentType.getString(ctx, "key");
        if (!Locks.lock(level, pos, key, difficulty)) {
            source.sendFailure(Component.translatable("commands.emergentstealth.lock.not_lockable", pos.toShortString()));
            return 0;
        }
        if (source.getEntity() instanceof ServerPlayer player && !Locks.hasKey(player, key)) {
            give(player, key);
        }
        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.lock.locked", pos.toShortString(), key, difficulty), true);
        return 1;
    }

    private static int unlock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        if (!Locks.unlock(source.getLevel(), pos)) {
            source.sendFailure(Component.translatable("commands.emergentstealth.lock.not_locked", pos.toShortString()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.lock.unlocked", pos.toShortString()), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<Map.Entry<BlockPos, LockData.Lock>> all = LockData.get(source.getLevel()).all();
        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.lock.list", all.size()), false);
        for (Map.Entry<BlockPos, LockData.Lock> entry : all) {
            String line = "  " + entry.getKey().toShortString() + "  " + source.getLevel().getBlockState(entry.getKey()).getBlock().getName().getString()
                    + "  key " + entry.getValue().key() + "  difficulty " + entry.getValue().difficulty();
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return all.size();
    }

    private static int giveKey(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String key = StringArgumentType.getString(ctx, "key");
        give(player, key);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.key.given", key), false);
        return 1;
    }

    private static int npcKeys(CommandContext<CommandSourceStack> ctx, String key) throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "npcs");
        int count = 0;
        for (Entity entity : targets) {
            if (!(entity instanceof LivingEntity living) || entity instanceof ServerPlayer) {
                continue;
            }
            List<String> keys = new ArrayList<>(living.getData(ESAttachments.NPC_KEYS));
            if (key == null) {
                keys.clear();
            } else if (!keys.contains(key)) {
                keys.add(key);
            }
            living.setData(ESAttachments.NPC_KEYS, List.copyOf(keys));
            count++;
        }
        int done = count;
        ctx.getSource().sendSuccess(() -> key == null
                ? Component.translatable("commands.emergentstealth.key.npc_cleared", done)
                : Component.translatable("commands.emergentstealth.key.npc_given", key, done), true);
        return count;
    }

    private static void give(ServerPlayer player, String key) {
        ItemStack stack = KeyItem.create(key);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
