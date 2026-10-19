package com.fnaf.nightshift.entity;

import com.fnaf.nightshift.ai.AiState;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.registry.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Foxy: permanece latente en su cala. Su carga aumenta con el tiempo (más
 * rápido si nadie lo vigila). Al llenarse sale disparado hacia el jugador
 * más cercano a gran velocidad. Alumbrarlo con la linterna durante la
 * embestida lo aturde y lo hace volver a su escondite.
 */
public class FoxyEntity extends AnimatronicEntity {
	private static final double CHARGE_FULL = 100.0;
	private double charge;
	private int dashTicks;
	private int peekTicks;

	public FoxyEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return createAnimatronicAttributes(0.25, 72);
	}

	@Override
	public AnimatronicKind kind() {
		return AnimatronicKind.FOXY;
	}

	@Override
	public double runMultiplier() {
		return 2.05;
	}

	@Override
	public double detectionScale() {
		return 0.8;
	}

	@Override
	public float configuredDamage(ModConfig config) {
		return config.foxyDamage;
	}

	public double getCharge() {
		return charge;
	}

	@Override
	public double officeDriveRate() {
		return 0; // Foxy no acecha: embiste
	}

	/** Puerta cerrada durante la embestida: la golpea con fuerza y vuelve a su cala. */
	@Override
	public boolean handleBlockedOfficeDoor(com.fnaf.nightshift.campaign.OfficeManager.OfficeDoor door) {
		if (!(getWorld() instanceof net.minecraft.server.world.ServerWorld sw)) return false;
		com.fnaf.nightshift.campaign.CampaignState s = com.fnaf.nightshift.campaign.CampaignState.get(sw.getServer());
		triggerAnim(CTRL_ACTION, "attack");
		for (int i = 0; i < 2; i++) {
			com.fnaf.nightshift.campaign.OfficeManager.bang(sw, s, com.fnaf.nightshift.campaign.NightManager.definition(sw.getServer()), door, this, 2);
		}
		returnHome();
		return true;
	}

	@Override
	public boolean ignoresSightWhileChasing() {
		return dashTicks > 0;
	}

	@Override
	public boolean customIdleTick() {
		if (peekTicks > 0) {
			if (--peekTicks == 0) startDash();
			return true;
		}
		if (age % 20 != 0) return true;
		boolean watched = false;
		for (PlayerEntity p : getWorld().getPlayers()) {
			if (!p.isSpectator() && p.squaredDistanceTo(this) < 32 * 32 && p.canSee(this)) {
				Vec3d look = p.getRotationVec(1.0f);
				Vec3d to = getPos().subtract(p.getEyePos()).normalize();
				if (look.dotProduct(to) > 0.8) {
					watched = true;
					break;
				}
			}
		}
		charge += getAggression() * (watched ? 0.3 : 1.25);
		if (charge >= CHARGE_FULL) {
			charge = 0;
			peekTicks = 30;
			triggerAnim(CTRL_ACTION, "special");
			getWorld().playSound(null, getBlockPos(), ModSounds.SERVO, SoundCategory.HOSTILE, 1.5f, 0.7f);
		}
		return true;
	}

	private void startDash() {
		PlayerEntity target = null;
		double best = 80 * 80;
		for (PlayerEntity p : getWorld().getPlayers()) {
			if (!ai.isValidTarget(p)) continue;
			double d = squaredDistanceTo(p);
			if (d < best) {
				best = d;
				target = p;
			}
		}
		if (target == null) return;
		dashTicks = 400;
		getWorld().playSound(null, getBlockPos(), ModSounds.FOXY_RUN, SoundCategory.HOSTILE, 2.0f, 1.0f);
		// aviso para todos los jugadores cercanos: algo viene corriendo
		for (PlayerEntity p : getWorld().getPlayers()) {
			if (p.squaredDistanceTo(this) < 70 * 70 && p instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
				sp.sendMessage(net.minecraft.text.Text.translatable("message.fnaf_night_shift.foxy_running")
						.formatted(net.minecraft.util.Formatting.DARK_RED), true);
			}
		}
		ai.forceChase(target);
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient && dashTicks > 0) {
			dashTicks--;
			if (dashTicks == 0 && ai.getState() == AiState.CHASE) {
				returnHome();
			}
		}
	}

	@Override
	public void onChaseEnded(boolean caughtSomeone) {
		if (dashTicks > 0 || caughtSomeone) returnHome();
	}

	private void returnHome() {
		dashTicks = 0;
		ai.reposition(getHome() != null ? Vec3d.ofBottomCenter(getHome()) : null);
	}

	@Override
	public void onFlashlightShine(PlayerEntity player) {
		if (ai.getState() == AiState.CHASE && dashTicks > 0 && squaredDistanceTo(player) < 14 * 14) {
			dashTicks = 0;
			getWorld().playSound(null, getBlockPos(), ModSounds.ANIMATRONIC_HURT, SoundCategory.HOSTILE, 1.0f, 1.3f);
			// al salir del aturdimiento vuelve a su escondite
			ai.reposition(getHome() != null ? Vec3d.ofBottomCenter(getHome()) : null);
			ai.stun(30);
		}
	}

	@Nullable
	@Override
	public net.minecraft.util.math.BlockPos customPatrolTarget() {
		// Foxy no patrulla: si llega a patrullar, vuelve a su cala.
		return getHome();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.SERVO;
	}

	@Override
	public SoundEvent signatureSound() {
		return ModSounds.FOXY_RUN;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putDouble("FoxyCharge", charge);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		charge = nbt.getDouble("FoxyCharge");
	}
}
