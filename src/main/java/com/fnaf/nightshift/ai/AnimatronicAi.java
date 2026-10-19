package com.fnaf.nightshift.ai;

import com.fnaf.nightshift.block.Zone;
import com.fnaf.nightshift.campaign.CampaignState;
import com.fnaf.nightshift.campaign.CameraManager;
import com.fnaf.nightshift.campaign.NightManager;
import com.fnaf.nightshift.campaign.OfficeManager;
import com.fnaf.nightshift.campaign.StaminaManager;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.entity.AnimatronicEntity;
import com.fnaf.nightshift.item.FlashlightLightManager;
import com.fnaf.nightshift.registry.ModSounds;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Máquina de estados de los animatrónicos:
 * IDLE → PATROL → (INVESTIGATE | DETECT → CHASE → ATTACK) → SEARCH → REPOSITION.
 * <p>
 * Incluye detección por distancia, campo de visión, línea de visión y luz;
 * reacción a ruidos; pérdida de interés; detección de atascos con
 * recálculo de ruta y, como último recurso, recolocación fuera de la vista
 * de los jugadores; separación entre animatrónicos y respeto del refugio.
 */
public class AnimatronicAi {
	private final AnimatronicEntity mob;

	private AiState state = AiState.IDLE;
	private AiState stateBeforeStun = AiState.PATROL;
	private int stateTicks;
	private int idleDuration = 100;

	@Nullable
	private UUID targetId;
	@Nullable
	private Vec3d lastSeenPos;
	@Nullable
	private Vec3d moveTarget;
	private double moveSpeed = 1.0;
	@Nullable
	private Vec3d searchCenter;
	private int searchDuration;
	private boolean searchAfterChase;
	private int loseSightTicks;
	private int attackCooldown;
	private int stunTicks;
	private int repathTicks;

	private Vec3d stuckCheckPos = Vec3d.ZERO;
	private int stuckChecks;
	private int failedPaths;
	private final Deque<BlockPos> recentNodes = new ArrayDeque<>();

	// --- Oficina vulnerable (1.1) ---
	private double officeDrive;
	@Nullable
	private OfficeManager.OfficeDoor lurkDoor;
	private int lurkDuration;
	private int nextBang;

	public AnimatronicAi(AnimatronicEntity mob) {
		this.mob = mob;
	}

	public AiState getState() {
		return state;
	}

	@Nullable
	public OfficeManager.OfficeDoor getLurkDoor() {
		return state == AiState.LURK || state == AiState.APPROACH ? lurkDoor : null;
	}

	public double getOfficeDrive() {
		return officeDrive;
	}

	/** Usado por pruebas y eventos: fuerza la presión sobre la oficina. */
	public void setOfficeDrive(double value) {
		officeDrive = value;
	}

	@Nullable
	public UUID getTargetId() {
		return targetId;
	}

	private ServerWorld world() {
		return (ServerWorld) mob.getWorld();
	}

	private double aggression() {
		return mob.getAggression();
	}

	public double walkSpeed() {
		return mob.walkMultiplier() * mob.dynamicSpeedFactor() * MathHelper.clamp(0.85 + 0.15 * aggression(), 0.8, 1.3);
	}

	public double runSpeed() {
		return mob.runMultiplier() * mob.dynamicSpeedFactor() * MathHelper.clamp(0.85 + 0.15 * aggression(), 0.8, 1.35);
	}

	// ------------------------------------------------------------------
	// Transiciones
	// ------------------------------------------------------------------

	public void setState(AiState next) {
		if (state == next) return;
		state = next;
		stateTicks = 0;
		stuckChecks = 0;
		failedPaths = 0;
		switch (next) {
			case IDLE -> {
				mob.getNavigation().stop();
				moveTarget = null;
				idleDuration = (int) ((60 + mob.getRandom().nextInt(80)) / Math.max(0.4, aggression()));
			}
			case DETECT -> {
				mob.getNavigation().stop();
				mob.triggerAnim(AnimatronicEntity.CTRL_ACTION, "detect");
				mob.getWorld().playSound(null, mob.getBlockPos(), mob.signatureSound(), SoundCategory.HOSTILE, 1.2f, 1.0f);
				PlayerEntity t = target();
				if (t instanceof ServerPlayerEntity sp) sp.playSound(ModSounds.ALERT_DANGER, SoundCategory.HOSTILE, 0.9f, 1.0f);
			}
			case ATTACK -> {
				mob.getNavigation().stop();
				mob.triggerAnim(AnimatronicEntity.CTRL_ACTION, "attack");
			}
			case STUNNED -> mob.getNavigation().stop();
			default -> {
			}
		}
	}

