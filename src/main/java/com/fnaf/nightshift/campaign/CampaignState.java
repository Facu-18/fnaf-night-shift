package com.fnaf.nightshift.campaign;

import com.fnaf.nightshift.FnafNightShift;
import com.fnaf.nightshift.block.Zone;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.entity.AnimatronicKind;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Estado persistente de la campaña, guardado en el mundo
 * (data/fnaf_night_shift_campaign.dat del Overworld). Contiene el progreso,
 * la noche en curso y el registro de instalaciones y marcadores del mapa.
 */
public class CampaignState extends PersistentState {
	private static final String KEY = "fnaf_night_shift_campaign";

	// --- Mapa ---
	@Nullable
	private String dimension;
	@Nullable
	private BlockPos shelter;
	private final Set<BlockPos> stations = new LinkedHashSet<>();
	private final Map<BlockPos, Zone> nodes = new HashMap<>();
	private final Map<BlockPos, AnimatronicKind> spawns = new HashMap<>();
	private final Set<BlockPos> crates = new LinkedHashSet<>();
	private final Set<BlockPos> lockers = new LinkedHashSet<>();

	// --- Progreso ---
	private int currentNight = 1;
	private int highestUnlocked = 1;
	private int nightsCompletedMask;
	private Difficulty difficulty = Difficulty.parse(ModConfig.get().defaultDifficulty, Difficulty.NORMAL);
	private int totalCatches;

	// --- Noche en curso ---
	private NightPhase phase = NightPhase.NONE;
	private int session;
	private int activeNight;
	private int ticksRemaining;
	private int totalTicks;
	private int graceTicks;
	private boolean spawned;
	private final Set<BlockPos> required = new LinkedHashSet<>();
	private final Set<BlockPos> completed = new HashSet<>();
	private final Set<UUID> participants = new HashSet<>();
	private final Set<UUID> caught = new HashSet<>();
	private int nightCatches;

	// --- Sistemas añadidos en la actualización 1.1 (todos opcionales en NBT) ---
	/** Puertas de la oficina forzadas/atascadas (mitad inferior). */
	private final Set<BlockPos> jammedDoors = new LinkedHashSet<>();
	/** Averías adicionales activas: instalación -> consecuencia aplicada. */
	private final Map<BlockPos, String> extraBreakdowns = new HashMap<>();
	/** Instalaciones que ya sufrieron una avería adicional esta noche (evita repeticiones). */
	private final Set<BlockPos> extraHistory = new HashSet<>();
	private int extraCount;
	private int nextExtraTick;
	private int ventTicks;
	private boolean ventFailed;
	private int ventFailures;

	public static CampaignState get(MinecraftServer server) {
		ServerWorld overworld = server.getOverworld();
		return overworld.getPersistentStateManager().getOrCreate(CampaignState::fromNbt, CampaignState::new, KEY);
	}

	// ------------------------------------------------------------------
	// Dimensión del complejo
	// ------------------------------------------------------------------

	private String dimId(World world) {
		return world.getRegistryKey().getValue().toString();
	}

	/** ¿Acepta registros de este mundo? Fija la dimensión la primera vez. */
	private boolean accepts(World world) {
		String id = dimId(world);
		if (dimension == null) {
			dimension = id;
			markDirty();
			return true;
		}
		return dimension.equals(id);
	}

	public boolean isFacilityWorld(World world) {
		return dimension == null || dimension.equals(dimId(world));
	}

	@Nullable
	public String getDimension() {
		return dimension;
	}

	@Nullable
	public ServerWorld getFacilityWorld(MinecraftServer server) {
		if (dimension == null) return server.getOverworld();
		for (ServerWorld w : server.getWorlds()) {
			if (dimId(w).equals(dimension)) return w;
		}
		return server.getOverworld();
	}

	// ------------------------------------------------------------------
	// Registro de bloques del mapa
	// ------------------------------------------------------------------

