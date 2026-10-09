package com.mcspacewizard.emergentstealth.item;

import java.util.List;
import java.util.Optional;

import com.mcspacewizard.emergentstealth.authoring.StructureViewer;
import com.mcspacewizard.emergentstealth.authoring.Zone;
import com.mcspacewizard.emergentstealth.authoring.ZonePayloads;
import com.mcspacewizard.emergentstealth.authoring.Zones;
import com.mcspacewizard.emergentstealth.registry.ESDataComponents;
import com.mcspacewizard.emergentstealth.registry.ESNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The zone tool (design doc 32 §2). Creative and game masters only.
 * <ul>
 *   <li>Right-click a block: mark a corner. Right-click the opposite corner: a new restricted zone with that box,
 *       which becomes the rope's zone.</li>
 *   <li>Sneak while clicking the second corner: add the box to the rope's zone instead.</li>
 *   <li>Use in the air: open the zone panel (name, rule, hours). Sneak + use in the air drops a marked corner.</li>
 * </ul>
 * While held, nearby zones are drawn as tinted volumes.
 */
public class SurveyorsRopeItem extends Item {
    /** Largest box edge the rope makes, in blocks (a typo in a corner shouldn't cover a whole region). */
    public static final int MAX_EDGE = 256;

    public SurveyorsRopeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!StructureViewer.mayAuthor(serverPlayer)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        BlockPos corner = stack.get(ESDataComponents.ROPE_CORNER.get());
        if (corner == null) {
            stack.set(ESDataComponents.ROPE_CORNER.get(), clicked);
            player.sendOverlayMessage(Component.translatable("message.emergentstealth.rope.corner", clicked.toShortString()));
            return InteractionResult.SUCCESS;
        }
        stack.remove(ESDataComponents.ROPE_CORNER.get());
        BoundingBox box = BoundingBox.fromCorners(corner, clicked);
        if (box.getXSpan() > MAX_EDGE || box.getYSpan() > MAX_EDGE || box.getZSpan() > MAX_EDGE) {
            player.sendOverlayMessage(Component.translatable("message.emergentstealth.rope.too_big", MAX_EDGE));
            return InteractionResult.FAIL;
        }
        player.sendOverlayMessage(addBox(level, stack, box, player.isSecondaryUseActive()));
        return InteractionResult.SUCCESS;
    }

    /**
     * Adds a box: to the rope's zone when {@code extend} and the zone exists, otherwise as a new restricted zone
     * that the rope then edits. Returns what to tell the author. Also used by GameTests.
     */
    public static Component addBox(ServerLevel level, ItemStack rope, BoundingBox box, boolean extend) {
        Zones zones = Zones.get(level);
        Optional<Zone> selected = Optional.ofNullable(rope.get(ESDataComponents.ROPE_ZONE.get())).flatMap(zones::get);
        if (extend && selected.isPresent()) {
            if (selected.get().boxes().size() >= Zone.MAX_BOXES) {
                return Component.translatable("message.emergentstealth.rope.max_boxes", Zone.MAX_BOXES);
            }
            Zone zone = selected.get().withBox(box);
            zones.put(zone);
            return Component.translatable("message.emergentstealth.rope.extended", zone.name(), zone.boxes().size());
        }
        Zone zone = new Zone(zones.nextName(), Zone.Access.RESTRICTED, List.of(box), Optional.empty());
        zones.put(zone);
        rope.set(ESDataComponents.ROPE_ZONE.get(), zone.name());
        return Component.translatable("message.emergentstealth.rope.created", zone.name(),
                box.getXSpan() + "x" + box.getYSpan() + "x" + box.getZSpan());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!StructureViewer.mayAuthor(serverPlayer)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && stack.has(ESDataComponents.ROPE_CORNER.get())) {
            stack.remove(ESDataComponents.ROPE_CORNER.get());
            player.sendOverlayMessage(Component.translatable("message.emergentstealth.rope.corner_dropped"));
            return InteractionResult.SUCCESS;
        }
        Optional<Zone> zone = Optional.ofNullable(stack.get(ESDataComponents.ROPE_ZONE.get())).flatMap(Zones.get(serverLevel)::get);
        if (zone.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.emergentstealth.rope.no_zone"));
            return InteractionResult.FAIL;
        }
        ESNetwork.sendIfSupported(serverPlayer, new ZonePayloads.OpenEditor(zone.get()));
        return InteractionResult.SUCCESS;
    }
}