	public void stun(int ticks) {
		if (state != AiState.STUNNED) stateBeforeStun = state == AiState.ATTACK ? AiState.CHASE : state;
		stunTicks = Math.max(stunTicks, ticks);
		setState(AiState.STUNNED);
	}

	public void hearNoise(Vec3d pos, NoiseManager.NoiseKind kind) {
		if (state == AiState.CHASE || state == AiState.ATTACK || state == AiState.DETECT || state == AiState.STUNNED) return;
		if (isNearShelter(pos, 2)) return;
		searchCenter = pos;
		moveTarget = pos;
		moveSpeed = walkSpeed() * (kind == NoiseManager.NoiseKind.ALARM ? 1.35 : 1.15);
		setState(AiState.INVESTIGATE);
		startMove(pos, moveSpeed);
		if (mob.getRandom().nextInt(3) == 0) {
			mob.getWorld().playSound(null, mob.getBlockPos(), ModSounds.SERVO, SoundCategory.HOSTILE, 0.7f, 1.1f);
		}
	}

	/** Fuerza una persecución inmediata (embestida de Foxy, pruebas). */
	public void forceChase(PlayerEntity player) {
		targetId = player.getUuid();
		lastSeenPos = player.getPos();
		loseSightTicks = 0;
		setState(AiState.CHASE);
	}

	public void reposition(@Nullable Vec3d explicitTarget) {
		moveTarget = explicitTarget != null ? explicitTarget : findRepositionTarget();
		moveSpeed = walkSpeed() * 1.2;
		setState(AiState.REPOSITION);
		if (moveTarget != null) startMove(moveTarget, moveSpeed);
	}

	// ------------------------------------------------------------------
	// Tick principal
	// ------------------------------------------------------------------

	public void tick() {
		stateTicks++;
		if (attackCooldown > 0) attackCooldown--;

		if (state == AiState.STUNNED) {
			if (--stunTicks <= 0) {
				AiState back = stateBeforeStun == AiState.STUNNED ? AiState.PATROL : stateBeforeStun;
				if (back == AiState.CHASE && target() == null) back = AiState.SEARCH;
				setState(back);
			}
			mob.syncAiState(state, false);
			return;
		}

		// Linternas apuntando: cada animatrónico reacciona a su manera.
		if (mob.age % 5 == 0) {
			PlayerEntity shining = FlashlightLightManager.findShiningPlayer(mob, 14);
			if (shining != null) mob.onFlashlightShine(shining);
			if (state == AiState.STUNNED) {
				mob.syncAiState(state, false);
				return;
			}
		}

		// quien acecha en una puerta ya está ocupándose de la oficina (lo ve por el dintel, pero espera o golpea)
		if (mob.age % 5 == 0 && state != AiState.ATTACK && state != AiState.DETECT && state != AiState.LURK) {
			perceive();
		}

		switch (state) {
			case IDLE -> tickIdle();
			case PATROL -> tickPatrol();
			case INVESTIGATE -> tickInvestigate();
			case SEARCH -> tickSearch();
			case DETECT -> tickDetect();
			case CHASE -> tickChase();
			case ATTACK -> tickAttack();
			case REPOSITION -> tickReposition();
			case APPROACH -> tickApproach();
			case LURK -> tickLurk();
			default -> {
			}
		}

		if (mob.age % 20 == 0) updateOfficeDrive();
		assistOfficeDoor();
		if (mob.age % 10 == 0) {
			separate();
			avoidShelter();
		}
		if (mob.age % 20 == 0) checkStuck();

		boolean running = !mob.getNavigation().isIdle() && moveSpeed > walkSpeed() * 1.15;
		mob.syncAiState(state, running);
	}

	// ------------------------------------------------------------------
	// Percepción
	// ------------------------------------------------------------------

	private void perceive() {
		PlayerEntity seen = findVisiblePlayer();
		if (seen == null) return;
		if (state == AiState.CHASE) {
			if (!seen.getUuid().equals(targetId)) {
				PlayerEntity current = target();
				if (current == null || mob.squaredDistanceTo(seen) < mob.squaredDistanceTo(current) * 0.5) {
					targetId = seen.getUuid();
				}
			}
			return;
		}
		targetId = seen.getUuid();
		lastSeenPos = seen.getPos();
		loseSightTicks = 0;
		setState(AiState.DETECT);
	}

