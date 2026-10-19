package com.fnaf.nightshift.entity;

import com.fnaf.nightshift.ai.AiState;
import com.fnaf.nightshift.ai.AnimatronicAi;
import com.fnaf.nightshift.ai.NoiseManager;
import com.fnaf.nightshift.block.Zone;
import com.fnaf.nightshift.campaign.CampaignState;
import com.fnaf.nightshift.campaign.NightManager;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.registry.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base común de todos los animatrónicos: sincronización de estado, animaciones
 * GeckoLib, ataques configurables, invulnerabilidad y persistencia.
 * La IA vive en {@link AnimatronicAi}; cada subclase ajusta su personalidad.
 */
public abstract class AnimatronicEntity extends HostileEntity implements GeoEntity {
	private static final TrackedData<Integer> AI_STATE = DataTracker.registerData(AnimatronicEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> RUNNING = DataTracker.registerData(AnimatronicEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	public static final String CTRL_ACTION = "action";

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	protected final AnimatronicAi ai;

	private final RawAnimation animIdle, animWalk, animRun, animLook, animDetect, animAttack, animHurt, animSpecial;

	@Nullable
	private BlockPos home;
	/** Sesión de noche a la que pertenece; -1 = invocado para pruebas. */
	private int session = -1;

	protected AnimatronicEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.ai = createAi();
		this.experiencePoints = 0;
		setStepHeight(1.0f);
		setPersistent();
		if (getNavigation() instanceof MobNavigation nav) {
			nav.setCanPathThroughDoors(false);
			nav.setCanEnterOpenDoors(true);
			nav.setCanSwim(true);
		}
		String id = kind().id();
		animIdle = RawAnimation.begin().thenLoop("animation." + id + ".idle");
		animWalk = RawAnimation.begin().thenLoop("animation." + id + ".walk");
		animRun = RawAnimation.begin().thenLoop("animation." + id + ".run");
		animLook = RawAnimation.begin().thenLoop("animation." + id + ".look");
		animDetect = RawAnimation.begin().thenPlay("animation." + id + ".detect");
		animAttack = RawAnimation.begin().thenPlay("animation." + id + ".attack");
		animHurt = RawAnimation.begin().thenPlay("animation." + id + ".hurt");
		animSpecial = RawAnimation.begin().thenPlay("animation." + id + ".special");
	}

	protected AnimatronicAi createAi() {
		return new AnimatronicAi(this);
	}

	public static DefaultAttributeContainer.Builder createAnimatronicAttributes(double speed, double followRange) {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, speed)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, followRange)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.9)
				.add(EntityAttributes.GENERIC_ARMOR, 10.0);
	}

	// ------------------------------------------------------------------
	// Personalidad (sobrescribible)
	// ------------------------------------------------------------------

	public abstract AnimatronicKind kind();

	/** Multiplicador de velocidad al correr respecto a caminar. */
	public abstract double runMultiplier();

	public double walkMultiplier() {
		return 1.0;
	}

	/** Multiplicador de rango de detección. */
	public double detectionScale() {
		return 1.0;
	}

	/** Factor de detección cuando el jugador está a oscuras y sin linterna. */
	public double darknessDetectionFactor(boolean playerInDarkness) {
		return playerInDarkness ? 0.55 : 1.0;
	}

	/** Multiplicador de velocidad dinámico (Freddy es más rápido a oscuras). */
	public double dynamicSpeedFactor() {
		return 1.0;
	}

	public double getHearingMultiplier(NoiseManager.NoiseKind kind) {
		return 1.0;
	}

	/** Zona preferida para patrullar, o null para cualquiera. */
	@Nullable
	public Zone preferredZone() {
		return null;
	}

	public abstract float configuredDamage(ModConfig config);

	/** Sonido característico reproducido al detectar o en eventos especiales. */
	public SoundEvent signatureSound() {
		return ModSounds.SERVO;
	}

	/** Llamado mientras un jugador lo alumbra con la linterna (servidor). */
	public void onFlashlightShine(PlayerEntity player) {
	}

	/** Hook de la IA en estado de reposo. Devuelve true si la subclase gestionó el tick. */
	public boolean customIdleTick() {
		return false;
	}

	/** Hook de la IA para elegir destino de patrulla. Null = comportamiento por defecto. */
	@Nullable
	public BlockPos customPatrolTarget() {
		return null;
	}

	/** Llamado al terminar un ataque o perder el objetivo. */
	public void onChaseEnded(boolean caughtSomeone) {
	}

	/** Velocidad a la que crece su interés por la oficina (0 = no la asalta por su cuenta). */
	public double officeDriveRate() {
		return 1.0;
	}

	/** Puerta de la oficina preferida (dirección desde la oficina hacia el pasillo), o null. */
	@Nullable
	public net.minecraft.util.math.Direction preferredDoorSide() {
		return null;
	}

	/** Persiguiendo a alguien que se encerró en la oficina. true = la subclase lo gestionó. */
	public boolean handleBlockedOfficeDoor(com.fnaf.nightshift.campaign.OfficeManager.OfficeDoor door) {
		return false;
	}

	/** Si es true, persigue sin necesitar línea de visión (embestida de Foxy). */
	public boolean ignoresSightWhileChasing() {
		return false;
	}

	// ------------------------------------------------------------------
	// Agresividad y campaña
	// ------------------------------------------------------------------

	public double getAggression() {
		if (getWorld() instanceof ServerWorld sw) {
			return NightManager.currentAggression(sw.getServer());
		}
		return 1.0;
	}

	@Nullable
	public BlockPos getHome() {
		return home;
	}

	public void setHome(@Nullable BlockPos home) {
		this.home = home == null ? null : home.toImmutable();
	}

	public int getSession() {
		return session;
	}

	public void setSession(int session) {
		this.session = session;
	}

	public AnimatronicAi getAi() {
		return ai;
	}

	public AiState getSyncedState() {
		return AiState.byOrdinal(dataTracker.get(AI_STATE));
	}

	public boolean isRunningSynced() {
		return dataTracker.get(RUNNING);
	}

	public void syncAiState(AiState state, boolean running) {
		if (dataTracker.get(AI_STATE) != state.ordinal()) dataTracker.set(AI_STATE, state.ordinal());
		if (dataTracker.get(RUNNING) != running) dataTracker.set(RUNNING, running);
	}

	public void stun(int ticks) {
		if (!getWorld().isClient) ai.stun(ticks);
	}

	/** Ejecuta el golpe: susto/atrapar durante la noche, daño normal fuera de ella. */
	public void performAttack(PlayerEntity player) {
		if (!(getWorld() instanceof ServerWorld sw)) return;
		ModConfig config = ModConfig.get();
		if (config.jumpscareCatch && player instanceof ServerPlayerEntity sp && NightManager.isParticipant(sw.getServer(), sp)) {
			NightManager.catchPlayer(sp, this);
		} else {
			player.damage(getDamageSources().mobAttack(this), configuredDamage(config));
		}
	}

	// ------------------------------------------------------------------
	// Ciclo de vida
	// ------------------------------------------------------------------

	@Override
	protected void initDataTracker() {
		super.initDataTracker();
		dataTracker.startTracking(AI_STATE, AiState.IDLE.ordinal());
		dataTracker.startTracking(RUNNING, false);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		serverAiTick();
	}

	protected void serverAiTick() {
		ai.tick();
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public void checkDespawn() {
		// Nunca desaparecen por distancia; el NightManager controla su ciclo de vida.
	}

	@Override
	public boolean canBreatheInWater() {
		return true;
	}

	@Override
	public boolean isPushedByFluids() {
		return false;
	}

	@Override
	public int getSafeFallDistance() {
		return 6;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (getWorld().isClient) return false;
		if (ModConfig.get().animatronicsInvulnerable && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (source.getAttacker() instanceof PlayerEntity && hurtCooldown <= 0) {
				hurtCooldown = 20;
				triggerAnim(CTRL_ACTION, "hurt");
				getWorld().playSound(null, getBlockPos(), ModSounds.ANIMATRONIC_HURT, SoundCategory.HOSTILE, 1.0f, 0.9f + random.nextFloat() * 0.2f);
				ai.stun(10);
			}
			return false;
		}
		boolean result = super.damage(source, amount);
		if (result) triggerAnim(CTRL_ACTION, "hurt");
		return result;
	}

	private int hurtCooldown;

	@Override
	public void tick() {
		super.tick();
		if (hurtCooldown > 0) hurtCooldown--;
		// las lámparas titilan cuando pasa un animatrónico con intenciones (señal de peligro)
		if (!getWorld().isClient && age % 30 == 0 && ModConfig.get().animatronicLightInterference
				&& getWorld() instanceof ServerWorld sw) {
			AiState st = ai.getState();
			if ((st == AiState.CHASE || st == AiState.APPROACH || st == AiState.LURK || st == AiState.ATTACK) && random.nextFloat() < 0.7f) {
				com.fnaf.nightshift.block.FlickeringLightBlock.disturb(sw, getBlockPos(), 5);
			}
		}
	}

	@Override
	public boolean tryAttack(net.minecraft.entity.Entity target) {
		if (target instanceof PlayerEntity p) {
			performAttack(p);
			return true;
		}
		return super.tryAttack(target);
	}

	// ------------------------------------------------------------------
	// Sonidos
	// ------------------------------------------------------------------

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(kind() == AnimatronicKind.FOXY && isRunningSynced() ? ModSounds.FOXY_RUN : ModSounds.STEP_METAL,
				// volumen > 1 amplía el alcance: los pasos metálicos se oyen a ~20-30 bloques
				isRunningSynced() ? 1.8f : 1.25f, 0.85f + random.nextFloat() * 0.3f);
	}

	@Nullable
	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.ANIMATRONIC_HURT;
	}

	@Nullable
	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.POWER_OUT;
	}

	@Override
	public int getMinAmbientSoundDelay() {
		return 260;
	}

	// ------------------------------------------------------------------
	// Persistencia
	// ------------------------------------------------------------------

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("FnafSession", session);
		if (home != null) nbt.put("FnafHome", NbtHelper.fromBlockPos(home));
		ai.writeNbt(nbt);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		session = nbt.contains("FnafSession") ? nbt.getInt("FnafSession") : -1;
		home = nbt.contains("FnafHome") ? NbtHelper.toBlockPos(nbt.getCompound("FnafHome")) : null;
		ai.readNbt(nbt);
	}

	// ------------------------------------------------------------------
	// GeckoLib
	// ------------------------------------------------------------------

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 4, this::mainPredicate));
		controllers.add(new AnimationController<>(this, CTRL_ACTION, 2, state -> PlayState.STOP)
				.triggerableAnim("detect", animDetect)
				.triggerableAnim("attack", animAttack)
				.triggerableAnim("hurt", animHurt)
				.triggerableAnim("special", animSpecial));
	}

	protected PlayState mainPredicate(AnimationState<AnimatronicEntity> state) {
		AiState ai = getSyncedState();
		if (ai == AiState.STUNNED || ai == AiState.DETECT || ai == AiState.ATTACK) {
			return state.setAndContinue(state.isMoving() && ai == AiState.ATTACK ? animRun : animIdle);
		}
		if (state.isMoving()) {
			// Correr y caminar son excluyentes: el servidor decide según la velocidad real.
			return state.setAndContinue(isRunningSynced() ? animRun : animWalk);
		}
		if (ai == AiState.SEARCH || ai == AiState.INVESTIGATE) {
			return state.setAndContinue(animLook);
		}
		return state.setAndContinue(animIdle);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	/** Usado por el NightManager para comprobar la pertenencia a la campaña. */
	public boolean belongsTo(CampaignState state) {
		return session >= 0 && session == state.getSession();
	}
}
