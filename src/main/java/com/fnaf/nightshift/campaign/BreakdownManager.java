package com.fnaf.nightshift.campaign;

import com.fnaf.nightshift.block.FlickeringLightBlock;
import com.fnaf.nightshift.registry.ModSounds;
import com.fnaf.nightshift.repair.RepairStationBlockEntity;
import com.fnaf.nightshift.repair.RepairType;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Averías adicionales durante la noche. Reutiliza las instalaciones
 * reparables existentes: una avería adicional rompe una instalación que
 * estaba operativa, la añade a los objetivos obligatorios y aplica una
 * consecuencia real hasta que se repara:
 * <ul>
 *   <li>generador / fusibles / tablero → corte de luz en la oficina</li>
 *   <li>control de puerta automática → una puerta de la oficina queda atascada abierta</li>
 *   <li>ventilación → el mantenimiento de la oficina se acelera</li>
 *   <li>equipo de seguridad / terminal / cableado → interferencias en todas las cámaras</li>
 *   <li>iluminación → las lámparas de la zona parpadean</li>
 *   <li>cámara → esa cámara pierde la señal</li>
 * </ul>
 * Nunca se programan cuando queda menos tiempo que la ventana de respuesta
 * de la noche, como máximo dos pendientes a la vez y nunca dos veces la
 * misma instalación en una noche: la noche siempre se puede completar.
 */
public final class BreakdownManager {
	public static final String POWER = "power";
	public static final String VENT = "vent";
	public static final String CAMERAS = "cameras";
	public static final String LIGHTS = "lights";
	public static final String CAMERA = "camera";
	public static final String DOOR_PREFIX = "door:";

	private BreakdownManager() {
	}

	public static void tick(ServerWorld world, CampaignState s, NightDefinition def) {
		if (def.maxExtraBreakdowns() <= 0 || s.getGraceTicks() > 0 || !s.isSpawned()) return;
		if (s.getExtraCount() >= def.maxExtraBreakdowns()) return;
		if (s.getTicksRemaining() < def.extraResponseTicks()) return;
		if (s.getElapsedTicks() < s.getNextExtraTick()) return;
		if (pendingExtras(s) >= 2) return;

		List<BlockPos> candidates = new ArrayList<>();
		for (BlockPos p : s.getStations()) {
			if (s.getExtraHistory().contains(p)) continue;
			if (world.getBlockEntity(p) instanceof RepairStationBlockEntity be && !be.isBroken()) candidates.add(p);
		}
		if (candidates.isEmpty()) return;
		BlockPos pick = candidates.get(world.random.nextInt(candidates.size()));
		trigger(world, s, def, pick, null);
		int gap = (int) ((60 + world.random.nextInt(60)) * 20 / Math.max(0.6, def.aggression()));
		s.setNextExtraTick(s.getElapsedTicks() + gap);
	}

	public static long pendingExtras(CampaignState s) {
		return s.getExtraBreakdowns().keySet().stream().filter(p -> !s.getCompleted().contains(p)).count();
	}

	/**
	 * Avería de un tipo concreto (p. ej. al forzar una puerta). Sólo se hace
	 * obligatoria si queda tiempo para repararla. Devuelve false si no se pudo.
	 */
	public static boolean triggerOfType(ServerWorld world, CampaignState s, NightDefinition def, RepairType type, String consequence) {
		if (!s.getPhase().isActive() || s.getTicksRemaining() < def.extraResponseTicks()) return false;
		for (BlockPos p : s.getStations()) {
			if (world.getBlockEntity(p) instanceof RepairStationBlockEntity be && be.getRepairType() == type && !be.isBroken()) {
				return trigger(world, s, def, p, consequence);
			}
		}
		return false;
	}