	public boolean isValidTarget(@Nullable PlayerEntity p) {
		if (p == null || !p.isAlive() || p.isSpectator() || p.isCreative()) return false;
		if (p.getWorld() != mob.getWorld()) return false;
		CampaignState campaign = CampaignState.get(world().getServer());
		// La oficina ya no es una zona segura: sólo se excluye a los atrapados.
		return !campaign.isCaught(p.getUuid());
	}

	public double detectionRange(PlayerEntity player) {
		double range = ModConfig.get().detectionRange * mob.detectionScale() * MathHelper.clamp(0.7 + 0.3 * aggression(), 0.6, 1.6);
		boolean flashlight = FlashlightLightManager.isHoldingActive(player) != null;
		boolean dark = !flashlight && world().getLightLevel(player.getBlockPos().up()) < 5;
		range *= mob.darknessDetectionFactor(dark);
		if (player.isSneaking()) range *= 0.6;
		if (flashlight) range *= 1.25;
		if (player.isSprinting()) range *= 1.15;
		if (StaminaManager.isExhausted(player.getUuid())) range *= 1.2;
		return range;
	}

	@Nullable
	private PlayerEntity findVisiblePlayer() {
		PlayerEntity best = null;
		double bestDist = Double.MAX_VALUE;
		Vec3d look = mob.getRotationVec(1.0f);
		for (PlayerEntity p : world().getPlayers()) {
			if (!isValidTarget(p)) continue;
			double dist = mob.distanceTo(p);
			double range = detectionRange(p);
			if (dist > range) continue;
			boolean isTarget = p.getUuid().equals(targetId) && state == AiState.CHASE;
			Vec3d to = p.getEyePos().subtract(mob.getEyePos()).normalize();
			boolean inFov = dist < 3.5 || isTarget || look.dotProduct(to) > 0.35;
			if (!inFov || !mob.canSee(p)) continue;
			if (dist < bestDist) {
				bestDist = dist;
				best = p;
			}
		}
		return best;
	}

	@Nullable
	public PlayerEntity target() {
		if (targetId == null) return null;
		PlayerEntity p = world().getPlayerByUuid(targetId);
		return isValidTarget(p) ? p : null;
	}

	// ------------------------------------------------------------------
	// Estados
	// ------------------------------------------------------------------

	private void tickIdle() {
		if (mob.customIdleTick()) return;
		if (stateTicks % 40 == 0) {
			mob.getLookControl().lookAt(mob.getX() + mob.getRandom().nextGaussian() * 4, mob.getEyeY(), mob.getZ() + mob.getRandom().nextGaussian() * 4);
		}
		if (stateTicks >= idleDuration) {
			setState(AiState.PATROL);
		}
	}

	private void tickPatrol() {
		if (moveTarget == null || stateTicks == 1) {
			BlockPos custom = mob.customPatrolTarget();
			moveTarget = custom != null ? Vec3d.ofBottomCenter(custom) : choosePatrolTarget();
			moveSpeed = walkSpeed();
			if (moveTarget == null) {
				setState(AiState.IDLE);
				return;
			}
			startMove(moveTarget, moveSpeed);
		}
		if (arrived(moveTarget, 2.0) || stateTicks > 600) {
			if (mob.getRandom().nextInt(3) == 0) mob.triggerAnim(AnimatronicEntity.CTRL_ACTION, "special");
			moveTarget = null;
			setState(AiState.IDLE);
			return;
		}
		maintainPath();
	}

	private void tickInvestigate() {
		if (moveTarget == null) {
			setState(AiState.PATROL);
			return;
		}
		if (stateTicks == 1) startMove(moveTarget, moveSpeed);
		if (arrived(moveTarget, 2.5) || stateTicks > 400 || (mob.getNavigation().isIdle() && stateTicks > 60)) {
			beginSearch(moveTarget, false);
			return;
		}
		maintainPath();
	}

	public void beginSearch(Vec3d center, boolean afterChase) {
		searchCenter = center;
		searchAfterChase = afterChase;
		searchDuration = (int) (140 + 80 * aggression());
		moveTarget = null;
		setState(AiState.SEARCH);
	}

	private void tickSearch() {
		if (searchCenter == null) {
			setState(AiState.PATROL);
			return;
		}
		if (stateTicks >= searchDuration) {
			// Pérdida de interés
			mob.onChaseEnded(false);
			if (state != AiState.SEARCH) return;
			if (searchAfterChase) reposition(null);
			else setState(AiState.PATROL);
			return;
		}
		if (moveTarget == null || arrived(moveTarget, 1.5) || mob.getNavigation().isIdle()) {
			if (stateTicks % 25 == 0 || moveTarget == null) {
				Vec3d p = randomPointNear(searchCenter, 7);
				if (p != null) {
					moveTarget = p;
					moveSpeed = walkSpeed();
					startMove(p, moveSpeed);
				}
			}
		}
	}

