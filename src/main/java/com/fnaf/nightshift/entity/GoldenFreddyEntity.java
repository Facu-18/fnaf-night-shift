package com.fnaf.nightshift.entity;

import com.fnaf.nightshift.ai.AiState;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.registry.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Golden Freddy (personaje secreto). No camina: se manifiesta cerca de un
 * jugador, lo observa y desaparece. Si alguien lo mira fijamente demasiado
 * tiempo durante la noche, lo atrapa. Lo genera el evento especial
 * {@link com.fnaf.nightshift.campaign.SpecialEvents}.
 */
public class GoldenFreddyEntity extends AnimatronicEntity {
	private int lifetime = 160;
	private final Map<UUID, Integer> stare = new HashMap<>();
	@Nullable
	private UUID focus;

	public GoldenFreddyEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		setNoGravity(false);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return createAnimatronicAttributes(0.0, 32);
	}

	public void setFocus(@Nullable UUID player, int lifetimeTicks) {
		this.focus = player;
		this.lifetime = lifetimeTicks;
	}

	@Override
	public AnimatronicKind kind() {
		return AnimatronicKind.GOLDEN_FREDDY;
	}

	@Override
	public double runMultiplier() {
		return 0;
	}

	@Override
	public double officeDriveRate() {
		return 0;
	}

	@Override
	public float configuredDamage(ModConfig config) {
		return config.freddyDamage;
	}

	@Override
	protected void serverAiTick() {
		if (!(getWorld() instanceof ServerWorld sw)) return;
		getNavigation().stop();
		PlayerEntity look = focus != null ? sw.getPlayerByUuid(focus) : sw.getClosestPlayer(this, 24);
		if (look != null) getLookControl().lookAt(look, 10, 10);

		if (age % 2 == 0) {
			for (PlayerEntity p : sw.getPlayers()) {
				if (!ai.isValidTarget(p) || p.squaredDistanceTo(this) > 24 * 24) continue;
				Vec3d dir = p.getRotationVec(1.0f);
				Vec3d to = getEyePos().subtract(p.getEyePos()).normalize();
				boolean staring = dir.dotProduct(to) > 0.96 && p.canSee(this);
				int t = stare.getOrDefault(p.getUuid(), 0);
				t = staring ? t + 2 : Math.max(0, t - 1);
				stare.put(p.getUuid(), t);
				if (t >= 60 && getSession() >= 0) {
					triggerAnim(CTRL_ACTION, "attack");
					performAttack(p);
					vanish(sw);
					return;
				}
			}
		}
		syncAiState(AiState.IDLE, false);
		if (getSession() >= 0 && --lifetime <= 0) vanish(sw);
	}

	private void vanish(ServerWorld sw) {
		sw.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + 1, getZ(), 40, 0.4, 0.8, 0.4, 0.02);
		sw.playSound(null, getBlockPos(), ModSounds.GOLDEN_WHISPER, SoundCategory.HOSTILE, 1.0f, 0.6f);
		discard();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.GOLDEN_WHISPER;
	}

	@Override
	public SoundEvent signatureSound() {
		return ModSounds.GOLDEN_WHISPER;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Lifetime", lifetime);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Lifetime")) lifetime = nbt.getInt("Lifetime");
	}
}
