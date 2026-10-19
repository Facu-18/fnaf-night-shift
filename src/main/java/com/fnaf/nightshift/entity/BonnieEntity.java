package com.fnaf.nightshift.entity;

import com.fnaf.nightshift.ai.NoiseManager;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.registry.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;

/**
 * Bonnie: patrullero incansable. Buen oído, recorre todo el mapa e investiga
 * cualquier ruido. Es el animatrónico de la primera noche.
 */
public class BonnieEntity extends AnimatronicEntity {
	public BonnieEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return createAnimatronicAttributes(0.24, 48);
	}

	@Override
	public AnimatronicKind kind() {
		return AnimatronicKind.BONNIE;
	}

	@Override
	public double runMultiplier() {
		return 1.45;
	}

	@Override
	public double getHearingMultiplier(NoiseManager.NoiseKind kind) {
		return 1.25;
	}

	@Override
	public net.minecraft.util.math.Direction preferredDoorSide() {
		return net.minecraft.util.math.Direction.WEST; // como en el original: llega por la izquierda
	}

	@Override
	public float configuredDamage(ModConfig config) {
		return config.bonnieDamage;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.BONNIE_IDLE;
	}

	@Override
	public SoundEvent signatureSound() {
		return ModSounds.SERVO;
	}
}
