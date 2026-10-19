package com.fnaf.nightshift.campaign;

import com.fnaf.nightshift.ai.NoiseManager;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.network.ModPackets;
import com.fnaf.nightshift.registry.ModSounds;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Estamina individual de cada jugador, calculada en el servidor. Correr la
 * consume; caminar o quedarse quieto la recupera. Al agotarse no se puede
 * correr hasta recuperar un umbral, se respira con fuerza (hace ruido) y
 * los animatrónicos detectan y alcanzan mejor al jugador agotado.
 * Sólo actúa durante una noche activa.
 */
public final class StaminaManager {
	private static final Map<UUID, Float> stamina = new HashMap<>();
	private static final Set<UUID> exhausted = new HashSet<>();
	private static final Map<UUID, Vec3d> lastPos = new HashMap<>();
	private static final Map<UUID, Float> lastSent = new HashMap<>();
	private static final Map<UUID, Boolean> lastSentExhausted = new HashMap<>();

	private StaminaManager() {
	}

	public static float get(UUID id) {
		return stamina.getOrDefault(id, ModConfig.get().staminaMax);
	}

	public static boolean isExhausted(UUID id) {
		return exhausted.contains(id);
	}

	public static void reset(UUID id) {
		stamina.remove(id);
		exhausted.remove(id);
	}

	public static void tick(MinecraftServer server) {
		ModConfig c = ModConfig.get();
		CampaignState s = CampaignState.get(server);
		boolean active = c.staminaEnabled && s.getPhase().isActive();
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			tickPlayer(p, s, c, active);
		}
	}

	/** Visible para las pruebas automáticas. */
	public static void tickPlayer(ServerPlayerEntity p, CampaignState s, ModConfig c, boolean active) {
		UUID id = p.getUuid();
		float max = c.staminaMax;
		float value = get(id);
		boolean counts = active && !p.isCreative() && !p.isSpectator() && !s.isCaught(id) && s.isFacilityWorld(p.getWorld());
		if (!counts) {
			value = max;
			exhausted.remove(id);
		} else {
			Vec3d prev = lastPos.get(id);
			Vec3d now = p.getPos();
			double moved = prev == null ? 0 : Math.hypot(now.x - prev.x, now.z - prev.z);
			lastPos.put(id, now);
			boolean sprinting = p.isSprinting() && moved > 0.05;
			if (sprinting && !exhausted.contains(id)) {
				value -= c.staminaDrainPerSecond / 20f;
				if (value <= 0) {
					value = 0;
					exhausted.add(id);
					p.playSound(ModSounds.BREATH, SoundCategory.PLAYERS, 1.0f, 1.0f);
				}
			} else {
				float regen = moved > 0.02 ? c.staminaRegenWalkingPerSecond : c.staminaRegenRestingPerSecond;
				value = Math.min(max, value + regen / 20f);
				if (exhausted.contains(id) && value >= c.staminaRecoverThreshold) exhausted.remove(id);
			}
			if (exhausted.contains(id)) {
				if (p.isSprinting()) {
					// el cliente también lo impide; esto cubre la latencia
					p.setSprinting(false);
					p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 0, false, false, false));
				}
				if (p.age % 30 == 0) {
					NoiseManager.emit(p.getServerWorld(), p.getPos(), 6, NoiseManager.NoiseKind.FOOTSTEP);
					p.playSound(ModSounds.BREATH, SoundCategory.PLAYERS, 0.6f, 1.0f);
				}
			}
		}
		stamina.put(id, value);
		boolean ex = exhausted.contains(id);
		Float sent = lastSent.get(id);
		if (sent == null || Math.abs(sent - value) >= 1f || lastSentExhausted.getOrDefault(id, false) != ex || (value >= max && sent < max)) {
			lastSent.put(id, value);
			lastSentExhausted.put(id, ex);
			ModPackets.sendStamina(p, value / max, ex);
		}
	}

	public static void remove(PlayerEntity p) {
		stamina.remove(p.getUuid());
		exhausted.remove(p.getUuid());
		lastPos.remove(p.getUuid());
		lastSent.remove(p.getUuid());
		lastSentExhausted.remove(p.getUuid());
	}
}
