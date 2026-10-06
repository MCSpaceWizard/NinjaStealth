package com.mcspacewizard.emergentstealth.entity;

import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.brain.AlertState;
import com.mcspacewizard.emergentstealth.ai.brain.StealthActionGoal;
import com.mcspacewizard.emergentstealth.ai.brain.StealthBrain;
import com.mcspacewizard.emergentstealth.ai.perception.NpcPerception;
import com.mcspacewizard.emergentstealth.ai.perception.PerceptionProfile;
import com.mcspacewizard.emergentstealth.ai.perception.TargetAwareness;
import com.mcspacewizard.emergentstealth.data.Archetype;
import com.mcspacewizard.emergentstealth.debug.NpcDebugInfo;
import com.mcspacewizard.emergentstealth.registry.ESDebugSubscriptions;
import com.mcspacewizard.emergentstealth.registry.ESRegistries;
import com.mcspacewizard.emergentstealth.stealth.light.ExposureModel;

import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.debug.DebugValueSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The single entity type behind every human NPC. Its {@link Archetype} (datapack) decides role, looks,
 * stats, gear and how it perceives. Perception ({@link NpcPerception}, run by the perception scheduler)
 * feeds the {@link StealthBrain}, which picks an alert state that the goals act out.
 */
public class StealthNpc extends PathfinderMob {
    public static final Identifier DEFAULT_ARCHETYPE = EmergentStealth.id("ashigaru");