	private void tickDetect() {
		PlayerEntity t = target();
		if (t != null) mob.getLookControl().lookAt(t, 60, 60);
		int freeze = Math.max(6, (int) (16 / Math.max(0.5, aggression())));
		if (stateTicks >= freeze) {
			if (t == null) {
				beginSearch(lastSeenPos != null ? lastSeenPos : mob.getPos(), true);
			} else {
				setState(AiState.CHASE);
			}
		}
	}

	private void tickChase() {
		PlayerEntity t = target();
		if (t == null) {
			mob.onChaseEnded(false);
			if (state == AiState.CHASE) beginSearch(lastSeenPos != null ? lastSeenPos : mob.getPos(), true);
			return;
		}
		boolean visible = mob.ignoresSightWhileChasing() || mob.canSee(t);
		if (visible) {
			lastSeenPos = t.getPos();
			loseSightTicks = 0;
		} else {
			loseSightTicks++;
		}
		int patience = (int) (50 + 50 * aggression());
		if (loseSightTicks > patience) {
			mob.onChaseEnded(false);
			if (state == AiState.CHASE) beginSearch(lastSeenPos, true);
			return;
		}

		moveSpeed = runSpeed() * (StaminaManager.isExhausted(t.getUuid()) ? 1.1 : 1.0);
		mob.getLookControl().lookAt(t, 30, 30);
		// El objetivo se refugió en la oficina: si la puerta más cercana está cerrada, acecha en ella.
		CampaignState officeState = CampaignState.get(world().getServer());
		if (officeState.isInShelter(t)) {
			OfficeManager.OfficeDoor door = OfficeManager.nearestDoor(world(), officeState, mob.getBlockPos(), 4.5);
			if (door != null && !OfficeManager.isOpen(world(), door)) {
				if (!mob.handleBlockedOfficeDoor(door)) startLurk(door);
				return;
			}
		}
		if (--repathTicks <= 0) {
			repathTicks = 8;
			Vec3d dest = visible ? t.getPos() : lastSeenPos;
			if (dest != null) startMove(dest, moveSpeed);
		}

		double reach = mob.getWidth() * 2.0 + t.getWidth() + 0.6;
		if (mob.squaredDistanceTo(t) <= reach * reach && attackCooldown <= 0 && mob.canSee(t)) {
			setState(AiState.ATTACK);
		}
	}

	private void tickAttack() {
		PlayerEntity t = target();
		if (t != null) mob.getLookControl().lookAt(t, 90, 90);
		if (stateTicks == 8 && t != null) {
			double reach = mob.getWidth() * 2.0 + t.getWidth() + 1.0;
			if (mob.squaredDistanceTo(t) <= reach * reach && mob.canSee(t)) {
				mob.swingHand(net.minecraft.util.Hand.MAIN_HAND);
				mob.performAttack(t);
				if (!isValidTarget(t)) {
					// Lo atrapó: se retira para no acampar en el mismo sitio.
					attackCooldown = 60;
					mob.onChaseEnded(true);
					if (state == AiState.ATTACK) reposition(null);
					return;
				}
			}
		}
		if (stateTicks >= 18) {
			attackCooldown = (int) (30 / Math.max(0.5, aggression()));
			setState(target() != null ? AiState.CHASE : AiState.SEARCH);
			if (state == AiState.SEARCH) beginSearch(mob.getPos(), true);
		}
	}

	private void tickReposition() {
		if (moveTarget == null) {
			moveTarget = findRepositionTarget();
			if (moveTarget == null) {
				setState(AiState.IDLE);
				return;
			}
		}
		if (stateTicks == 1) startMove(moveTarget, moveSpeed);
		if (arrived(moveTarget, 2.0) || stateTicks > 320) {
			moveTarget = null;
			setState(AiState.IDLE);
			return;
		}
		maintainPath();
	}

	// ------------------------------------------------------------------
	// Oficina vulnerable
	// ------------------------------------------------------------------

