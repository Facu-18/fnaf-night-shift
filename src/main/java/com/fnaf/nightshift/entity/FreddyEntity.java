package com.fnaf.nightshift.entity;

import com.fnaf.nightshift.ai.AiState;
import com.fnaf.nightshift.campaign.CampaignState;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.registry.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Freddy: estratega. En lugar de patrullar al azar, acecha: se desplaza a
 * nodos cercanos al jugador que queden fuera de su campo de visión.
 * Es más rápido y percibe mejor en la oscuridad; la linterna lo congela.
 */
public class FreddyEntity extends AnimatronicEntity {
	private int frozenTicks;

	public FreddyEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return createAnimatronicAttributes(0.21, 64);
	}

	@Override
	public AnimatronicKind kind() {
		return AnimatronicKind.FREDDY;
	}

	@Override
	public double runMultiplier() {
		return 1.5;
	}

	@Override
	public double darknessDetectionFactor(boolean playerInDarkness) {
		return playerInDarkness ? 1.2 : 0.9;
	}

	@Override
	public double dynamicSpeedFactor() {
		if (frozenTicks > 0) return 0.0;
		return getWorld().getLightLevel(getBlockPos().up()) < 5 ? 1.3 : 1.0;
	}

	@Override
	public double officeDriveRate() {
		// más paciente pero más insistente; aún más si la oficina se quedó sin luz
		return 1.15;
	}

	@Override
	public float configuredDamage(ModConfig config) {
		return config.freddyDamage;
	}

	@Override
	public void tick() {
		super.tick();
		if (frozenTicks > 0) frozenTicks--;
	}

	@Override
	public void onFlashlightShine(PlayerEntity player) {
		// Alumbrado de frente: queda congelado mientras dure la luz.
		if (squaredDistanceTo(player) < 12 * 12) {
			frozenTicks = 8;
			ai.stun(8);
		}
	}

	@Nullable
	@Override
	public BlockPos customPatrolTarget() {
		if (!(getWorld() instanceof ServerWorld sw)) return null;
		PlayerEntity nearest = null;
		double best = 64 * 64;
		for (PlayerEntity p : sw.getPlayers()) {
			if (!ai.isValidTarget(p)) continue;
			double d = squaredDistanceTo(p);
			if (d < best) {
				best = d;
				nearest = p;
			}
		}
		if (nearest == null) return null;
		CampaignState campaign = CampaignState.get(sw.getServer());
		List<BlockPos> hidden = new ArrayList<>();
		for (BlockPos n : campaign.getNodes(sw, nearest.getBlockPos(), 28)) {
			double d = n.getSquaredDistance(nearest.getBlockPos());
			if (d < 64) continue;
			if (ai.isNearShelter(Vec3d.ofCenter(n), 2)) continue;
			if (!ai.canPlayerSee(nearest, Vec3d.ofBottomCenter(n))) hidden.add(n);
		}
		if (hidden.isEmpty()) return null;
		if (random.nextFloat() < 0.35f) {
			getWorld().playSound(null, getBlockPos(), ModSounds.FREDDY_LAUGH, SoundCategory.HOSTILE, 1.4f, 0.9f + random.nextFloat() * 0.2f);
		}
		BlockPos center = nearest.getBlockPos();
		hidden.sort((a, b) -> Double.compare(a.getSquaredDistance(center), b.getSquaredDistance(center)));
		return hidden.get(random.nextInt(Math.min(3, hidden.size())));
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.FREDDY_LAUGH;
	}

	@Override
	public SoundEvent signatureSound() {
		return ModSounds.FREDDY_LAUGH;
	}
}