	public void setShelter(ServerWorld world, BlockPos pos) {
		String id = dimId(world);
		if (dimension != null && !dimension.equals(id)) {
			FnafNightShift.LOGGER.info("El refugio se movió a otra dimensión ({}); se reinicia el registro del mapa.", id);
			stations.clear();
			nodes.clear();
			spawns.clear();
			crates.clear();
		}
		dimension = id;
		shelter = pos.toImmutable();
		markDirty();
	}

	public void clearShelterIf(ServerWorld world, BlockPos pos) {
		if (shelter != null && shelter.equals(pos) && isFacilityWorld(world)) {
			shelter = null;
			markDirty();
		}
	}

	public boolean isShelter(ServerWorld world, BlockPos pos) {
		return shelter != null && shelter.equals(pos) && isFacilityWorld(world);
	}

	@Nullable
	public BlockPos getShelter(World world) {
		return isFacilityWorld(world) ? shelter : null;
	}

	@Nullable
	public BlockPos getShelterPos() {
		return shelter;
	}

	public int shelterRadius() {
		return ModConfig.get().shelterRadius;
	}

	public boolean isInShelter(PlayerEntity player) {
		BlockPos s = getShelter(player.getWorld());
		if (s == null) return false;
		double r = shelterRadius();
		return player.getBlockPos().getSquaredDistance(s) <= r * r && Math.abs(player.getBlockY() - s.getY()) < 5;
	}

	public void registerStation(ServerWorld world, BlockPos pos) {
		if (accepts(world) && stations.add(pos.toImmutable())) markDirty();
	}

	public void unregisterStation(ServerWorld world, BlockPos pos) {
		if (isFacilityWorld(world) && stations.remove(pos)) {
			if (required.remove(pos)) completed.remove(pos);
			markDirty();
		}
	}

	public void registerNode(ServerWorld world, BlockPos pos, Zone zone) {
		if (accepts(world)) {
			nodes.put(pos.toImmutable(), zone);
			markDirty();
		}
	}

	public void unregisterNode(ServerWorld world, BlockPos pos) {
		if (isFacilityWorld(world) && nodes.remove(pos) != null) markDirty();
	}

	public void registerSpawn(ServerWorld world, BlockPos pos, AnimatronicKind kind) {
		if (accepts(world)) {
			spawns.put(pos.toImmutable(), kind);
			markDirty();
		}
	}

	public void unregisterSpawn(ServerWorld world, BlockPos pos) {
		if (isFacilityWorld(world) && spawns.remove(pos) != null) markDirty();
	}

	public void registerCrate(ServerWorld world, BlockPos pos) {
		if (accepts(world) && crates.add(pos.toImmutable())) markDirty();
	}

	public void unregisterCrate(ServerWorld world, BlockPos pos) {
		if (isFacilityWorld(world) && crates.remove(pos)) markDirty();
	}

	public Set<BlockPos> getStations() {
		return stations;
	}

	public Set<BlockPos> getCrates() {
		return crates;
	}

	public void registerLocker(ServerWorld world, BlockPos pos) {
		if (accepts(world) && lockers.add(pos.toImmutable())) markDirty();
	}

	public void unregisterLocker(ServerWorld world, BlockPos pos) {
		if (isFacilityWorld(world) && lockers.remove(pos)) markDirty();
	}

	public Set<BlockPos> getLockers() {
		return lockers;
	}

	public Set<BlockPos> getJammedDoors() {
		return jammedDoors;
	}

	public boolean isDoorJammed(BlockPos lowerHalf) {
		return phase.isActive() && jammedDoors.contains(lowerHalf);
	}

	public Map<BlockPos, String> getExtraBreakdowns() {
		return extraBreakdowns;
	}

	public Set<BlockPos> getExtraHistory() {
		return extraHistory;
	}

	public int getExtraCount() {
		return extraCount;
	}

	public void setExtraCount(int c) {
		extraCount = c;
		markDirty();
	}

	public int getNextExtraTick() {
		return nextExtraTick;
	}

	public void setNextExtraTick(int t) {
		nextExtraTick = t;
		markDirty();
	}

	public int getVentTicks() {
		return ventTicks;
	}