	/**
	 * La presión sobre la oficina crece con el tiempo mientras patrulla, más
	 * rápido si hay jugadores dentro y aún más si alguien está distraído
	 * mirando las cámaras. Al llenarse, se dirige a una puerta de la oficina
	 * por las rutas normales del mapa (nunca se teletransporta).
	 */
	private void updateOfficeDrive() {
		if (state != AiState.IDLE && state != AiState.PATROL) return;
		if (mob.officeDriveRate() <= 0 || mob.getSession() < 0) return;
		CampaignState campaign = CampaignState.get(world().getServer());
		if (!campaign.getPhase().isActive() || campaign.getGraceTicks() > 0) return;
		int inOffice = OfficeManager.playersInOffice(world(), campaign).size();
		double rate = aggression() * mob.officeDriveRate() * com.fnaf.nightshift.config.ModConfig.get().officePressureMultiplier;
		rate *= inOffice > 0 ? 1.0 : 0.25;
		if (CameraManager.viewerCount() > 0) rate *= 1.8;
		officeDrive += rate * 3.0; // cada segundo
		if (officeDrive < 100) return;
		officeDrive = 0;
		goToOffice(campaign);
	}

	/** Se dirige a una puerta de la oficina siguiendo las rutas del mapa. */
	public boolean goToOffice(CampaignState campaign) {
		List<OfficeManager.OfficeDoor> doors = OfficeManager.doors(world(), campaign);
		if (doors.isEmpty()) return false;
		OfficeManager.OfficeDoor chosen = null;
		net.minecraft.util.math.Direction pref = mob.preferredDoorSide();
		if (pref != null) {
			for (OfficeManager.OfficeDoor d : doors) if (d.outward() == pref) chosen = d;
		}
		if (chosen == null) {
			// la puerta más cercana a su posición actual (ruta coherente)
			double best = Double.MAX_VALUE;
			for (OfficeManager.OfficeDoor d : doors) {
				double dist = d.approach().getSquaredDistance(mob.getBlockPos());
				if (dist < best) {
					best = dist;
					chosen = d;
				}
			}
		}
		lurkDoor = chosen;
		moveTarget = Vec3d.ofBottomCenter(chosen.approach());
		moveSpeed = walkSpeed() * 1.1;
		setState(AiState.APPROACH);
		startMove(moveTarget, moveSpeed);
		return true;
	}

	private void tickApproach() {
		if (lurkDoor == null || moveTarget == null) {
			setState(AiState.PATROL);
			return;
		}
		if (arrived(moveTarget, 1.6)) {
			startLurk(lurkDoor);
			return;
		}
		if (stateTicks > 900) {
			setState(AiState.PATROL);
			return;
		}
		maintainPath();
	}

	public void startLurk(OfficeManager.OfficeDoor door) {
		lurkDoor = door;
		mob.getNavigation().stop();
		lurkDuration = (int) Math.max(100, Math.min(400, (160 + mob.getRandom().nextInt(140)) / Math.max(0.5, aggression())));
		nextBang = 20;
		setState(AiState.LURK);
		// señal previa para quien esté en la oficina
		world().playSound(null, door.lower(), ModSounds.LURK, SoundCategory.HOSTILE, 1.0f, 0.9f + mob.getRandom().nextFloat() * 0.2f);
	}

	private void tickLurk() {
		if (lurkDoor == null) {
			setState(AiState.PATROL);
			return;
		}
		CampaignState campaign = CampaignState.get(world().getServer());
		mob.getLookControl().lookAt(lurkDoor.lower().getX() + 0.5, lurkDoor.lower().getY() + 1.5, lurkDoor.lower().getZ() + 0.5);
		List<PlayerEntity> inside = OfficeManager.playersInOffice(world(), campaign);
		if (inside.isEmpty()) {
			if (stateTicks > 60) {
				lurkDoor = null;
				reposition(null);
			}
			return;
		}
		if (OfficeManager.isOpen(world(), lurkDoor)) {
			// puerta abierta: entra a por el jugador más cercano
			PlayerEntity target = null;
			double best = Double.MAX_VALUE;
			for (PlayerEntity p : inside) {
				double d = p.squaredDistanceTo(mob);
				if (d < best) {
					best = d;
					target = p;
				}
			}
			if (target != null) {
				mob.triggerAnim(AnimatronicEntity.CTRL_ACTION, "detect");
				forceChase(target);
			}
			return;
		}
		// puerta cerrada: golpea; un jugador distraído con las cámaras no lo frena a tiempo
		boolean distracted = inside.stream().anyMatch(p -> CameraManager.isViewing(p.getUuid()));
		if (--nextBang <= 0) {
			nextBang = distracted ? 20 : 30;
			mob.triggerAnim(AnimatronicEntity.CTRL_ACTION, "attack");
			if (OfficeManager.bang(world(), campaign, NightManager.definition(world().getServer()), lurkDoor, mob, 1)) {
				return; // la puerta cedió: el próximo tick entra
			}
		}
		if (stateTicks > lurkDuration) {
			// se cansa y se marcha: la defensa funcionó
			lurkDoor = null;
			reposition(null);
		}
	}