    private static final EntityDataAccessor<String> DATA_ARCHETYPE =
            SynchedEntityData.defineId(StealthNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_BODY_VARIANT =
            SynchedEntityData.defineId(StealthNpc.class, EntityDataSerializers.INT);

    public static final Identifier DEFAULT_PERCEPTION = EmergentStealth.id("default");

    /** Exposure at the eyes below which an alert guard gets a torch out (L-04). */
    private static final float DARK_FOR_TORCH = 0.25F;

    private final NpcPerception perception = new NpcPerception(this);
    private final StealthBrain brain = new StealthBrain(this);
    /** Whether the off-hand torch was taken out by us (so we put it away again). */
    private boolean carryingSearchTorch;

    public StealthNpc(EntityType<? extends StealthNpc> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.getNavigation().setCanOpenDoors(true);
    }

    public NpcPerception perception() {
        return perception;
    }

    public StealthBrain stealthBrain() {
        return brain;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Archetype.Stats.DEFAULT.maxHealth())
                .add(Attributes.MOVEMENT_SPEED, Archetype.Stats.DEFAULT.movementSpeed())
                .add(Attributes.ATTACK_DAMAGE, Archetype.Stats.DEFAULT.attackDamage())
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ARCHETYPE, DEFAULT_ARCHETYPE.toString());
        builder.define(DATA_BODY_VARIANT, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Combat: vanilla melee follows getTarget(), which the brain only sets while the target is seen.
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, false));
        this.goalSelector.addGoal(2, new StealthActionGoal(this));
        // Idle placeholders until patrols and routines (S5). No "look at nearby player" goal: that would be
        // free information (D-08) - NPCs only turn to what they perceive.
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        brain.tick(level);
        if (this.tickCount % 20 == 0) {
            updateSearchTorch(level);
        }
        super.customServerAiStep(level);
    }

    /** Guards investigating, hunting or searching in the dark take out a torch, and put it away when calm. */
    private void updateSearchTorch(ServerLevel level) {
        AlertState state = brain.state();
        boolean looking = state == AlertState.INVESTIGATING || state == AlertState.HUNTING || state == AlertState.SEARCHING;
        if (looking && isCombatant()) {
            if (!carryingSearchTorch && this.getOffhandItem().isEmpty()
                    && ExposureModel.INSTANCE.exposure(level, this.getEyePosition()) < DARK_FOR_TORCH) {
                this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH));
                carryingSearchTorch = true;
            }
        } else if (carryingSearchTorch && !state.isActive()) {
            if (this.getOffhandItem().is(Items.TORCH)) {
                this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            }
            carryingSearchTorch = false;
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && this.isAlive()) {
            brain.onHurt(level, source);
        }
        return hurt;
    }

    // ------------------------------------------------------------------------------------------------
    // Archetype

    public Identifier getArchetypeId() {
        Identifier id = Identifier.tryParse(this.entityData.get(DATA_ARCHETYPE));
        return id != null ? id : DEFAULT_ARCHETYPE;
    }

    /** Looks the archetype up in this side's registry access (works on the client too: the registry is synced). */
    public Optional<Archetype> getArchetype() {
        return Optional.ofNullable(this.level().registryAccess().lookupOrThrow(ESRegistries.ARCHETYPE).getValue(getArchetypeId()));
    }

    /** Guards fight when alerted; everyone else flees. */
    public boolean isCombatant() {
        return getArchetype().map(a -> a.role().isGuard()).orElse(true);
    }

    /** The NPC's perception profile from its archetype (server registry), or the built-in default. */
    public PerceptionProfile getPerceptionProfile() {
        Identifier id = getArchetype().flatMap(Archetype::perception).orElse(DEFAULT_PERCEPTION);
        PerceptionProfile profile = this.level().registryAccess().lookupOrThrow(ESRegistries.PERCEPTION_PROFILE).getValue(id);
        return profile != null ? profile : PerceptionProfile.DEFAULT;
    }

    public int getBodyVariant() {
        return this.entityData.get(DATA_BODY_VARIANT);
    }

    /** Sets the archetype id only, without applying anything. Use before {@code finalizeSpawn}, which applies it. */
    public void setArchetypeId(Identifier archetypeId) {
        this.entityData.set(DATA_ARCHETYPE, archetypeId.toString());
    }

    /**
     * Switches this NPC to an archetype and applies its stats.
     *
     * @param fresh true when the NPC is newly spawned: also rolls a body variant, equips the archetype's
     *              gear and heals to full. False when re-applying to an existing NPC (e.g. after loading).
     */
    public void setArchetype(Identifier archetypeId, boolean fresh) {
        setArchetypeId(archetypeId);
        Optional<Archetype> archetype = getArchetype();
        if (archetype.isEmpty()) {
            EmergentStealth.LOGGER.warn("Stealth NPC {} has unknown archetype {}", this.getUUID(), archetypeId);
            return;
        }

        Archetype.Stats stats = archetype.get().stats();
        setBaseValue(Attributes.MAX_HEALTH, stats.maxHealth());
        setBaseValue(Attributes.MOVEMENT_SPEED, stats.movementSpeed());
        setBaseValue(Attributes.ATTACK_DAMAGE, stats.attackDamage());

        if (fresh) {
            int bodies = archetype.get().bodies().size();
            this.entityData.set(DATA_BODY_VARIANT, bodies > 0 ? this.getRandom().nextInt(bodies) : 0);
            for (Map.Entry<EquipmentSlot, ItemStackTemplate> entry : archetype.get().equipment().entrySet()) {
                this.setItemSlot(entry.getKey(), entry.getValue().create());
            }
            this.setHealth(this.getMaxHealth());
        }
    }

    private void setBaseValue(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData spawnData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        setArchetype(getArchetypeId(), true);
        return data;
    }

    // ------------------------------------------------------------------------------------------------
    // Persistence

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Archetype", this.entityData.get(DATA_ARCHETYPE));
        output.putInt("BodyVariant", getBodyVariant());
        brain.save(output);
        output.putBoolean("SearchTorch", carryingSearchTorch);
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        brain.load(input);
        carryingSearchTorch = input.getBooleanOr("SearchTorch", false);
        this.entityData.set(DATA_BODY_VARIANT, input.getIntOr("BodyVariant", 0));
        input.getString("Archetype").ifPresent(value -> {
            Identifier id = Identifier.tryParse(value);
            if (id != null) {
                setArchetype(id, false);
            }
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Debug

    @Override
    public void registerDebugValues(ServerLevel level, DebugValueSource.Registration registration) {
        super.registerDebugValues(level, registration);
        registration.register(ESDebugSubscriptions.NPC.get(), this::createDebugInfo);
    }

    private NpcDebugInfo createDebugInfo() {
        Optional<Archetype> archetype = getArchetype();
        PerceptionProfile profile = getPerceptionProfile();
        java.util.UUID focusId = brain.alertTarget() != null ? brain.alertTarget() : perception.focus();
        TargetAwareness focus = focusId == null ? null : perception.get(focusId);
        return new NpcDebugInfo(
                getArchetypeId().toString(),
                archetype.map(a -> a.role().getSerializedName()).orElse("?"),
                archetype.map(a -> a.faction().toString()).orElse("?"),
                brain.state().name().toLowerCase(java.util.Locale.ROOT),
                perception.tier(),
                focus == null ? 0.0F : focus.awareness(),
                new NpcDebugInfo.Cones(profile.central().halfAngle(), profile.central().range(),
                        profile.peripheral().halfAngle(), profile.peripheral().range(), profile.verticalHalfAngle()),
                Optional.ofNullable(focus == null ? null : focus.lastKnownPos()),
                perception.debugRays().stream().map(r -> new NpcDebugInfo.Ray(r.point(), r.transmittance())).toList());
    }
}
