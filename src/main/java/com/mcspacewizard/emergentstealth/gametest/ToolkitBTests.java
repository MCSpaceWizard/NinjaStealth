package com.mcspacewizard.emergentstealth.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.action.BodyState;
import com.mcspacewizard.emergentstealth.ai.brain.AlertState;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoute;
import com.mcspacewizard.emergentstealth.entity.FireArrow;
import com.mcspacewizard.emergentstealth.entity.SleepDart;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.entity.WaterArrow;
import com.mcspacewizard.emergentstealth.item.KeyItem;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.registry.ESBlocks;
import com.mcspacewizard.emergentstealth.registry.ESEntities;
import com.mcspacewizard.emergentstealth.registry.ESItems;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseEvent;
import com.mcspacewizard.emergentstealth.tool.ArrowEffects;
import com.mcspacewizard.emergentstealth.tool.Darts;
import com.mcspacewizard.emergentstealth.tool.SpyglassTagging;
import com.mcspacewizard.emergentstealth.tool.SpyglassTags;
import com.mcspacewizard.emergentstealth.world.lock.Lockpicking;
import com.mcspacewizard.emergentstealth.world.lock.Locks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Beta toolkit, part B (design doc 21 §4: tests 4, 5 and 6, plus fire arrows, lockpicking and tagging).
 * Registers itself, separately from ESGameTests. Arena: 9 x 6 x 26, stone floor at y=0.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class ToolkitBTests {
    private ToolkitBTests() {}

    private static final Identifier ARENA = EmergentStealth.id("arena");

    private record Spec(Consumer<GameTestHelper> function, int maxTicks) {}

    private static final Map<String, Spec> TESTS = new LinkedHashMap<>();

    static {
        TESTS.put("toolkit/water_arrow_snuffs_torch", new Spec(ToolkitBTests::waterArrowSnuffsTorch, 100));
        TESTS.put("toolkit/fire_arrow_relights_torch", new Spec(ToolkitBTests::fireArrowRelightsTorch, 100));
        TESTS.put("toolkit/sleep_dart_knocks_out_after_delay", new Spec(ToolkitBTests::sleepDartKnocksOut, 200));
        TESTS.put("toolkit/locked_door_needs_key", new Spec(ToolkitBTests::lockedDoorNeedsKey, 100));
        TESTS.put("toolkit/guard_with_key_walks_through", new Spec(ToolkitBTests::guardWithKeyWalksThrough, 400));
        TESTS.put("toolkit/guard_without_key_blocked", new Spec(ToolkitBTests::guardWithoutKeyBlocked, 400));
        TESTS.put("toolkit/lockpick_opens_once", new Spec(ToolkitBTests::lockpickOpensOnce, 200));
        TESTS.put("toolkit/spyglass_tags_npc", new Spec(ToolkitBTests::spyglassTagsNpc, 100));
    }

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        for (Map.Entry<String, Spec> test : TESTS.entrySet()) {
            event.register(Registries.TEST_FUNCTION, EmergentStealth.id(test.getKey()), () -> test.getValue().function());
        }
    }

    @SubscribeEvent
    static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(EmergentStealth.id("toolkit_b"));
        for (Map.Entry<String, Spec> test : TESTS.entrySet()) {
            Identifier id = EmergentStealth.id(test.getKey());
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(environment, ARENA, test.getValue().maxTicks(), 0, true)));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Helpers

    /** A closed oak door at {@code lower} (and the block above). */
    static void door(GameTestHelper helper, BlockPos lower) {
        helper.setBlock(lower, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(lower.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    static boolean isOpen(GameTestHelper helper, BlockPos lower) {
        BlockState state = helper.getBlockState(lower);
        return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.OPEN);
    }

    /** Right-clicks the block at {@code relative} as the player would (fires the interaction events). */
    static void rightClick(GameTestHelper helper, ServerPlayer player, BlockPos relative, InteractionHand hand) {
        BlockPos abs = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getItemInHand(hand), hand, hit);
    }

    // ------------------------------------------------------------------------------------------------
    // Arrows

    static void waterArrowSnuffsTorch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos torch = new BlockPos(4, 1, 8);
        helper.setBlock(torch, Blocks.TORCH);
        List<NoiseEvent> noises = SoundTests.capture(helper, noise -> true);
        Vec3 from = helper.absoluteVec(new Vec3(4.5, 4.5, 8.5));
        WaterArrow arrow = new WaterArrow(level, from.x, from.y, from.z, new ItemStack(ESItems.WATER_ARROW.get()));
        arrow.setDeltaMovement(0.0, -1.2, 0.0);
        level.addFreshEntity(arrow);
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(torch).is(ESBlocks.UNLIT_TORCH.get()), "The torch should be put out");
            helper.assertTrue(arrow.isRemoved(), "A water arrow is used up on impact");
            helper.assertTrue(noises.size() == 1 && noises.getFirst().loudness() == ArrowEffects.SPLASH_NOISE
                    && noises.getFirst().cause() == null, "One unattributed splash noise of 2, got " + noises);
        });
    }

    static void fireArrowRelightsTorch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos torch = new BlockPos(4, 1, 8);
        helper.setBlock(torch, ESBlocks.UNLIT_TORCH.get());
        Vec3 from = helper.absoluteVec(new Vec3(4.5, 4.5, 8.5));
        FireArrow arrow = new FireArrow(level, from.x, from.y, from.z, new ItemStack(ESItems.FIRE_ARROW.get()));
        arrow.setDeltaMovement(0.0, -1.2, 0.0);
        level.addFreshEntity(arrow);
        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockState(torch).is(Blocks.TORCH), "The torch should be relit"));
    }

    // ------------------------------------------------------------------------------------------------
    // Sleep darts

    static void sleepDartKnocksOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(Darts.knocksOut(AlertState.UNAWARE) && Darts.knocksOut(AlertState.SEARCHING) && !Darts.knocksOut(AlertState.COMBAT),
                "Darts knock out unless the NPC is fighting");
        StealthNpc npc = SoundTests.listener(helper, new Vec3(4.5, 1, 9.5), 180.0F);
        Vec3 from = helper.absoluteVec(new Vec3(4.5, 2.0, 5.5));
        SleepDart dart = new SleepDart(level, from.x, from.y, from.z, new ItemStack(ESItems.SLEEP_DART.get()));
        dart.setDeltaMovement(0.0, 0.0, 1.5);
        level.addFreshEntity(dart);
        float health = npc.getHealth();
        long[] hitAt = {-1};
        helper.onEachTick(() -> {
            if (hitAt[0] < 0 && Darts.isStaggered(npc)) {
                hitAt[0] = level.getGameTime();
            }
            if (hitAt[0] >= 0 && level.getGameTime() < hitAt[0] + Darts.STAGGER_TICKS - 1) {
                helper.assertTrue(!npc.isBody(), "Still staggering, not out yet, " + (level.getGameTime() - hitAt[0]) + " ticks after the hit");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hitAt[0] >= 0, "Waiting for the dart to hit");
            helper.assertTrue(npc.getBodyState() == BodyState.UNCONSCIOUS, "Should be knocked out after the stagger");
            helper.assertTrue(level.getGameTime() - hitAt[0] >= Darts.STAGGER_TICKS, "Knocked out only after the 3 s stagger");
            helper.assertTrue(npc.getHealth() == health, "A dart does no damage");
            helper.assertTrue(!Darts.isStaggered(npc), "The stagger is cleared");
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Locks

    static void lockedDoorNeedsKey(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NavigationTests.wallAcross(helper, 8, 3);
        BlockPos door = new BlockPos(4, 1, 8);
        door(helper, door);
        helper.assertTrue(Locks.lock(level, helper.absolutePos(door.above()), "test_gate", 1), "A door can be locked (via its upper half)");
        helper.assertTrue(Locks.isLocked(level, helper.absolutePos(door)) && Locks.isLocked(level, helper.absolutePos(door.above())),
                "Both halves report the lock");
        ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 1, 6.5), 0.0F);
        try {
            rightClick(helper, player, door, InteractionHand.MAIN_HAND);
            helper.assertTrue(!isOpen(helper, door), "Without the key the door stays shut");
            // A key anywhere in the inventory is enough; the hand stays empty.
            player.getInventory().setItem(20, KeyItem.create("wrong_key"));
            rightClick(helper, player, door, InteractionHand.MAIN_HAND);
            helper.assertTrue(!isOpen(helper, door), "The wrong key doesn't open it");
            player.getInventory().setItem(21, KeyItem.create("test_gate"));
            rightClick(helper, player, door, InteractionHand.MAIN_HAND);
            helper.assertTrue(isOpen(helper, door), "With the key the door opens");
            helper.assertTrue(KeyItem.create("test_gate").getHoverName().getString().contains("test_gate"), "Keys are named after their lock");
        } finally {
            TestPlayers.remove(player);
        }
        helper.succeed();
    }

    /** Wall at z=10 with a locked door at x=4; a guard patrols from z=4 to z=17. */
    static StealthNpc lockedRouteGuard(GameTestHelper helper, boolean withKey) {
        NavigationTests.wallAcross(helper, 10, 3);
        BlockPos door = new BlockPos(4, 1, 10);
        door(helper, door);
        Locks.lock(helper.getLevel(), helper.absolutePos(door), "barracks", 1);
        StealthNpc npc = NavigationTests.patroller(helper, new BlockPos(4, 1, 4), PatrolRoute.Mode.PINGPONG,
                NavigationTests.at(4, 1, 4), NavigationTests.at(4, 1, 17));
        if (withKey) {
            npc.setItemSlot(EquipmentSlot.OFFHAND, KeyItem.create("barracks"));
        }
        return npc;
    }

    static void guardWithKeyWalksThrough(GameTestHelper helper) {
        StealthNpc npc = lockedRouteGuard(helper, true);
        BlockPos door = new BlockPos(4, 1, 10);
        double goalZ = helper.absoluteVec(new Vec3(0, 0, 15)).z;
        helper.succeedWhen(() -> {
            helper.assertTrue(npc.getZ() > goalZ, "A guard with the key should get through the locked door" + NavigationTests.describe(helper, npc));
            helper.assertTrue(!isOpen(helper, door), "and close it behind him");
            helper.assertTrue(Locks.isLocked(helper.getLevel(), helper.absolutePos(door)), "The door stays locked");
        });
    }

    static void guardWithoutKeyBlocked(GameTestHelper helper) {
        StealthNpc npc = lockedRouteGuard(helper, false);
        BlockPos door = new BlockPos(4, 1, 10);
        double wallZ = helper.absoluteVec(new Vec3(0, 0, 10)).z;
        helper.onEachTick(() -> {
            helper.assertTrue(!isOpen(helper, door), "A guard without the key must not open the locked door" + NavigationTests.describe(helper, npc));
            helper.assertTrue(npc.getZ() < wallZ, "and must stay on his side" + NavigationTests.describe(helper, npc));
        });
        helper.runAtTickTime(300, () -> {
            // The same guard, handed the key by command (attachment), now gets through.
            npc.setData(ESAttachments.NPC_KEYS, List.of("barracks"));
            helper.assertTrue(Locks.hasKey(npc, "barracks"), "Keys can also be carried as ids (/es key npc)");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Lockpicking

    static void lockpickOpensOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NavigationTests.wallAcross(helper, 8, 3);
        BlockPos door = new BlockPos(4, 1, 8);
        door(helper, door);
        BlockPos absDoor = helper.absolutePos(door);
        Locks.lock(level, absDoor, "vault", 3);
        List<NoiseEvent> noises = SoundTests.capture(helper, noise -> true);
        ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 1, 6.5), 0.0F);
        ItemStack pick = new ItemStack(ESItems.LOCKPICK.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, pick);
        // Using the pick on the door starts a session (and doesn't open the door).
        rightClick(helper, player, door, InteractionHand.MAIN_HAND);
        helper.assertTrue(Lockpicking.session(player) != null, "Using a lockpick on a locked door starts the minigame");
        helper.assertTrue(!isOpen(helper, door), "Starting to pick doesn't open the door");
        List<Integer> progress = new ArrayList<>();
        helper.runAfterDelay(5, () -> {
            progress.add(Lockpicking.click(player, false));
            helper.assertTrue(noises.size() == 1 && noises.getFirst().loudness() == Lockpicking.MISS_NOISE
                    && player.getUUID().equals(noises.getFirst().cause()), "A miss clicks (noise 4, gives the picker away), got " + noises);
            helper.assertTrue(pick.getDamageValue() <= 1, "A miss wears the pick by at most one");
            progress.add(Lockpicking.click(player, true)); // too soon: rate-limited, ignored
        });
        helper.runAfterDelay(10, () -> progress.add(Lockpicking.click(player, true)));
        helper.runAfterDelay(15, () -> progress.add(Lockpicking.click(player, true)));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(!isOpen(helper, door), "Two pins aren't enough");
            progress.add(Lockpicking.click(player, true));
        });
        helper.runAfterDelay(22, () -> {
            try {
                helper.assertTrue(progress.equals(List.of(0, 0, 1, 2, Lockpicking.PINS)), "Progress should go 0,0(ignored),1,2,3, got " + progress);
                helper.assertTrue(isOpen(helper, door), "Three pins open the door");
                helper.assertTrue(Lockpicking.session(player) == null, "The session ends");
                helper.assertTrue(Locks.isLocked(level, absDoor), "Picking opens it once; the lock stays");
                BlockState open = level.getBlockState(absDoor);
                ((DoorBlock) open.getBlock()).setOpen(player, level, open, absDoor, false);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                rightClick(helper, player, door, InteractionHand.MAIN_HAND);
                helper.assertTrue(!isOpen(helper, door), "Once closed it's locked again");
            } finally {
                TestPlayers.remove(player);
            }
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Spyglass tagging

    static void spyglassTagsNpc(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StealthNpc npc = SoundTests.listener(helper, new Vec3(4.5, 1, 12.5), 180.0F);
        ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 1, 2.5), 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPYGLASS));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        helper.assertTrue(player.isScoping(), "Setup: scoping");
        helper.assertTrue(SpyglassTagging.lookedAt(player) == npc, "The scope ray finds the NPC");
        long start = level.getGameTime();
        helper.onEachTick(() -> SpyglassTagging.tick(player));
        helper.runAfterDelay(SpyglassTagging.FOCUS_TICKS / 2, () -> helper.assertTrue(SpyglassTagging.tags(player).isEmpty(),
                "Half a second isn't enough to tag"));
        helper.succeedWhen(() -> {
            List<SpyglassTags.Tag> tags = SpyglassTagging.tags(player);
            helper.assertTrue(tags.size() == 1 && tags.getFirst().entityId() == npc.getId(), "The NPC should be tagged, got " + tags);
            helper.assertTrue(level.getGameTime() - start >= SpyglassTagging.FOCUS_TICKS, "Only after a second of looking");
            helper.assertTrue(tags.getFirst().expiresAt() - level.getGameTime() > SpyglassTagging.TAG_TICKS - 40, "Tags last 60 s");
            // At most three tags: a fourth replaces the oldest.
            SpyglassTags many = SpyglassTags.EMPTY;
            for (int id = 1; id <= 4; id++) {
                many = SpyglassTagging.tag(many, id, level.getGameTime() + id);
            }
            helper.assertTrue(many.tags().size() == SpyglassTagging.MAX_TAGS && !many.isTagged(1, level.getGameTime()),
                    "At most 3 tags; the oldest goes, got " + many.tags());
            player.stopUsingItem();
            TestPlayers.remove(player);
        });
    }
}