	public void setVentTicks(int t) {
		ventTicks = Math.max(0, t);
		if (ventTicks % 100 == 0) markDirty();
	}

	public boolean isVentFailed() {
		return ventFailed;
	}

	public void setVentFailed(boolean f) {
		ventFailed = f;
		markDirty();
	}

	public int getVentFailures() {
		return ventFailures;
	}

	public void setVentFailures(int f) {
		ventFailures = f;
		markDirty();
	}

	/** Ticks transcurridos de la noche actual. */
	public int getElapsedTicks() {
		return totalTicks - ticksRemaining;
	}

	public List<BlockPos> getNodes(World world, BlockPos around, double radius) {
		List<BlockPos> list = new ArrayList<>();
		if (!isFacilityWorld(world)) return list;
		double r2 = radius * radius;
		for (BlockPos p : nodes.keySet()) {
			if (p.getSquaredDistance(around) <= r2) list.add(p);
		}
		return list;
	}

	public int nodeCount() {
		return nodes.size();
	}

	@Nullable
	public Zone getNodeZone(BlockPos pos) {
		return nodes.get(pos);
	}

	public List<BlockPos> getSpawnPositions(World world) {
		return isFacilityWorld(world) ? new ArrayList<>(spawns.keySet()) : new ArrayList<>();
	}

	public Map<BlockPos, AnimatronicKind> getSpawns() {
		return spawns;
	}

	// ------------------------------------------------------------------
	// Progreso
	// ------------------------------------------------------------------

	public int getCurrentNight() {
		return currentNight;
	}

	public void setCurrentNight(int night) {
		currentNight = Math.max(1, Math.min(NightDefinition.MAX_NIGHT, night));
		highestUnlocked = Math.max(highestUnlocked, currentNight);
		markDirty();
	}

	public int getHighestUnlocked() {
		return highestUnlocked;
	}

	public int getCompletedMask() {
		return nightsCompletedMask;
	}

	public boolean isNightCompleted(int night) {
		return (nightsCompletedMask & (1 << night)) != 0;
	}

	public void markNightCompleted(int night) {
		nightsCompletedMask |= 1 << night;
		if (night < NightDefinition.MAX_NIGHT) {
			highestUnlocked = Math.max(highestUnlocked, night + 1);
			currentNight = night + 1;
		}
		markDirty();
	}

	public Difficulty getDifficulty() {
		return difficulty;
	}

	public void setDifficulty(Difficulty d) {
		difficulty = d;
		markDirty();
	}

	public int getTotalCatches() {
		return totalCatches;
	}

	public void resetProgress() {
		currentNight = 1;
		highestUnlocked = 1;
		nightsCompletedMask = 0;
		totalCatches = 0;
		markDirty();
	}

	// ------------------------------------------------------------------
	// Noche en curso
	// ------------------------------------------------------------------

	public NightPhase getPhase() {
		return phase;
	}

	public void setPhase(NightPhase p) {
		phase = p;
		markDirty();
	}

	public int getSession() {
		return session;
	}

	public int getActiveNight() {
		return activeNight;
	}

	public int getTicksRemaining() {
		return ticksRemaining;
	}

	public void setTicksRemaining(int t) {
		ticksRemaining = Math.max(0, t);
		if (ticksRemaining % 100 == 0) markDirty();
	}

	public int getTotalTicks() {
		return totalTicks;
	}

	public int getGraceTicks() {
		return graceTicks;
	}

	public void setGraceTicks(int t) {
		graceTicks = Math.max(0, t);
	}

	public boolean isSpawned() {
		return spawned;
	}

	public void setSpawned(boolean s) {
		spawned = s;
		markDirty();
	}

	public Set<BlockPos> getRequired() {
		return required;
	}

	public Set<BlockPos> getCompleted() {
		return completed;
	}

	public Set<UUID> getParticipants() {
		return participants;
	}

	public boolean isCaught(UUID id) {
		return phase.isActive() && caught.contains(id);
	}

	public Set<UUID> getCaught() {
		return caught;
	}

	public int getNightCatches() {
		return nightCatches;
	}

