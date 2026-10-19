package com.fnaf.nightshift.ai;

import com.fnaf.nightshift.entity.AnimatronicEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Sistema de ruido: reparaciones, puertas, carreras y alarmas emiten ruido
 * que los animatrónicos cercanos pueden oír e investigar.
 */
public final class NoiseManager {
	public enum NoiseKind {
		/** Pasos al correr. */
		FOOTSTEP,
		/** Reparaciones (herramientas, generadores). */
		REPAIR,
		/** Puertas, rejillas, cajas. */
		DOOR,
		/** Fallo de un minijuego: alarma muy ruidosa. */
		ALARM
	}

	private NoiseManager() {
	}

	public static void emit(ServerWorld world, BlockPos pos, double radius, NoiseKind kind) {
		emit(world, Vec3d.ofCenter(pos), radius, kind);
	}

	public static void emit(ServerWorld world, Vec3d pos, double radius, NoiseKind kind) {
		double maxReach = radius * 2.0; // margen para animatrónicos con oído fino
		Box box = new Box(pos, pos).expand(maxReach);
		for (AnimatronicEntity e : world.getEntitiesByClass(AnimatronicEntity.class, box, AnimatronicEntity::isAlive)) {
			double hearing = radius * e.getHearingMultiplier(kind);
			if (e.squaredDistanceTo(pos) <= hearing * hearing) {
				e.getAi().hearNoise(pos, kind);
			}
		}
	}
}
