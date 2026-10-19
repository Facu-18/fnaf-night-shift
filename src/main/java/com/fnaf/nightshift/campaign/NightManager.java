package com.fnaf.nightshift.campaign;

import com.fnaf.nightshift.FnafNightShift;
import com.fnaf.nightshift.ai.AiState;
import com.fnaf.nightshift.ai.NoiseManager;
import com.fnaf.nightshift.block.SupplyCrateBlock;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.entity.AnimatronicEntity;
import com.fnaf.nightshift.entity.AnimatronicKind;
import com.fnaf.nightshift.item.FlashlightItem;
import com.fnaf.nightshift.network.ModPackets;
import com.fnaf.nightshift.registry.ModEntities;
import com.fnaf.nightshift.registry.ModItems;
import com.fnaf.nightshift.registry.ModSounds;
import com.fnaf.nightshift.repair.RepairStationBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Controla el ciclo de cada noche en el servidor: inicio, objetivos,
 * aparición de animatrónicos, temporizador, victoria, derrota y reinicio.
 * Todo el estado importante vive en {@link CampaignState} (persistente).
 */
public final class NightManager {
	public enum DefeatReason {CAUGHT, TIME}

	private static int pendingDefeatTicks = -1;
	@Nullable
	private static NightDefinition cachedDefinition;
	private static int cachedSession = Integer.MIN_VALUE;

	private NightManager() {
	}

	// ------------------------------------------------------------------
	// Consultas
	// ------------------------------------------------------------------

	public static NightDefinition definition(MinecraftServer server) {
		CampaignState state = CampaignState.get(server);
		int key = state.getSession() * 31 + state.getDifficulty().ordinal() * 7 + state.getActiveNight();
		if (cachedDefinition == null || cachedSession != key) {
			int night = state.getPhase().isActive() ? state.getActiveNight() : state.getCurrentNight();
			cachedDefinition = NightDefinition.of(night, state.getDifficulty());
			cachedSession = key;
		}
		return cachedDefinition;
	}

	public static void invalidateCache() {
		cachedDefinition = null;
	}

	public static double currentAggression(MinecraftServer server) {
		return definition(server).aggression();
	}

	public static boolean isNightActive(MinecraftServer server) {
		return CampaignState.get(server).getPhase().isActive();
	}

	public static boolean isParticipant(MinecraftServer server, ServerPlayerEntity player) {
		CampaignState s = CampaignState.get(server);
		return s.getPhase().isActive() && s.getParticipants().contains(player.getUuid()) && !player.isCreative() && !player.isSpectator();
	}

	// ------------------------------------------------------------------
	// Inicio / fin
	// ------------------------------------------------------------------

