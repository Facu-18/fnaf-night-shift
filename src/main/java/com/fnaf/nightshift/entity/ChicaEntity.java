package com.fnaf.nightshift.entity;

import com.fnaf.nightshift.ai.NoiseManager;
import com.fnaf.nightshift.block.Zone;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.registry.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Chica: recorre las zonas de servicio (cocina, depósitos) y es muy sensible
 * al ruido de las reparaciones: cuanto más ruidosa la herramienta, antes llega.
 */
public class ChicaEntity extends AnimatronicEntity {
	public ChicaEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return createAnimatronicAttributes(0.22, 48);
	}

	@Override
	public AnimatronicKind kind() {
		return AnimatronicKind.CHICA;
	}

	@Override
	public double runMultiplier() {
		return 1.4;
	}

	@Override
	public double detectionScale() {
		return 0.9;
	}

	@Override
	public double getHearingMultiplier(NoiseManager.NoiseKind kind) {
		return switch (kind) {
			case REPAIR -> 1.9;
			case ALARM -> 1.6;
			default -> 1.0;
		};
	}

	@Nullable
	@Override
	public Zone preferredZone() {
		return Zone.SERVICE;
	}

	@Override
	public net.minecraft.util.math.Direction preferredDoorSide() {
		return net.minecraft.util.math.Direction.EAST; // llega por la derecha
	}

	@Override
	public double officeDriveRate() {
		return 0.85;
	}

	@Override
	public float configuredDamage(ModConfig config) {
		return config.chicaDamage;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.CHICA_IDLE;
	}

	@Override
	public SoundEvent signatureSound() {
		return ModSounds.CHICA_IDLE;
	}
}