	private int doorStuckTicks;

	/**
	 * Cruce de las puertas de la oficina: el hueco de una puerta abierta es
	 * estrecho (la hoja ocupa un lateral). Si el animatrónico queda frenado
	 * junto a una puerta abierta, se le alinea con el centro del marco y se le
	 * empuja hacia dentro o hacia fuera según adónde va. Nunca más de medio
	 * bloque: no es un teletransporte.
	 */
	private void assistOfficeDoor() {
		if (state != AiState.CHASE && state != AiState.APPROACH && state != AiState.REPOSITION
				&& state != AiState.SEARCH && state != AiState.PATROL && state != AiState.INVESTIGATE) {
			doorStuckTicks = 0;
			return;
		}
		CampaignState campaign = CampaignState.get(world().getServer());
		OfficeManager.OfficeDoor door = OfficeManager.nearestDoor(world(), campaign, mob.getBlockPos(), 2.2);
		if (door == null || !OfficeManager.isOpen(world(), door)) {
			doorStuckTicks = 0;
			return;
		}
		Vec3d v = mob.getVelocity();
		boolean slow = v.x * v.x + v.z * v.z < 0.0025;
		doorStuckTicks = slow ? doorStuckTicks + 1 : Math.max(0, doorStuckTicks - 1);
		if (doorStuckTicks < 6) return;
		doorStuckTicks = 0;
		// ¿hacia dónde quiere ir? hacia la oficina si su objetivo está dentro, si no hacia fuera
		PlayerEntity t = target();
		boolean inward = t != null ? campaign.isInShelter(t)
				: moveTarget != null && isNearShelter(moveTarget, 0);
		net.minecraft.util.math.Direction dir = inward ? door.outward().getOpposite() : door.outward();
		BlockPos d = door.lower();
		double x = mob.getX(), z = mob.getZ();
		// alinear con el centro del marco en el eje perpendicular al paso
		if (door.outward().getAxis() == net.minecraft.util.math.Direction.Axis.X) z = d.getZ() + 0.5;
		else x = d.getX() + 0.5;
		mob.setPosition(x, mob.getY(), z);
		mob.setVelocity(dir.getOffsetX() * 0.25, mob.getVelocity().y, dir.getOffsetZ() * 0.25);
		mob.velocityDirty = true;
		mob.getNavigation().recalculatePath();
	}

	// ------------------------------------------------------------------
	// Navegación y utilidades
	// ------------------------------------------------------------------

	private boolean arrived(@Nullable Vec3d target, double dist) {
		return target != null && mob.getPos().squaredDistanceTo(target.x, mob.getY(), target.z) < dist * dist
				&& Math.abs(mob.getY() - target.y) < 3;
	}

	private void startMove(Vec3d dest, double speed) {
		Path path = mob.getNavigation().findPathTo(dest.x, dest.y, dest.z, 1);
		if (path == null) {
			failedPaths++;
			return;
		}
		mob.getNavigation().startMovingAlong(path, speed);
	}

	/** Si la navegación se detuvo sin llegar, reintenta; tras varios fallos cambia de objetivo. */
	private void maintainPath() {
		if (moveTarget != null && mob.getNavigation().isIdle() && stateTicks % 20 == 0) {
			startMove(moveTarget, moveSpeed);
		}
		if (failedPaths >= 3) {
			failedPaths = 0;
			moveTarget = null;
			if (state == AiState.PATROL || state == AiState.REPOSITION) {
				moveTarget = randomPointNear(mob.getPos(), 10);
				if (moveTarget != null) startMove(moveTarget, moveSpeed);
			} else if (state == AiState.INVESTIGATE) {
				beginSearch(mob.getPos(), false);
			}
		}
	}

	private void checkStuck() {
		boolean moving = !mob.getNavigation().isIdle()
				&& (state == AiState.PATROL || state == AiState.INVESTIGATE || state == AiState.CHASE
				|| state == AiState.REPOSITION || state == AiState.SEARCH);
		Vec3d pos = mob.getPos();
		if (moving && pos.squaredDistanceTo(stuckCheckPos) < 0.12) {
			stuckChecks++;
		} else {
			stuckChecks = 0;
		}
		stuckCheckPos = pos;

		if (stuckChecks == 2) {
			mob.getNavigation().recalculatePath();
			mob.getJumpControl().setActive();
		} else if (stuckChecks == 4) {
			Vec3d alt = randomPointNear(pos, 6);
			if (alt != null) startMove(alt, moveSpeed);
		} else if (stuckChecks >= 6) {
			stuckChecks = 0;
			if (!isObservedByAnyPlayer()) {
				BlockPos safe = nearestSafeNode();
				if (safe != null) {
					mob.refreshPositionAndAngles(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, mob.getYaw(), mob.getPitch());
					mob.getNavigation().stop();
				}
			}
			moveTarget = null;
			if (state != AiState.CHASE) setState(AiState.IDLE);
		}
	}

