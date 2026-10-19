package com.fnaf.nightshift.campaign;

import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.registry.ModSounds;
import com.fnaf.nightshift.repair.RepairType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ventilación de la oficina: necesita reiniciarse desde el panel de control
 * antes de que venza su temporizador (más corto en noches avanzadas). Avisa
 * a los 30 y a los 10 segundos. Si falla, los participantes sufren efectos
 * temporales y reversibles (náuseas = distorsión, oscuridad = visión
 * reducida, visión borrosa en la interfaz) hasta que alguien la reinicie.
 * Se integra con las instalaciones de ventilación existentes: si alguna está
 * averiada el temporizador corre más rápido, y cada tres fallos se avería
 * una de ellas (avería adicional obligatoria, si queda tiempo).
 */
public final class VentilationSystem {
	private static final Map<UUID, Long> lastReset = new HashMap<>();

	private VentilationSystem() {
	}

	public static void begin(CampaignState s, NightDefinition def) {
		s.setVentTicks(def.ventIntervalTicks());
		s.setVentFailed(false);
		s.setVentFailures(0);
	}

	public static void tick(ServerWorld world, CampaignState s, NightDefinition def) {
		if (!ModConfig.get().ventilationEnabled || s.getGraceTicks() > 0) return;
		MinecraftServer server = world.getServer();
		List<ServerPlayerEntity> players = affected(world, s);
		if (s.isVentFailed()) {
			if (server.getTicks() % 40 == 0) {
				for (ServerPlayerEntity p : players) applyEffects(s, p);
			}
			if (server.getTicks() % 80 == 0) {
				for (ServerPlayerEntity p : players) p.playSound(ModSounds.VENT_FAIL, SoundCategory.AMBIENT, 0.5f, 0.8f);
			}
			return;
		}
		// Noche guardada con una versión anterior (sin temporizador): se inicializa en lugar de fallar al cargar.
		if (s.getVentTicks() <= 0) {
			s.setVentTicks(def.ventIntervalTicks());
			return;
		}
		int before = s.getVentTicks();
		int rate = BreakdownManager.ventDegraded(s) || anyVentStationBroken(world, s) ? 2 : 1;
		// las averías de ventilación aceleran el temporizador un 50% (2 ticks cada 2)
		int dec = rate == 2 ? (server.getTicks() % 2 == 0 ? 2 : 1) : 1;
		int now = Math.max(0, before - dec);
		s.setVentTicks(now);
		if (crossed(before, now, 30 * 20) || crossed(before, now, 10 * 20)) {
			for (ServerPlayerEntity p : players) {
				p.playSound(ModSounds.VENT_WARNING, SoundCategory.MASTER, 0.9f, now <= 10 * 20 ? 1.3f : 1.0f);
				p.sendMessage(Text.translatable("message.fnaf_night_shift.vent_warning", now / 20).formatted(Formatting.YELLOW), true);
			}
		}
		if (now == 0) fail(world, s, def, players);
	}

	private static boolean crossed(int before, int now, int mark) {
		return before > mark && now <= mark;
	}

	private static boolean anyVentStationBroken(ServerWorld world, CampaignState s) {
		for (BlockPos p : s.getRequired()) {
			if (!s.getCompleted().contains(p) && world.getBlockEntity(p) instanceof com.fnaf.nightshift.repair.RepairStationBlockEntity be
					&& be.getRepairType() == RepairType.VENTILATION && be.isBroken()) return true;
		}
		return false;
	}

	private static void fail(ServerWorld world, CampaignState s, NightDefinition def, List<ServerPlayerEntity> players) {
		s.setVentFailed(true);
		s.setVentFailures(s.getVentFailures() + 1);
		for (ServerPlayerEntity p : players) {
			p.playSound(ModSounds.VENT_FAIL, SoundCategory.MASTER, 1.0f, 1.0f);
			p.sendMessage(Text.translatable("message.fnaf_night_shift.vent_failed").formatted(Formatting.RED), false);
			applyEffects(s, p);
		}
		if (s.getVentFailures() % 3 == 0) {
			BreakdownManager.triggerOfType(world, s, def, RepairType.VENTILATION, BreakdownManager.VENT);
		}
	}

	private static void applyEffects(CampaignState s, ServerPlayerEntity p) {
		if (p.isCreative() || p.isSpectator() || s.isCaught(p.getUuid())) return;
		p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 160, 0, false, false, true));
		p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0, false, false, true));
	}

	/** Reinicio desde el panel de control de la oficina. */
	public static void reset(ServerPlayerEntity player, BlockPos panel) {
		MinecraftServer server = player.getServer();
		if (server == null) return;
		CampaignState s = CampaignState.get(server);
		if (!s.getPhase().isActive()) {
			player.sendMessage(Text.translatable("message.fnaf_night_shift.vent_idle").formatted(Formatting.GRAY), true);
			return;
		}
		long t = player.getServerWorld().getTime();
		Long last = lastReset.get(player.getUuid());
		if (last != null && t - last < 60) return; // evita reinicios dobles por clic mantenido
		lastReset.put(player.getUuid(), t);
		NightDefinition def = NightManager.definition(server);
		boolean wasFailed = s.isVentFailed();
		s.setVentTicks(def.ventIntervalTicks());
		s.setVentFailed(false);
		player.getServerWorld().playSound(null, panel, ModSounds.VENT_RESET, SoundCategory.BLOCKS, 1.0f, 1.0f);
		for (ServerPlayerEntity p : affected(player.getServerWorld(), s)) {
			if (wasFailed) {
				p.removeStatusEffect(StatusEffects.NAUSEA);
				p.removeStatusEffect(StatusEffects.DARKNESS);
			}
			p.sendMessage(Text.translatable("message.fnaf_night_shift.vent_reset", player.getDisplayName()).formatted(Formatting.AQUA), true);
		}
		NightManager.syncAll(server);
	}

	/** Jugadores del mundo del complejo afectados por la ventilación. */
	private static List<ServerPlayerEntity> affected(ServerWorld world, CampaignState s) {
		List<ServerPlayerEntity> list = new java.util.ArrayList<>();
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (!p.isSpectator() && s.isFacilityWorld(p.getWorld())) list.add(p);
		}
		return list;
	}

	/** Al terminar la noche se retiran los efectos. */
	public static void clearEffects(MinecraftServer server, CampaignState s) {
		ServerWorld fw = s.getFacilityWorld(server);
		if (fw == null) return;
		for (ServerPlayerEntity p : affected(fw, s)) {
			if (s.isVentFailed()) {
				p.removeStatusEffect(StatusEffects.NAUSEA);
				p.removeStatusEffect(StatusEffects.DARKNESS);
			}
		}
		s.setVentFailed(false);
	}
}