	public void addCatch(UUID id) {
		caught.add(id);
		nightCatches++;
		totalCatches++;
		markDirty();
	}

	/** Empieza una nueva sesión de noche y limpia el estado temporal. */
	public void beginNight(int night, int durationTicks, int grace) {
		session++;
		activeNight = night;
		currentNight = night;
		totalTicks = durationTicks;
		ticksRemaining = durationTicks;
		graceTicks = grace;
		spawned = false;
		required.clear();
		completed.clear();
		participants.clear();
		caught.clear();
		nightCatches = 0;
		jammedDoors.clear();
		extraBreakdowns.clear();
		extraHistory.clear();
		extraCount = 0;
		nextExtraTick = durationTicks / 3;
		ventTicks = 0;
		ventFailed = false;
		ventFailures = 0;
		phase = NightPhase.REPAIRING;
		markDirty();
	}

	public void endNight(NightPhase result) {
		phase = result;
		session++; // invalida a los animatrónicos de la noche
		markDirty();
	}

	// ------------------------------------------------------------------
	// NBT
	// ------------------------------------------------------------------

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		if (dimension != null) nbt.putString("Dimension", dimension);
		if (shelter != null) nbt.putLong("Shelter", shelter.asLong());
		nbt.putLongArray("Stations", stations.stream().mapToLong(BlockPos::asLong).toArray());
		nbt.putLongArray("Crates", crates.stream().mapToLong(BlockPos::asLong).toArray());
		NbtList nodeList = new NbtList();
		nodes.forEach((p, z) -> {
			NbtCompound c = new NbtCompound();
			c.putLong("P", p.asLong());
			c.putString("Z", z.asString());
			nodeList.add(c);
		});
		nbt.put("Nodes", nodeList);
		NbtList spawnList = new NbtList();
		spawns.forEach((p, k) -> {
			NbtCompound c = new NbtCompound();
			c.putLong("P", p.asLong());
			c.putString("K", k.id());
			spawnList.add(c);
		});
		nbt.put("Spawns", spawnList);

		nbt.putInt("CurrentNight", currentNight);
		nbt.putInt("HighestUnlocked", highestUnlocked);
		nbt.putInt("Completed", nightsCompletedMask);
		nbt.putString("Difficulty", difficulty.name());
		nbt.putInt("TotalCatches", totalCatches);

