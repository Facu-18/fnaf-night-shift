package com.fnaf.nightshift.client;

import com.fnaf.nightshift.campaign.NightPhase;
import com.fnaf.nightshift.repair.RepairType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Copia en el cliente del estado de la noche (sólo para mostrarlo). */
public final class ClientNightState {
	public record Objective(BlockPos pos, RepairType type, boolean done, boolean extra) {
	}

	public record Snapshot(NightPhase phase, int night, int ticksRemaining, int totalTicks, int graceTicks, int completed,
						   int required, boolean caught, int danger, boolean inShelter, @Nullable BlockPos shelter,
						   List<Objective> objectives, boolean ventEnabled, int ventTicks, int ventInterval, boolean ventFailed,
						   Set<String> lurking, int jammedDoors, boolean viewingCameras) {
	}

	public static NightPhase phase = NightPhase.NONE;
	public static int night = 1;
	public static int ticksRemaining;
	public static int totalTicks = 1;
	public static int graceTicks;
	public static int completed;
	public static int required;
	public static boolean caught;
	public static int danger;
	public static boolean inShelter;
	@Nullable
	public static BlockPos shelter;
	public static List<Objective> objectives = new ArrayList<>();
	// 1.1
	public static boolean ventEnabled;
	public static int ventTicks;
	public static int ventInterval = 1;
	public static boolean ventFailed;
	public static Set<String> lurking = Set.of();
	public static int jammedDoors;
	public static float stamina = 1f;
	public static boolean exhausted;
	/** Tick de cliente de la última actualización, para interpolar el reloj. */
	public static long lastUpdate;

	private ClientNightState() {
	}

	public static Snapshot read(PacketByteBuf buf) {
		NightPhase phase = NightPhase.byOrdinal(buf.readVarInt());
		int night = buf.readVarInt();
		int remaining = buf.readVarInt();
		int total = buf.readVarInt();
		int grace = buf.readVarInt();
		int completed = buf.readVarInt();
		int required = buf.readVarInt();
		boolean caught = buf.readBoolean();
		int danger = buf.readVarInt();
		boolean inShelter = buf.readBoolean();
		BlockPos shelter = buf.readBoolean() ? buf.readBlockPos() : null;
		int n = buf.readVarInt();
		List<Objective> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			BlockPos p = buf.readBlockPos();
			int t = buf.readVarInt();
			boolean done = buf.readBoolean();
			boolean extra = buf.readBoolean();
			list.add(new Objective(p, RepairType.values()[Math.max(0, Math.min(t, RepairType.values().length - 1))], done, extra));
		}
		boolean ventEnabled = buf.readBoolean();
		int ventTicks = buf.readVarInt();
		int ventInterval = buf.readVarInt();
		boolean ventFailed = buf.readBoolean();
		int nl = buf.readVarInt();
		Set<String> lurking = new HashSet<>();
		for (int i = 0; i < nl; i++) lurking.add(buf.readString());
		int jammed = buf.readVarInt();
		boolean viewing = buf.readBoolean();
		return new Snapshot(phase, night, remaining, total, grace, completed, required, caught, danger, inShelter, shelter, list,
				ventEnabled, ventTicks, ventInterval, ventFailed, lurking, jammed, viewing);
	}

	public static void apply(Snapshot s) {
		phase = s.phase();
		night = s.night();
		ticksRemaining = s.ticksRemaining();
		totalTicks = Math.max(1, s.totalTicks());
		graceTicks = s.graceTicks();
		completed = s.completed();
		required = s.required();
		caught = s.caught();
		danger = s.danger();
		inShelter = s.inShelter();
		shelter = s.shelter();
		objectives = s.objectives();
		ventEnabled = s.ventEnabled();
		ventTicks = s.ventTicks();
		ventInterval = Math.max(1, s.ventInterval());
		ventFailed = s.ventFailed();
		lurking = s.lurking();
		jammedDoors = s.jammedDoors();
		lastUpdate = System.currentTimeMillis();
	}

	public static void reset() {
		phase = NightPhase.NONE;
		objectives = new ArrayList<>();
		danger = 0;
		caught = false;
		ventFailed = false;
		lurking = Set.of();
		stamina = 1f;
		exhausted = false;
	}
}