	public static boolean startNight(MinecraftServer server, int night, @Nullable ServerPlayerEntity initiator) {
		CampaignState state = CampaignState.get(server);
		ServerWorld world = state.getFacilityWorld(server);
		BlockPos shelter = state.getShelterPos();
		if (shelter == null || world == null) {
			feedback(server, initiator, Text.translatable("message.fnaf_night_shift.no_shelter").formatted(Formatting.RED));
			return false;
		}
		if (state.getPhase().isActive()) {
			cleanupNight(server, state);
		}
		if (night > state.getHighestUnlocked() && initiator != null && !initiator.hasPermissionLevel(2)) {
			feedback(server, initiator, Text.translatable("message.fnaf_night_shift.night_locked", night).formatted(Formatting.RED));
			return false;
		}

		NightDefinition def = NightDefinition.of(night, state.getDifficulty());
		ModConfig config = ModConfig.get();
		// Mapas generados antes de la 1.1: se añaden cámaras, casilleros, ventilación y dinteles (sólo en huecos libres).
		if (com.fnaf.nightshift.world.FacilityUpgrade.needsUpgrade(world, state)) {
			int changes = com.fnaf.nightshift.world.FacilityUpgrade.upgradeExisting(server);
			if (changes > 0) feedback(server, initiator, Text.translatable("message.fnaf_night_shift.upgrade_done", changes).formatted(Formatting.GREEN));
		}
		BreakdownManager.revertAll(world, state);
		state.beginNight(def.night(), def.durationTicks(), config.gracePeriodSeconds * 20);
		VentilationSystem.begin(state, def);
		OfficeManager.reset();
		invalidateCache();
		pendingDefeatTicks = -1;

		// Instalaciones: todas operativas, luego se averían las elegidas.
		List<BlockPos> valid = new ArrayList<>();
		for (BlockPos p : new ArrayList<>(state.getStations())) {
			if (world.getBlockEntity(p) instanceof RepairStationBlockEntity be) {
				be.resetRepaired();
				valid.add(p);
			} else {
				state.unregisterStation(world, p);
			}
		}
		Collections.shuffle(valid, new java.util.Random(world.random.nextLong()));
		int count = Math.min(def.repairs(), valid.size());
		int level = def.puzzleLevel(state.getDifficulty());
		for (int i = 0; i < count; i++) {
			BlockPos p = valid.get(i);
			if (world.getBlockEntity(p) instanceof RepairStationBlockEntity be) {
				be.breakStation(world.random.nextLong(), level);
				state.getRequired().add(p);
			}
		}

		// Cajas de suministros rellenas
		for (BlockPos p : new ArrayList<>(state.getCrates())) {
			BlockState bs = world.getBlockState(p);
			if (bs.getBlock() instanceof SupplyCrateBlock) {
				world.setBlockState(p, bs.with(SupplyCrateBlock.LOOTED, false), 3);
			}
		}

		removeAnimatronics(world, a -> a.getSession() >= 0);
		AtmosphereManager.applyNightDarkness(world, state, def);

		// Participantes
		List<ServerPlayerEntity> joining = new ArrayList<>();
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (p.isSpectator()) continue;
			if (!p.isCreative()) state.getParticipants().add(p.getUuid());
			joining.add(p);
			StaminaManager.reset(p.getUuid());
			CameraManager.forceClose(p);
		}
		// Linterna al empezar; herramientas en casilleros (sin duplicar objetos entre reinicios).
		ResourceManager.prepareNight(world, state, def, joining);
		for (ServerPlayerEntity p : joining) {
			teleportToShelter(p, world, shelter);
			p.setHealth(p.getMaxHealth());
			p.getHungerManager().setFoodLevel(20);
			title(p, Text.translatable("title.fnaf_night_shift.night", def.night()).formatted(Formatting.WHITE),
					Text.literal("12:00 AM").formatted(Formatting.GRAY));
			p.playSound(ModSounds.NIGHT_START, SoundCategory.MASTER, 1.0f, 1.0f);
			if (def.night() == 7) p.playSound(ModSounds.MUSIC_BOX, SoundCategory.MASTER, 0.7f, 1.0f);
			p.sendMessage(Text.translatable("message.fnaf_night_shift.night_started", def.night(), state.getRequired().size(),
					def.durationTicks() / 20 / 60, Text.translatable(state.getDifficulty().translationKey())).formatted(Formatting.GOLD), false);
			if (valid.isEmpty()) {
				p.sendMessage(Text.translatable("message.fnaf_night_shift.no_stations").formatted(Formatting.YELLOW), false);
			}
		}
		state.markDirty();
		FnafNightShift.LOGGER.info("Noche {} iniciada ({} reparaciones, agresividad {})", def.night(), state.getRequired().size(), String.format("%.2f", def.aggression()));
		return true;
	}

	/** Detiene la noche sin resultado (comando de administración). */
	public static void stopNight(MinecraftServer server) {
		CampaignState state = CampaignState.get(server);
		if (!state.getPhase().isActive()) return;
		cleanupNight(server, state);
		state.endNight(NightPhase.NONE);
		syncAll(server);
	}

	public static void restartNight(MinecraftServer server, @Nullable ServerPlayerEntity initiator) {
		CampaignState state = CampaignState.get(server);
		int night = state.getPhase().isActive() || state.getPhase() == NightPhase.DEFEAT || state.getPhase() == NightPhase.VICTORY
				? Math.max(1, state.getActiveNight()) : state.getCurrentNight();
		startNight(server, night, initiator);
	}

	private static void cleanupNight(MinecraftServer server, CampaignState state) {
		ServerWorld world = state.getFacilityWorld(server);
		if (world != null) {
			removeAnimatronics(world, a -> a.getSession() >= 0);
			endOfNightRestore(server, world, state);
		}
		pendingDefeatTicks = -1;
	}

	/** Revierte las consecuencias temporales de la noche (puertas, luces, efectos de ventilación, cámaras). */
	private static void endOfNightRestore(MinecraftServer server, ServerWorld world, CampaignState state) {
		BreakdownManager.revertAll(world, state);
		AtmosphereManager.restore(world, state);
		VentilationSystem.clearEffects(server, state);
		for (ServerPlayerEntity p : playersIn(server, state)) CameraManager.forceClose(p);
	}

	private static void victory(MinecraftServer server, CampaignState state) {
		ServerWorld world = state.getFacilityWorld(server);
		int night = state.getActiveNight();
		int timeUsed = state.getTotalTicks() - state.getTicksRemaining();
		if (world != null) endOfNightRestore(server, world, state);
		state.endNight(NightPhase.VICTORY);
		state.markNightCompleted(night);
		invalidateCache();
		if (world != null) removeAnimatronics(world, a -> a.getSession() >= 0);
		for (ServerPlayerEntity p : playersIn(server, state)) {
			title(p, Text.literal("6:00 AM").formatted(Formatting.GOLD), Text.translatable("title.fnaf_night_shift.survived").formatted(Formatting.YELLOW));
			p.playSound(ModSounds.SIX_AM, SoundCategory.MASTER, 1.0f, 1.0f);
			ModPackets.sendResult(p, true, night, state.getCompleted().size(), state.getRequired().size(), state.getNightCatches(), timeUsed, null);
			p.removeStatusEffect(StatusEffects.BLINDNESS);
			p.removeStatusEffect(StatusEffects.SLOWNESS);
		}
		syncAll(server);
		FnafNightShift.LOGGER.info("Noche {} superada", night);
	}

	private static void defeat(MinecraftServer server, CampaignState state, DefeatReason reason) {
		ServerWorld world = state.getFacilityWorld(server);
		int night = state.getActiveNight();
		int timeUsed = state.getTotalTicks() - state.getTicksRemaining();
		if (world != null) endOfNightRestore(server, world, state);
		state.endNight(NightPhase.DEFEAT);
		invalidateCache();
		if (world != null) removeAnimatronics(world, a -> a.getSession() >= 0);
		BlockPos shelter = state.getShelterPos();
		for (ServerPlayerEntity p : playersIn(server, state)) {
			title(p, Text.translatable("title.fnaf_night_shift.game_over").formatted(Formatting.DARK_RED),
					Text.translatable("title.fnaf_night_shift.defeat_" + reason.name().toLowerCase()).formatted(Formatting.RED));
			ModPackets.sendResult(p, false, night, state.getCompleted().size(), state.getRequired().size(), state.getNightCatches(), timeUsed, reason);
			if (world != null && shelter != null && p.getWorld() == world) teleportToShelter(p, world, shelter);
			p.removeStatusEffect(StatusEffects.BLINDNESS);
			p.removeStatusEffect(StatusEffects.SLOWNESS);
		}
		syncAll(server);
		FnafNightShift.LOGGER.info("Noche {} perdida ({})", night, reason);
	}

	// ------------------------------------------------------------------
	// Eventos de juego
	// ------------------------------------------------------------------

	public static void onRepairCompleted(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
		MinecraftServer server = world.getServer();
		CampaignState state = CampaignState.get(server);
		if (!state.getPhase().isActive() || !state.getRequired().contains(pos)) return;
		if (!state.getCompleted().add(pos.toImmutable())) return; // evita contar dos veces
		BreakdownManager.onRepaired(world, state, pos);
		state.markDirty();
		int done = state.getCompleted().size();
		int total = state.getRequired().size();
		for (ServerPlayerEntity p : playersIn(server, state)) {
			p.sendMessage(Text.translatable("message.fnaf_night_shift.objective_progress", player.getDisplayName(), done, total).formatted(Formatting.AQUA), false);
		}
		if (done >= total && state.getPhase() == NightPhase.REPAIRING) {
			state.setPhase(NightPhase.RETURN_TO_SHELTER);
			for (ServerPlayerEntity p : playersIn(server, state)) {
				// Completar los objetivos no termina la noche: hay que sobrevivir hasta las 6 AM.
				title(p, Text.translatable("title.fnaf_night_shift.all_repaired").formatted(Formatting.GREEN),
						Text.translatable("title.fnaf_night_shift.survive_until_6").formatted(Formatting.GRAY));
				p.playSound(ModSounds.REPAIR_SUCCESS, SoundCategory.MASTER, 1.0f, 0.8f);
			}
			// Último empujón: todos los animatrónicos buscan al jugador que terminó.
			for (AnimatronicEntity a : world.getEntitiesByClass(AnimatronicEntity.class, new Box(pos).expand(64), e -> e.belongsTo(state))) {
				a.getAi().hearNoise(player.getPos(), NoiseManager.NoiseKind.ALARM);
			}
		}
		syncAll(server);
	}

	public static void catchPlayer(ServerPlayerEntity player, AnimatronicEntity by) {
		MinecraftServer server = player.getServer();
		if (server == null) return;
		CampaignState state = CampaignState.get(server);
		if (!state.getPhase().isActive() || state.isCaught(player.getUuid())) return;
		state.addCatch(player.getUuid());
		CameraManager.forceClose(player);
		ModPackets.sendJumpscare(player, by.kind());
		// El grito lo reproduce el cliente al recibir el paquete del susto (sin posición): si lo
		// emitiera el servidor quedaría sonando donde lo atraparon, ya que acto seguido se lo lleva a la oficina.
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 3, false, false));
		ServerWorld world = state.getFacilityWorld(server);
		BlockPos shelter = state.getShelterPos();
		if (world != null && shelter != null) teleportToShelter(player, world, shelter);

		boolean anyFree = false;
		for (UUID id : state.getParticipants()) {
			if (!state.isCaught(id) && isPresent(server, state, id)) anyFree = true;
		}
		if (!anyFree) {
			pendingDefeatTicks = 45; // dejar que se vea el susto antes de la pantalla de derrota
		} else {
			for (ServerPlayerEntity p : playersIn(server, state)) {
				p.sendMessage(Text.translatable("message.fnaf_night_shift.player_caught", player.getDisplayName(),
						Text.translatable("animatronic.fnaf_night_shift." + by.kind().id())).formatted(Formatting.DARK_RED), false);
			}
		}
		syncAll(server);
	}

	/** ¿El participante sigue en la partida? (conectado o presente en el mundo del complejo) */
	public static boolean isPresent(MinecraftServer server, CampaignState state, UUID id) {
		if (server.getPlayerManager().getPlayer(id) != null) return true;
		ServerWorld w = state.getFacilityWorld(server);
		return w != null && w.getPlayerByUuid(id) != null;
	}

	/** Todos los objetivos obligatorios están reparados (se comprueba el estado real de cada instalación). */
	public static boolean allObjectivesDone(ServerWorld world, CampaignState state) {
		for (BlockPos p : state.getRequired()) {
			if (!state.getCompleted().contains(p)) return false;
			if (world.isChunkLoaded(p) && world.getBlockEntity(p) instanceof RepairStationBlockEntity be && be.isBroken()) return false;
		}
		return true;
	}

	/** Al menos un participante sigue libre (sin participantes —pruebas en creativo— cuenta como sí). */
	public static boolean anyoneSurvived(MinecraftServer server, CampaignState state) {
		if (state.getParticipants().isEmpty()) return true;
		for (UUID id : state.getParticipants()) {
			if (!state.isCaught(id)) return true;
		}
		return false;
	}

	public static void onPlayerJoin(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) return;
		CampaignState state = CampaignState.get(server);
		if (state.getPhase().isActive() && state.isFacilityWorld(player.getWorld()) && !player.isSpectator() && !player.isCreative()) {
			state.getParticipants().add(player.getUuid());
			state.markDirty();
		}
		ModPackets.sendNightState(player, state);
	}

	// ------------------------------------------------------------------
	// Tick
	// ------------------------------------------------------------------

	public static void tick(MinecraftServer server) {
		CampaignState state = CampaignState.get(server);
		ServerWorld world = state.getFacilityWorld(server);
		int t = server.getTicks();

		if (!state.getPhase().isActive()) {
			if (t % 100 == 0 && world != null) removeAnimatronics(world, a -> a.getSession() >= 0);
			if (t % 40 == 0) syncAll(server);
			return;
		}
		if (world == null) return;
		NightDefinition def = definition(server);

		if (pendingDefeatTicks > 0) {
			if (--pendingDefeatTicks == 0) {
				pendingDefeatTicks = -1;
				defeat(server, state, DefeatReason.CAUGHT);
				return;
			}
		}

		// Periodo de gracia
		if (state.getGraceTicks() > 0) {
			state.setGraceTicks(state.getGraceTicks() - 1);
			if (def.tutorial()) tutorial(server, state, def);
			if (state.getGraceTicks() == 0) {
				for (ServerPlayerEntity p : playersIn(server, state)) {
					p.sendMessage(Text.translatable("message.fnaf_night_shift.animatronics_awake").formatted(Formatting.DARK_RED), false);
					p.playSound(ModSounds.POWER_OUT, SoundCategory.MASTER, 0.8f, 0.7f);
				}
			}
		} else if (!state.isSpawned()) {
			spawnRoster(world, state, def);
			state.setSpawned(true);
		}

		// Temporizador
		state.setTicksRemaining(state.getTicksRemaining() - 1);
		if (state.getTicksRemaining() <= 0) {
			if (allObjectivesDone(world, state) && anyoneSurvived(server, state)) victory(server, state);
			else defeat(server, state, DefeatReason.TIME);
			return;
		}
		// Campanada de cada hora
		int hourTicks = Math.max(1, state.getTotalTicks() / 6);
		if (state.getTicksRemaining() % hourTicks == 0) {
			int hour = 6 - state.getTicksRemaining() / hourTicks;
			for (ServerPlayerEntity p : playersIn(server, state)) {
				p.playSound(ModSounds.NIGHT_START, SoundCategory.MASTER, 0.5f, 1.3f);
				p.sendMessage(Text.translatable("message.fnaf_night_shift.hour", hour).formatted(Formatting.GRAY), true);
			}
		}

		// Sistemas de la oficina (1.1)
		VentilationSystem.tick(world, state, def);
		OfficeManager.tick(world, state);
		if (t % 20 == 0) BreakdownManager.tick(world, state, def);

		// Ruido de pasos al correr
		if (t % 10 == 0) {
			for (ServerPlayerEntity p : world.getPlayers()) {
				if (p.isSprinting() && !p.isSpectator() && !p.isCreative() && p.isOnGround()) {
					NoiseManager.emit(world, p.getPos(), 9, NoiseManager.NoiseKind.FOOTSTEP);
				}
			}
		}

		if (t % 40 == 0) {
			if (state.isSpawned()) maintainRoster(world, state, def);
			// Animatrónicos de sesiones viejas
			removeAnimatronics(world, a -> a.getSession() >= 0 && !a.belongsTo(state));
			// Participantes desconectados o ausentes no bloquean la derrota
			if (!state.getParticipants().isEmpty()) {
				boolean anyFree = false;
				for (UUID id : state.getParticipants()) {
					if (!state.isCaught(id) && isPresent(server, state, id)) anyFree = true;
				}
				if (!anyFree && pendingDefeatTicks < 0) pendingDefeatTicks = 20;
			}
		}

		if (t % 20 == 0) SpecialEvents.tick(world, state, def);
		if (t % 10 == 0) syncAll(server);
	}

	private static void tutorial(MinecraftServer server, CampaignState state, NightDefinition def) {
		int elapsed = ModConfig.get().gracePeriodSeconds * 20 - state.getGraceTicks();
		String key = switch (elapsed) {
			case 40 -> "tutorial.fnaf_night_shift.1";
			case 160 -> "tutorial.fnaf_night_shift.2";
			case 300 -> "tutorial.fnaf_night_shift.3";
			case 440 -> "tutorial.fnaf_night_shift.4";
			case 560 -> "tutorial.fnaf_night_shift.5";
			default -> null;
		};
		if (key == null) return;
		for (ServerPlayerEntity p : playersIn(server, state)) {
			p.sendMessage(Text.translatable(key).formatted(Formatting.YELLOW), false);
			p.playSound(ModSounds.FLASHLIGHT_CLICK, SoundCategory.MASTER, 0.6f, 1.5f);
		}
	}

	// ------------------------------------------------------------------
	// Animatrónicos
	// ------------------------------------------------------------------

	private static void spawnRoster(ServerWorld world, CampaignState state, NightDefinition def) {
		Set<BlockPos> used = new HashSet<>();
		for (AnimatronicKind kind : def.roster()) {
			BlockPos pos = findSpawnPos(world, state, kind, used);
			if (pos == null) continue;
			used.add(pos);
			spawn(world, kind, pos, state.getSession());
		}
	}

	private static void maintainRoster(ServerWorld world, CampaignState state, NightDefinition def) {
		Map<AnimatronicKind, Integer> expected = new EnumMap<>(AnimatronicKind.class);
		for (AnimatronicKind k : def.roster()) expected.merge(k, 1, Integer::sum);
		Map<AnimatronicKind, List<AnimatronicEntity>> present = new EnumMap<>(AnimatronicKind.class);
		for (Entity e : world.iterateEntities()) {
			if (e instanceof AnimatronicEntity a && a.isAlive() && a.belongsTo(state) && a.kind().isRegular()) {
				present.computeIfAbsent(a.kind(), k -> new ArrayList<>()).add(a);
			}
		}
		int cap = ModConfig.get().maxAnimatronicsPerType;
		Set<BlockPos> used = new HashSet<>();
		for (AnimatronicKind kind : AnimatronicKind.values()) {
			if (!kind.isRegular()) continue;
			int want = Math.min(expected.getOrDefault(kind, 0), cap);
			List<AnimatronicEntity> list = present.getOrDefault(kind, List.of());
			// Exceso: se eliminan los sobrantes (evita acumulaciones)
			for (int i = want; i < list.size(); i++) list.get(i).discard();
			// Faltantes (p. ej. muertos por /kill o descargados): reaparecen
			for (int i = list.size(); i < want; i++) {
				BlockPos pos = findSpawnPos(world, state, kind, used);
				if (pos != null && !visibleToAnyPlayer(world, pos)) {
					used.add(pos);
					spawn(world, kind, pos, state.getSession());
				}
			}
		}
	}

	@Nullable
	public static AnimatronicEntity spawn(ServerWorld world, AnimatronicKind kind, BlockPos pos, int session) {
		EntityType<? extends AnimatronicEntity> type = ModEntities.typeOf(kind);
		AnimatronicEntity e = type.create(world);
		if (e == null) return null;
		e.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360f, 0);
		e.initialize(world, world.getLocalDifficulty(pos), SpawnReason.EVENT, null, null);
		e.setHome(pos);
		e.setSession(session);
		e.setPersistent();
		world.spawnEntity(e);
		return e;
	}

	@Nullable
	private static BlockPos findSpawnPos(ServerWorld world, CampaignState state, AnimatronicKind kind, Set<BlockPos> used) {
		List<BlockPos> exact = new ArrayList<>();
		List<BlockPos> any = new ArrayList<>();
		state.getSpawns().forEach((p, k) -> {
			if (used.contains(p)) return;
			if (k == kind) exact.add(p);
			else if (k == AnimatronicKind.ANY) any.add(p);
		});
		Collections.shuffle(exact);
		Collections.shuffle(any);
		for (BlockPos p : exact) if (isSpawnable(world, p)) return p;
		for (BlockPos p : any) if (isSpawnable(world, p)) return p;

		BlockPos shelter = state.getShelterPos();
		List<BlockPos> nodes = new ArrayList<>(state.getNodes(world, shelter != null ? shelter : BlockPos.ORIGIN, 200));
		Collections.shuffle(nodes);
		for (BlockPos p : nodes) {
			if (used.contains(p)) continue;
			if (shelter != null && p.getSquaredDistance(shelter) < 24 * 24) continue;
			if (isSpawnable(world, p)) return p;
		}
		if (shelter != null) {
			for (int i = 0; i < 20; i++) {
				double angle = world.random.nextDouble() * Math.PI * 2;
				int x = shelter.getX() + (int) (Math.cos(angle) * 32);
				int z = shelter.getZ() + (int) (Math.sin(angle) * 32);
				BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
				for (int y = shelter.getY() + 4; y >= shelter.getY() - 8; y--) {
					BlockPos p = new BlockPos(x, y, z);
					if (isSpawnable(world, p)) return p;
				}
				if (isSpawnable(world, top)) return top;
			}
		}
		return null;
	}

	private static boolean isSpawnable(ServerWorld world, BlockPos p) {
		for (int dy = 0; dy < 3; dy++) {
			if (!world.getBlockState(p.up(dy)).getCollisionShape(world, p.up(dy)).isEmpty()) return false;
		}
		return !world.getBlockState(p.down()).getCollisionShape(world, p.down()).isEmpty();
	}

	private static boolean visibleToAnyPlayer(ServerWorld world, BlockPos pos) {
		Vec3d target = Vec3d.ofCenter(pos).add(0, 1, 0);
		for (PlayerEntity p : world.getPlayers()) {
			if (p.isSpectator()) continue;
			if (p.getEyePos().squaredDistanceTo(target) < 10 * 10) return true;
			var hit = world.raycast(new net.minecraft.world.RaycastContext(p.getEyePos(), target,
					net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, p));
			if (hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS && p.getEyePos().squaredDistanceTo(target) < 48 * 48) return true;
		}
		return false;
	}

	public static int removeAnimatronics(ServerWorld world, java.util.function.Predicate<AnimatronicEntity> filter) {
		List<AnimatronicEntity> remove = new ArrayList<>();
		for (Entity e : world.iterateEntities()) {
			if (e instanceof AnimatronicEntity a && filter.test(a)) remove.add(a);
		}
		remove.forEach(Entity::discard);
		return remove.size();
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	public static void teleportToShelter(ServerPlayerEntity p, ServerWorld world, BlockPos shelter) {
		double angle = p.getRandom().nextDouble() * Math.PI * 2;
		double r = 1.5 + p.getRandom().nextDouble() * 1.5;
		double x = shelter.getX() + 0.5 + Math.cos(angle) * r;
		double z = shelter.getZ() + 0.5 + Math.sin(angle) * r;
		BlockPos target = BlockPos.ofFloored(x, shelter.getY(), z);
		// buscar un hueco libre cerca del núcleo
		for (int dy = 0; dy <= 3; dy++) {
			BlockPos t = target.up(dy);
			if (world.getBlockState(t).getCollisionShape(world, t).isEmpty() && world.getBlockState(t.up()).getCollisionShape(world, t.up()).isEmpty()) {
				p.teleport(world, x, t.getY(), z, p.getYaw(), p.getPitch());
				return;
			}
		}
		p.teleport(world, shelter.getX() + 0.5, shelter.getY() + 1, shelter.getZ() + 0.5, p.getYaw(), p.getPitch());
	}

	public static void giveStarterKit(ServerPlayerEntity p) {
		giveIfMissing(p, ModItems.FLASHLIGHT, 1);
		giveIfMissing(p, ModItems.WRENCH, 1);
		giveIfMissing(p, ModItems.SCREWDRIVER, 1);
		giveIfMissing(p, ModItems.PLIERS, 1);
		topUp(p, ModItems.FUSE, 3);
		topUp(p, ModItems.CABLE, 2);
		topUp(p, ModItems.ELECTRONIC_PARTS, 1);
		for (int i = 0; i < p.getInventory().size(); i++) {
			ItemStack s = p.getInventory().getStack(i);
			if (s.isOf(ModItems.FLASHLIGHT)) FlashlightItem.setBattery(s, FlashlightItem.maxBattery(p.getWorld()));
		}
	}

	private static void giveIfMissing(ServerPlayerEntity p, Item item, int count) {
		if (p.getInventory().count(item) == 0) {
			ItemStack stack = new ItemStack(item, count);
			if (!p.getInventory().insertStack(stack)) p.dropItem(stack, false);
		}
	}

	private static void topUp(ServerPlayerEntity p, Item item, int min) {
		int have = p.getInventory().count(item);
		if (have < min) {
			ItemStack stack = new ItemStack(item, min - have);
			if (!p.getInventory().insertStack(stack)) p.dropItem(stack, false);
		}
	}

	public static List<ServerPlayerEntity> playersIn(MinecraftServer server, CampaignState state) {
		List<ServerPlayerEntity> list = new ArrayList<>();
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (state.getParticipants().contains(p.getUuid()) || state.isFacilityWorld(p.getWorld())) list.add(p);
		}
		return list;
	}

	public static void title(ServerPlayerEntity p, Text title, Text subtitle) {
		p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 50, 15));
		p.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
		p.networkHandler.sendPacket(new TitleS2CPacket(title));
	}

	private static void feedback(MinecraftServer server, @Nullable ServerPlayerEntity player, Text text) {
		if (player != null) player.sendMessage(text, false);
		else FnafNightShift.LOGGER.info(text.getString());
	}

	public static void syncAll(MinecraftServer server) {
		CampaignState state = CampaignState.get(server);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			ModPackets.sendNightState(p, state);
		}
	}

	/** Nivel de peligro para la interfaz: 0 tranquilo, 1 cerca, 2 persiguiéndote. */
	public static int dangerLevel(ServerPlayerEntity player) {
		int level = 0;
		for (AnimatronicEntity a : player.getServerWorld().getEntitiesByClass(AnimatronicEntity.class, player.getBoundingBox().expand(24), Entity::isAlive)) {
			AiState s = a.getAi().getState();
			if ((s == AiState.CHASE || s == AiState.ATTACK || s == AiState.DETECT) && player.getUuid().equals(a.getAi().getTargetId())) return 2;
			if (a.squaredDistanceTo(player) < 16 * 16) level = 1;
		}
		return level;
	}

	public static void sendStatus(ServerPlayerEntity p) {
		CampaignState state = CampaignState.get(p.getServer());
		p.sendMessage(statusText(state), false);
	}

	public static Text statusText(CampaignState s) {
		Text phase = Text.translatable("phase.fnaf_night_shift." + s.getPhase().name().toLowerCase());
		int secs = s.getTicksRemaining() / 20;
		return Text.translatable("message.fnaf_night_shift.status",
				s.getPhase().isActive() ? s.getActiveNight() : s.getCurrentNight(), phase,
				s.getCompleted().size(), s.getRequired().size(),
				String.format("%d:%02d", secs / 60, secs % 60),
				Text.translatable(s.getDifficulty().translationKey()),
				s.getHighestUnlocked(), s.getStations().size(), s.nodeCount(), s.getSpawns().size(),
				s.getShelterPos() == null ? "-" : s.getShelterPos().toShortString()).formatted(Formatting.AQUA);
	}
}