	public static boolean trigger(ServerWorld world, CampaignState s, NightDefinition def, BlockPos pos, @Nullable String forced) {
		if (!(world.getBlockEntity(pos) instanceof RepairStationBlockEntity be) || be.isBroken()) return false;
		RepairType type = be.getRepairType();
		be.breakStation(world.random.nextLong(), def.puzzleLevel(s.getDifficulty()));
		s.getRequired().add(pos.toImmutable());
		s.getCompleted().remove(pos);
		s.getExtraHistory().add(pos.toImmutable());
		s.setExtraCount(s.getExtraCount() + 1);
		String consequence = forced != null ? forced : apply(world, s, type);
		s.getExtraBreakdowns().put(pos.toImmutable(), consequence);
		if (s.getPhase() == NightPhase.RETURN_TO_SHELTER) s.setPhase(NightPhase.REPAIRING);
		s.markDirty();

		for (ServerPlayerEntity p : NightManager.playersIn(world.getServer(), s)) {
			int dist = (int) Math.sqrt(p.getBlockPos().getSquaredDistance(pos));
			p.sendMessage(Text.translatable("message.fnaf_night_shift.malfunction", Text.translatable(type.translationKey()), dist,
					Text.translatable("consequence.fnaf_night_shift." + consequenceKey(consequence))).formatted(Formatting.RED), false);
			p.playSound(ModSounds.MALFUNCTION, SoundCategory.MASTER, 0.9f, 1.0f);
		}
		NightManager.syncAll(world.getServer());
		return true;
	}

	private static String consequenceKey(String c) {
		return c.startsWith(DOOR_PREFIX) ? "door" : c;
	}

	private static String apply(ServerWorld world, CampaignState s, RepairType type) {
		BlockPos shelter = s.getShelter(world);
		switch (type) {
			case GENERATOR, FUSE_BOX, ELECTRICAL_PANEL -> {
				if (shelter != null) FlickeringLightBlock.setAreaFaulty(world, shelter, s.shelterRadius() + 2, true);
				return POWER;
			}
			case AUTO_DOOR -> {
				for (OfficeManager.OfficeDoor door : OfficeManager.doors(world, s)) {
					if (s.getJammedDoors().contains(door.lower())) continue;
					BlockState st = world.getBlockState(door.lower());
					if (st.getBlock() instanceof DoorBlock d) d.setOpen(null, world, st, door.lower(), true);
					s.getJammedDoors().add(door.lower());
					return DOOR_PREFIX + door.lower().asLong();
				}
				return LIGHTS;
			}
			case VENTILATION -> {
				s.setVentTicks((int) (s.getVentTicks() * 0.6));
				return VENT;
			}
			case SECURITY, TERMINAL, WIRING -> {
				return CAMERAS;
			}
			case CAMERA -> {
				return CAMERA;
			}
			default -> {
				return LIGHTS;
			}
		}
	}

	/** Llamado al reparar una instalación: revierte la consecuencia de su avería adicional. */
	public static void onRepaired(ServerWorld world, CampaignState s, BlockPos pos) {
		String c = s.getExtraBreakdowns().remove(pos);
		if (c == null) return;
		revert(world, s, c);
		s.markDirty();
	}

	private static void revert(ServerWorld world, CampaignState s, String c) {
		if (c.equals(POWER)) {
			boolean stillOut = s.getExtraBreakdowns().entrySet().stream()
					.anyMatch(e -> e.getValue().equals(POWER) && !s.getCompleted().contains(e.getKey()));
			BlockPos shelter = s.getShelter(world);
			if (!stillOut && shelter != null) FlickeringLightBlock.setAreaFaulty(world, shelter, s.shelterRadius() + 2, false);
		} else if (c.startsWith(DOOR_PREFIX)) {
			try {
				OfficeManager.unjam(s, BlockPos.fromLong(Long.parseLong(c.substring(DOOR_PREFIX.length()))));
			} catch (NumberFormatException ignored) {
			}
		}
	}

	/** ¿Las cámaras sufren interferencias por una avería pendiente? */
	public static boolean camerasDisrupted(CampaignState s) {
		for (Map.Entry<BlockPos, String> e : s.getExtraBreakdowns().entrySet()) {
			if (e.getValue().equals(CAMERAS) && !s.getCompleted().contains(e.getKey())) return true;
		}
		return false;
	}

	public static boolean ventDegraded(CampaignState s) {
		for (Map.Entry<BlockPos, String> e : s.getExtraBreakdowns().entrySet()) {
			if (e.getValue().equals(VENT) && !s.getCompleted().contains(e.getKey())) return true;
		}
		return false;
	}

	/** Al terminar la noche se deshacen todas las consecuencias. */
	public static void revertAll(ServerWorld world, CampaignState s) {
		for (String c : new ArrayList<>(s.getExtraBreakdowns().values())) revert(world, s, c);
		s.getExtraBreakdowns().clear();
		s.getJammedDoors().clear();
		BlockPos shelter = s.getShelter(world);
		if (shelter != null) FlickeringLightBlock.setAreaFaulty(world, shelter, s.shelterRadius() + 2, false);
		OfficeManager.reset();
		s.markDirty();
	}
}
