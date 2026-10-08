package com.mcspacewizard.emergentstealth.gametest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.brain.PoiCause;
import com.mcspacewizard.emergentstealth.ai.perception.NpcPerception;
import com.mcspacewizard.emergentstealth.block.CaltropsBlock;
import com.mcspacewizard.emergentstealth.entity.LitFirecracker;
import com.mcspacewizard.emergentstealth.entity.SmokeCloud;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.entity.ThrownItem;
import com.mcspacewizard.emergentstealth.registry.ESBlocks;
import com.mcspacewizard.emergentstealth.registry.ESEntities;
import com.mcspacewizard.emergentstealth.registry.ESItems;
import com.mcspacewizard.emergentstealth.stealth.LightSampler;
import com.mcspacewizard.emergentstealth.stealth.SightRay;
import com.mcspacewizard.emergentstealth.stealth.SmokeVolumes;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseEvent;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseKind;
import com.mcspacewizard.emergentstealth.tool.ActiveTool;
import com.mcspacewizard.emergentstealth.tool.Blinding;
import com.mcspacewizard.emergentstealth.tool.CaltropsItem;
import com.mcspacewizard.emergentstealth.tool.PebbleItem;
import com.mcspacewizard.emergentstealth.tool.SmokeBombItem;
import com.mcspacewizard.emergentstealth.tool.Toolkit;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Beta toolkit, part A (design doc 21 §4: tests 1, 2, 3, 7 and 8, plus the pebble and smoke expiry). Registers
 * itself, separately from ESGameTests. Arena: 9 x 6 x 26, stone floor at y=0, open air above. Yaw 0 faces +z.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class ToolkitATests {
    private ToolkitATests() {}

    private static final Identifier ARENA = EmergentStealth.id("arena");
    private static final LightSampler FULL_LIGHT = (level, point) -> 1.0F;

    private record Spec(Consumer<GameTestHelper> function, int maxTicks) {}

    private static final Map<String, Spec> TESTS = new LinkedHashMap<>();

    static {
        TESTS.put("toolkit/smoke_blocks_sight", new Spec(ToolkitATests::smokeBlocksSight, 100));
        TESTS.put("toolkit/smoke_expires", new Spec(ToolkitATests::smokeExpires, 100));
        TESTS.put("toolkit/firecracker_draws_guard", new Spec(ToolkitATests::firecrackerDrawsGuard, 400));
        TESTS.put("toolkit/blinding_cone_only", new Spec(ToolkitATests::blindingConeOnly, 100));
        TESTS.put("toolkit/caltrops_slow_and_hurt", new Spec(ToolkitATests::caltropsSlowAndHurt, 100));
        TESTS.put("toolkit/quick_use_keeps_held_item", new Spec(ToolkitATests::quickUseKeepsHeldItem, 100));
        TESTS.put("toolkit/pebble_noise_no_trace", new Spec(ToolkitATests::pebbleNoiseNoTrace, 100));
    }

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        for (Map.Entry<String, Spec> test : TESTS.entrySet()) {
            event.register(Registries.TEST_FUNCTION, EmergentStealth.id(test.getKey()), () -> test.getValue().function());
        }
    }

    @SubscribeEvent
    static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(EmergentStealth.id("toolkit_a"));
        for (Map.Entry<String, Spec> test : TESTS.entrySet()) {
            Identifier id = EmergentStealth.id(test.getKey());
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(environment, ARENA, test.getValue().maxTicks(), 0, true)));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Helpers

    static StealthNpc guard(GameTestHelper helper, Vec3 relative, float yaw, boolean ai) {
        StealthNpc npc = helper.spawn(ESEntities.STEALTH_NPC.get(), relative);
        npc.setArchetype(EmergentStealth.id("ashigaru"), true);
        npc.setNoAi(!ai);
        npc.setYRot(yaw);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setXRot(0.0F);
        npc.setHome(npc.blockPosition(), yaw);
        return npc;
    }

    static NpcPerception.Sight sight(GameTestHelper helper, StealthNpc npc, ServerPlayer player) {
        return NpcPerception.computeSight(helper.getLevel(), npc.getPerceptionProfile(), npc.getEyePosition(),
                npc.getYHeadRot(), npc.getXRot(), player, true, FULL_LIGHT, null);
    }

    /** {@link GameTestHelper#relativePos} is off in 26.1.2; this subtracts the test origin instead. */
    static Vec3 relative(GameTestHelper helper, Vec3 absolute) {
        return absolute.subtract(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)));
    }

    // ------------------------------------------------------------------------------------------------
    // 1. Smoke between a guard and a player blocks sight completely; NPCs inside it are blinded

    static void smokeBlocksSight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StealthNpc watcher = guard(helper, new Vec3(4.5, 1, 3.5), 0.0F, false);
        StealthNpc inside = guard(helper, new Vec3(6.5, 1, 9.5), 180.0F, false);
        ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 1, 14.5), 180.0F);
        SmokeCloud cloud;
        try {
            NpcPerception.Sight before = sight(helper, watcher, player);
            helper.assertTrue(before.visibility() > 0.3F && before.anyClearRay(),
                    "Setup: the guard should see the player 11 blocks ahead in full light, got " + before);
            cloud = SmokeBombItem.pop(level, helper.absoluteVec(new Vec3(4.5, 1.05, 9.0)));
            helper.assertTrue(SmokeVolumes.isInSmoke(level, helper.absoluteVec(new Vec3(4.5, 2.0, 9.0))),
                    "The smoke should block sight from the moment it pops");
            NpcPerception.Sight after = sight(helper, watcher, player);
            helper.assertTrue(after.visibility() == 0.0F && !after.anyClearRay(),
                    "Smoke between them should block sight completely, got " + after);
            helper.assertTrue(SightRay.transmittance(level, watcher.getEyePosition(), player.getEyePosition(), false) == 0.0F,
                    "A sight ray through smoke should have zero transmittance");
            Vec3 aside = helper.absoluteVec(new Vec3(0.5, 2.5, 14.5));
            helper.assertTrue(SightRay.transmittance(level, watcher.getEyePosition(), aside, false) > 0.9F,
                    "A ray that misses the cloud should still be clear");
        } finally {
            TestPlayers.remove(player);
        }
        long popped = level.getGameTime();
        helper.succeedWhen(() -> {
            long now = level.getGameTime();
            helper.assertTrue(inside.isBlinded(now), "The guard standing in the smoke should be blinded");
            helper.assertTrue(!watcher.isBlinded(now), "The guard outside the smoke should not be blinded");
            helper.assertTrue(now - popped < SmokeCloud.DURATION_TICKS && !cloud.isRemoved(), "The cloud should still be there");
        });
    }

    /** Smoke stops blocking once it expires (a short-lived volume, so the test doesn't wait 10 s). */
    static void smokeExpires(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 a = helper.absoluteVec(new Vec3(4.5, 2.5, 3.5));
        Vec3 b = helper.absoluteVec(new Vec3(4.5, 2.5, 14.5));
        SmokeVolumes.Volume volume = SmokeVolumes.add(level, helper.absoluteVec(new Vec3(4.5, 2.5, 9.0)), 2.0F, level.getGameTime() + 5);
        helper.assertTrue(SmokeVolumes.blocksSight(level, a, b), "A fresh volume blocks the ray");
        helper.assertTrue(!SmokeVolumes.blocksSight(level, a, helper.absoluteVec(new Vec3(0.5, 5.5, 3.5))), "A ray that misses is clear");
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(!SmokeVolumes.blocksSight(level, a, b), "Expired smoke no longer blocks sight");
            helper.assertTrue(SmokeVolumes.find(level, volume.id()) == null, "Expired volumes are pruned");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------
    // 2. A firecracker draws a guard to it

    static void firecrackerDrawsGuard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Facing +z; the firecracker goes off 13 blocks behind it, so only hearing can draw it there.
        StealthNpc npc = guard(helper, new Vec3(4.5, 1, 18.5), 0.0F, true);
        Vec3 spot = helper.absoluteVec(new Vec3(4.5, 1.05, 5.5));
        List<NoiseEvent> bangs = SoundTests.capture(helper, noise -> noise.kind() == NoiseKind.EXPLOSION);
        LitFirecracker firecracker = LitFirecracker.create(level, spot);
        level.addFreshEntity(firecracker);
        helper.runAfterDelay(LitFirecracker.FUSE_TICKS - 5, () -> helper.assertTrue(bangs.isEmpty(), "No bang during the 2 s fuse"));
        helper.succeedWhen(() -> {
            helper.assertTrue(!bangs.isEmpty(), "Waiting for the bangs");
            helper.assertTrue(bangs.getFirst().loudness() == LitFirecracker.BANG_LOUDNESS && bangs.getFirst().cause() == null,
                    "Bangs are noise 24 with no cause, got " + bangs.getFirst());
            helper.assertTrue(npc.stealthBrain().cause() == PoiCause.HEARD || npc.distanceToSqr(spot) < 9.0,
                    "The guard should have heard the firecracker, cause " + npc.stealthBrain().cause());
            double distance = Math.sqrt(npc.distanceToSqr(spot));
            helper.assertTrue(distance < 3.5, String.format("The guard should come to the firecracker, %.1f blocks away (state %s)",
                    distance, npc.stealthBrain().state()));
        });
    }

    // ------------------------------------------------------------------------------------------------
    // 3. Blinding powder blinds an NPC in the cone but not one outside it

    static void blindingConeOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StealthNpc ahead = guard(helper, new Vec3(4.5, 1, 7.0), 180.0F, false);
        StealthNpc aside = guard(helper, new Vec3(7.5, 1, 4.5), 270.0F, false);
        StealthNpc far = guard(helper, new Vec3(4.5, 1, 10.5), 180.0F, false);
        ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 1, 4.5), 0.0F);
        try {
            long now = level.getGameTime();
            List<LivingEntity> caught = Blinding.puff(level, player);
            helper.assertTrue(ahead.isBlinded(now) && caught.contains(ahead), "The NPC 2.5 blocks ahead is in the cone and should be blinded");
            helper.assertTrue(!aside.isBlinded(now) && !caught.contains(aside), "The NPC 3 blocks to the side (90°) is outside the cone");
            helper.assertTrue(!far.isBlinded(now) && !caught.contains(far), "The NPC 6 blocks ahead is out of range");
            helper.assertTrue(ahead.isBlinded(now + Blinding.BLIND_TICKS - 1) && !ahead.isBlinded(now + Blinding.BLIND_TICKS),
                    "Blinded for 6 s");
            helper.assertTrue(Blinding.isStaggering(ahead, now), "A blinded NPC staggers");
            helper.assertTrue(ahead.perception().update(level, now, 1, FULL_LIGHT) == 0, "A blinded NPC casts no sight rays");
        } finally {
            TestPlayers.remove(player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // 7. Caltrops slow and hurt whoever steps on them

    static void caltropsSlowAndHurt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int placed = CaltropsItem.scatter(level, helper.absoluteVec(new Vec3(4.7, 1.05, 8.7)));
        helper.assertTrue(placed == 4, "Caltrops should scatter a 2x2 patch, placed " + placed);
        for (BlockPos pos : new BlockPos[] {new BlockPos(4, 1, 8), new BlockPos(5, 1, 8), new BlockPos(4, 1, 9), new BlockPos(5, 1, 9)}) {
            helper.assertBlockPresent(ESBlocks.CALTROPS.get(), pos);
        }
        LivingEntity pig = helper.spawn(EntityType.PIG, new Vec3(4.5, 1, 8.5));
        StealthNpc npc = guard(helper, new Vec3(5.5, 1, 9.5), 0.0F, false);
        LivingEntity bystander = helper.spawn(EntityType.PIG, new Vec3(1.5, 1, 2.5));
        ((net.minecraft.world.entity.Mob) bystander).setNoAi(true);
        helper.succeedWhen(() -> {
            for (LivingEntity victim : new LivingEntity[] {pig, npc}) {
                MobEffectInstance slow = victim.getEffect(MobEffects.SLOWNESS);
                helper.assertTrue(slow != null && slow.getAmplifier() == CaltropsBlock.SLOW_AMPLIFIER,
                        victim.getType().getDescriptionId() + " on caltrops should get Slowness II, got " + slow);
                helper.assertTrue(victim.getHealth() <= victim.getMaxHealth() - CaltropsBlock.DAMAGE_AMOUNT + 0.01F,
                        victim.getType().getDescriptionId() + " on caltrops should take 1 damage, health " + victim.getHealth());
            }
            helper.assertTrue(bystander.getEffect(MobEffects.SLOWNESS) == null && bystander.getHealth() == bystander.getMaxHealth(),
                    "A pig off the caltrops is unharmed");
        });
    }

    // ------------------------------------------------------------------------------------------------
    // 8. Quick use throws the active tool without changing the held item

    static void quickUseKeepsHeldItem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 1, 4.5), 0.0F);
        boolean handedOff = false;
        try {
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
            player.getInventory().setItem(5, new ItemStack(ESItems.SMOKE_BOMB.get(), 4));
            player.getInventory().setItem(20, new ItemStack(ESItems.FIRECRACKER.get(), 2));

            helper.assertTrue(!Toolkit.quickUse(player, 0.0F), "Nothing happens without an active tool");
            helper.assertTrue(!Toolkit.select(player, new ActiveTool(Items.STONE)), "Stone isn't a stealth tool");
            helper.assertTrue(!Toolkit.select(player, new ActiveTool(ESItems.CALTROPS.get())), "Can't select a tool you don't carry");
            helper.assertTrue(Toolkit.select(player, new ActiveTool(ESItems.SMOKE_BOMB.get())), "Selecting a carried tool works");
            helper.assertTrue(Toolkit.activeItem(player) == ESItems.SMOKE_BOMB.get(), "The active tool is the smoke bomb");

            helper.assertTrue(Toolkit.quickUse(player, 0.0F), "Quick use should throw the smoke bomb");
            helper.assertTrue(player.getMainHandItem().is(Items.IRON_SWORD) && player.getMainHandItem().getCount() == 1,
                    "The held item is unchanged, got " + player.getMainHandItem());
            helper.assertTrue(player.getInventory().getItem(5).getCount() == 3, "One smoke bomb was used, left "
                    + player.getInventory().getItem(5));
            List<ThrownItem> thrown = level.getEntitiesOfClass(ThrownItem.class, new AABB(player.position(), player.position()).inflate(4.0),
                    t -> t.getItem().is(ESItems.SMOKE_BOMB.get()));
            helper.assertTrue(thrown.size() == 1 && thrown.getFirst().getOwner() == player, "A thrown smoke bomb is in flight");

            helper.assertTrue(Toolkit.select(player, new ActiveTool(ESItems.FIRECRACKER.get())), "Switch to the firecracker");
            handedOff = true;
            helper.runAfterDelay(12, () -> {
                try {
                    helper.assertTrue(Toolkit.quickUse(player, 1.0F), "After the throw cooldown, quick use throws the firecracker");
                    helper.assertTrue(player.getMainHandItem().is(Items.IRON_SWORD), "Still holding the sword");
                    helper.assertTrue(player.getInventory().getItem(20).getCount() == 1, "One firecracker used from the main inventory");
                } finally {
                    TestPlayers.remove(player);
                }
                helper.succeed();
            });
        } finally {
            if (!handedOff) {
                TestPlayers.remove(player);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Pebble: impact noise 10, no trace

    static void pebbleNoiseNoTrace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<NoiseEvent> impacts = SoundTests.capture(helper, noise -> noise.kind() == NoiseKind.IMPACT);
        Vec3 from = helper.absoluteVec(new Vec3(4.5, 3.0, 8.5));
        ThrownItem pebble = new ThrownItem(level, from.x, from.y, from.z, new ItemStack(ESItems.PEBBLE.get()));
        pebble.setDeltaMovement(0.0, -0.3, 0.0);
        level.addFreshEntity(pebble);
        helper.succeedWhen(() -> {
            helper.assertTrue(pebble.isRemoved() && !impacts.isEmpty(), "Waiting for the pebble to land");
            helper.assertTrue(impacts.getFirst().loudness() == PebbleItem.IMPACT_LOUDNESS && impacts.getFirst().cause() == null,
                    "A pebble makes an impact noise of 10 with no cause, got " + impacts.getFirst());
            List<ItemEntity> left = level.getEntitiesOfClass(ItemEntity.class, new AABB(from, from).inflate(2.0, 4.0, 2.0));
            helper.assertTrue(left.isEmpty(), "A pebble leaves no trace, found " + left);
            helper.assertTrue(relative(helper, impacts.getFirst().pos()).y < 1.5, "The noise is at the landing spot");
        });
    }
}