	private boolean isObservedByAnyPlayer() {
		for (PlayerEntity p : world().getPlayers()) {
			if (!p.isSpectator() && p.squaredDistanceTo(mob) < 48 * 48 && p.canSee(mob)) return true;
		}
		return false;
	}

	private void separate() {
		for (AnimatronicEntity other : world().getEntitiesByClass(AnimatronicEntity.class, mob.getBoundingBox().expand(1.2), e -> e != mob)) {
			Vec3d push = mob.getPos().subtract(other.getPos());
			if (push.lengthSquared() < 1.0E-4) push = new Vec3d(mob.getRandom().nextGaussian(), 0, mob.getRandom().nextGaussian());
			push = push.normalize().multiply(0.12);
			mob.addVelocity(push.x, 0, push.z);
			if (state == AiState.PATROL && mob.getId() > other.getId()) {
				moveTarget = null;
				stateTicks = 0;
			}
		}
	}

	private void avoidShelter() {
		if (state == AiState.REPOSITION || state == AiState.APPROACH || state == AiState.LURK
				|| state == AiState.CHASE || state == AiState.ATTACK || state == AiState.DETECT) return;
		CampaignState campaign = CampaignState.get(world().getServer());
		BlockPos shelter = campaign.getShelter(world());
		if (shelter == null) return;
		double r = campaign.shelterRadius() + 3;
		if (mob.getBlockPos().getSquaredDistance(shelter) < r * r) {
			Vec3d away = NoPenaltyTargeting.findFrom(mob, 16, 4, Vec3d.ofCenter(shelter));
			if (state == AiState.CHASE || state == AiState.ATTACK) mob.onChaseEnded(false);
			reposition(away);
		}
	}

	public boolean isNearShelter(Vec3d pos, double margin) {
		CampaignState campaign = CampaignState.get(world().getServer());
		BlockPos shelter = campaign.getShelter(world());
		if (shelter == null) return false;
		double r = campaign.shelterRadius() + margin;
		return pos.squaredDistanceTo(Vec3d.ofCenter(shelter)) < r * r;
	}

	@Nullable
	private Vec3d randomPointNear(Vec3d center, int radius) {
		for (int i = 0; i < 8; i++) {
			double x = center.x + (mob.getRandom().nextDouble() * 2 - 1) * radius;
			double z = center.z + (mob.getRandom().nextDouble() * 2 - 1) * radius;
			BlockPos p = BlockPos.ofFloored(x, center.y, z);
			for (int dy = 2; dy >= -3; dy--) {
				BlockPos q = p.up(dy);
				if (world().getBlockState(q.down()).isSolidBlock(world(), q.down()) && world().getBlockState(q).getCollisionShape(world(), q).isEmpty()
						&& world().getBlockState(q.up()).getCollisionShape(world(), q.up()).isEmpty()) {
					Vec3d v = Vec3d.ofBottomCenter(q);
					if (!isNearShelter(v, 2)) return v;
				}
			}
		}
		Vec3d fuzzy = FuzzyTargeting.find(mob, radius, 3);
		return fuzzy != null && !isNearShelter(fuzzy, 2) ? fuzzy : null;
	}

	@Nullable
	private Vec3d choosePatrolTarget() {
		CampaignState campaign = CampaignState.get(world().getServer());
		List<BlockPos> nodes = campaign.getNodes(world(), mob.getBlockPos(), 64);
		if (!nodes.isEmpty()) {
			Zone preferred = mob.preferredZone();
			List<BlockPos> candidates = new ArrayList<>();
			for (BlockPos n : nodes) {
				if (recentNodes.contains(n) || n.getSquaredDistance(mob.getBlockPos()) < 9) continue;
				// nodos de otro nivel (túneles) sólo si hay un acceso cercano: se ignoran para no atascarse
				if (Math.abs(n.getY() - mob.getBlockY()) > 4) continue;
				if (isNearShelter(Vec3d.ofCenter(n), 2)) continue;
				candidates.add(n);
			}
			if (preferred != null && mob.getRandom().nextFloat() < 0.7f) {
				List<BlockPos> zoned = candidates.stream().filter(n -> campaign.getNodeZone(n) == preferred).toList();
				if (!zoned.isEmpty()) candidates = new ArrayList<>(zoned);
			}
			if (!candidates.isEmpty()) {
				// Preferir nodos a media distancia para que la patrulla recorra el mapa.
				candidates.sort((a, b) -> Double.compare(a.getSquaredDistance(mob.getBlockPos()), b.getSquaredDistance(mob.getBlockPos())));
				int bound = Math.min(candidates.size(), 5);
				BlockPos pick = candidates.get(mob.getRandom().nextInt(bound));
				recentNodes.addLast(pick);
				while (recentNodes.size() > 4) recentNodes.removeFirst();
				return Vec3d.ofBottomCenter(pick);
			}
		}
		return randomPointNear(mob.getPos(), 14);
	}