		nbt.putString("Phase", phase.name());
		nbt.putInt("Session", session);
		nbt.putInt("ActiveNight", activeNight);
		nbt.putInt("TicksRemaining", ticksRemaining);
		nbt.putInt("TotalTicks", totalTicks);
		nbt.putInt("GraceTicks", graceTicks);
		nbt.putBoolean("Spawned", spawned);
		nbt.putLongArray("Required", required.stream().mapToLong(BlockPos::asLong).toArray());
		nbt.putLongArray("Done", completed.stream().mapToLong(BlockPos::asLong).toArray());
		nbt.put("Participants", uuidList(participants));
		nbt.put("Caught", uuidList(caught));
		nbt.putInt("NightCatches", nightCatches);
		nbt.putLongArray("Lockers", lockers.stream().mapToLong(BlockPos::asLong).toArray());
		nbt.putLongArray("JammedDoors", jammedDoors.stream().mapToLong(BlockPos::asLong).toArray());
		NbtList extras = new NbtList();
		extraBreakdowns.forEach((p, c) -> {
			NbtCompound e = new NbtCompound();
			e.putLong("P", p.asLong());
			e.putString("C", c);
			extras.add(e);
		});
		nbt.put("Extras", extras);
		nbt.putLongArray("ExtraHistory", extraHistory.stream().mapToLong(BlockPos::asLong).toArray());
		nbt.putInt("ExtraCount", extraCount);
		nbt.putInt("NextExtra", nextExtraTick);
		nbt.putInt("VentTicks", ventTicks);
		nbt.putBoolean("VentFailed", ventFailed);
		nbt.putInt("VentFailures", ventFailures);
		return nbt;
	}

	private static NbtList uuidList(Set<UUID> set) {
		NbtList list = new NbtList();
		for (UUID u : set) list.add(NbtString.of(u.toString()));
		return list;
	}

	private static void readUuids(NbtList list, Set<UUID> into) {
		for (int i = 0; i < list.size(); i++) {
			try {
				into.add(UUID.fromString(list.getString(i)));
			} catch (IllegalArgumentException ignored) {
			}
		}
	}

	public static CampaignState fromNbt(NbtCompound nbt) {
		CampaignState s = new CampaignState();
		s.dimension = nbt.contains("Dimension") ? nbt.getString("Dimension") : null;
		s.shelter = nbt.contains("Shelter") ? BlockPos.fromLong(nbt.getLong("Shelter")) : null;
		for (long l : nbt.getLongArray("Stations")) s.stations.add(BlockPos.fromLong(l));
		for (long l : nbt.getLongArray("Crates")) s.crates.add(BlockPos.fromLong(l));
		NbtList nodeList = nbt.getList("Nodes", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < nodeList.size(); i++) {
			NbtCompound c = nodeList.getCompound(i);
			Zone z = Zone.GENERAL;
			for (Zone zz : Zone.values()) if (zz.asString().equals(c.getString("Z"))) z = zz;
			s.nodes.put(BlockPos.fromLong(c.getLong("P")), z);
		}
		NbtList spawnList = nbt.getList("Spawns", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < spawnList.size(); i++) {
			NbtCompound c = spawnList.getCompound(i);
			AnimatronicKind k = AnimatronicKind.byId(c.getString("K"));
			s.spawns.put(BlockPos.fromLong(c.getLong("P")), k == null ? AnimatronicKind.ANY : k);
		}

		s.currentNight = Math.max(1, nbt.getInt("CurrentNight"));
		s.highestUnlocked = Math.max(1, nbt.getInt("HighestUnlocked"));
		s.nightsCompletedMask = nbt.getInt("Completed");
		s.difficulty = Difficulty.parse(nbt.getString("Difficulty"), Difficulty.NORMAL);
		s.totalCatches = nbt.getInt("TotalCatches");

		try {
			s.phase = NightPhase.valueOf(nbt.getString("Phase"));
		} catch (IllegalArgumentException e) {
			s.phase = NightPhase.NONE;
		}
		s.session = nbt.getInt("Session");
		s.activeNight = nbt.getInt("ActiveNight");
		s.ticksRemaining = nbt.getInt("TicksRemaining");
		s.totalTicks = nbt.getInt("TotalTicks");
		s.graceTicks = nbt.getInt("GraceTicks");
		s.spawned = nbt.getBoolean("Spawned");
		for (long l : nbt.getLongArray("Required")) s.required.add(BlockPos.fromLong(l));
		for (long l : nbt.getLongArray("Done")) s.completed.add(BlockPos.fromLong(l));
		readUuids(nbt.getList("Participants", NbtElement.STRING_TYPE), s.participants);
		readUuids(nbt.getList("Caught", NbtElement.STRING_TYPE), s.caught);
		s.nightCatches = nbt.getInt("NightCatches");
		// Campos nuevos: si faltan (partidas antiguas) quedan con sus valores por defecto.
		for (long l : nbt.getLongArray("Lockers")) s.lockers.add(BlockPos.fromLong(l));
		for (long l : nbt.getLongArray("JammedDoors")) s.jammedDoors.add(BlockPos.fromLong(l));
		NbtList extras = nbt.getList("Extras", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < extras.size(); i++) {
			NbtCompound e = extras.getCompound(i);
			s.extraBreakdowns.put(BlockPos.fromLong(e.getLong("P")), e.getString("C"));
		}
		for (long l : nbt.getLongArray("ExtraHistory")) s.extraHistory.add(BlockPos.fromLong(l));
		s.extraCount = nbt.getInt("ExtraCount");
		s.nextExtraTick = nbt.getInt("NextExtra");
		s.ventTicks = nbt.getInt("VentTicks");
		s.ventFailed = nbt.getBoolean("VentFailed");
		s.ventFailures = nbt.getInt("VentFailures");
		return s;
	}
}