	@Nullable
	private Vec3d findRepositionTarget() {
		PlayerEntity nearest = world().getClosestPlayer(mob, 40);
		CampaignState campaign = CampaignState.get(world().getServer());
		// Volver al punto de origen si nadie lo ve
		if (mob.getHome() != null && (nearest == null || !canPlayerSee(nearest, Vec3d.ofCenter(mob.getHome())))) {
			if (mob.getRandom().nextFloat() < 0.4f) return Vec3d.ofBottomCenter(mob.getHome());
		}
		List<BlockPos> nodes = campaign.getNodes(world(), mob.getBlockPos(), 48);
		if (nearest != null) {
			BlockPos best = null;
			double bestScore = -1;
			for (BlockPos n : nodes) {
				Vec3d c = Vec3d.ofCenter(n);
				if (isNearShelter(c, 2)) continue;
				double d = c.squaredDistanceTo(nearest.getPos());
				if (d < 144) continue;
				double score = d + (canPlayerSee(nearest, c) ? -2000 : 0);
				if (score > bestScore) {
					bestScore = score;
					best = n;
				}
			}
			if (best != null) return Vec3d.ofBottomCenter(best);
			Vec3d away = NoPenaltyTargeting.findFrom(mob, 16, 5, nearest.getPos());
			if (away != null) return away;
		}
		return randomPointNear(mob.getPos(), 14);
	}

	public boolean canPlayerSee(PlayerEntity player, Vec3d point) {
		Vec3d eye = player.getEyePos();
		Vec3d p = point.add(0, 1.5, 0);
		if (eye.squaredDistanceTo(p) > 64 * 64) return false;
		return world().raycast(new RaycastContext(eye, p, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player)).getType() == HitResult.Type.MISS;
	}

	@Nullable
	private BlockPos nearestSafeNode() {
		CampaignState campaign = CampaignState.get(world().getServer());
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		List<BlockPos> candidates = new ArrayList<>(campaign.getNodes(world(), mob.getBlockPos(), 64));
		candidates.addAll(campaign.getSpawnPositions(world()));
		for (BlockPos n : candidates) {
			if (isNearShelter(Vec3d.ofCenter(n), 2)) continue;
			if (!world().getBlockState(n.up()).getCollisionShape(world(), n.up()).isEmpty()) continue;
			if (!world().getBlockState(n.up(2)).getCollisionShape(world(), n.up(2)).isEmpty()) continue;
			boolean seen = false;
			for (PlayerEntity p : world().getPlayers()) {
				if (canPlayerSee(p, Vec3d.ofBottomCenter(n))) {
					seen = true;
					break;
				}
			}
			if (seen) continue;
			double d = n.getSquaredDistance(mob.getBlockPos());
			if (d > 4 && d < bestD) {
				bestD = d;
				best = n;
			}
		}
		return best;
	}

	// ------------------------------------------------------------------
	// Persistencia
	// ------------------------------------------------------------------

	public void writeNbt(NbtCompound nbt) {
		NbtCompound ai = new NbtCompound();
		ai.putInt("State", state == AiState.ATTACK || state == AiState.DETECT || state == AiState.STUNNED ? AiState.IDLE.ordinal() : state.ordinal());
		nbt.put("FnafAi", ai);
	}

	public void readNbt(NbtCompound nbt) {
		NbtCompound ai = nbt.getCompound("FnafAi");
		AiState s = AiState.byOrdinal(ai.getInt("State"));
		// Persecuciones no se restauran tras recargar: se retoma desde patrulla.
		state = s == AiState.CHASE || s == AiState.SEARCH || s == AiState.INVESTIGATE ? AiState.PATROL : s;
		stateTicks = 0;
	}
}
