package com.fnaf.nightshift.network;

import com.fnaf.nightshift.FnafNightShift;
import com.fnaf.nightshift.campaign.CampaignState;
import com.fnaf.nightshift.campaign.NightManager;
import com.fnaf.nightshift.entity.AnimatronicKind;
import com.fnaf.nightshift.repair.RepairStationBlockEntity;
import com.fnaf.nightshift.repair.RepairType;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Paquetes del mod. El servidor es la autoridad: el cliente sólo recibe el
 * estado para mostrarlo y envía respuestas de minijuegos que el servidor valida.
 */
public final class ModPackets {
	public static final Identifier NIGHT_STATE = FnafNightShift.id("night_state");
	public static final Identifier NIGHT_RESULT = FnafNightShift.id("night_result");
	public static final Identifier JUMPSCARE = FnafNightShift.id("jumpscare");
	public static final Identifier OPEN_MINIGAME = FnafNightShift.id("open_minigame");
	public static final Identifier MINIGAME_ANSWER = FnafNightShift.id("minigame_answer");
	// --- 1.1 ---
	public static final Identifier STAMINA = FnafNightShift.id("stamina");
	public static final Identifier MENU = FnafNightShift.id("menu");
	public static final Identifier MENU_REQUEST = FnafNightShift.id("menu_request");
	public static final Identifier MENU_ACTION = FnafNightShift.id("menu_action");
	public static final Identifier CAMERAS = FnafNightShift.id("cameras");
	public static final Identifier CAMERAS_CLOSE = FnafNightShift.id("cameras_close");
	public static final Identifier CAMERA_STOP = FnafNightShift.id("camera_stop");

	private static final int MAX_OBJECTIVES = 12;

	private ModPackets() {
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(MENU_REQUEST, (server, player, handler, buf, responseSender) ->
				server.execute(() -> sendMenu(player)));
		ServerPlayNetworking.registerGlobalReceiver(MENU_ACTION, (server, player, handler, buf, responseSender) -> {
			int action = buf.readVarInt();
			int arg = buf.readVarInt();
			server.execute(() -> com.fnaf.nightshift.campaign.MenuActions.handle(player, action, arg));
		});
		ServerPlayNetworking.registerGlobalReceiver(CAMERA_STOP, (server, player, handler, buf, responseSender) ->
				server.execute(() -> com.fnaf.nightshift.campaign.CameraManager.stop(player)));
		ServerPlayNetworking.registerGlobalReceiver(MINIGAME_ANSWER, (server, player, handler, buf, responseSender) -> {
			BlockPos pos = buf.readBlockPos();
			boolean gaveUp = buf.readBoolean();
			int len = Math.min(buf.readVarInt(), 16);
			int[] answer = new int[len];
			for (int i = 0; i < len; i++) answer[i] = buf.readVarInt();
			server.execute(() -> {
				if (player.getWorld().isChunkLoaded(pos) && player.getWorld().getBlockEntity(pos) instanceof RepairStationBlockEntity be) {
					be.receiveAnswer(player, gaveUp, answer);
				}
			});
		});
	}

	// ------------------------------------------------------------------
	// S2C
	// ------------------------------------------------------------------

	public static void sendNightState(ServerPlayerEntity player, CampaignState state) {
		if (!ServerPlayNetworking.canSend(player, NIGHT_STATE)) return;
		PacketByteBuf buf = PacketByteBufs.create();
		boolean relevant = state.isFacilityWorld(player.getWorld()) || state.getParticipants().contains(player.getUuid());
		buf.writeVarInt(relevant ? state.getPhase().ordinal() : 0);
		buf.writeVarInt(state.getPhase().isActive() ? state.getActiveNight() : state.getCurrentNight());
		buf.writeVarInt(state.getTicksRemaining());
		buf.writeVarInt(state.getTotalTicks());
		buf.writeVarInt(state.getGraceTicks());
		buf.writeVarInt(state.getCompleted().size());
		buf.writeVarInt(state.getRequired().size());
		buf.writeBoolean(state.isCaught(player.getUuid()));
		buf.writeVarInt(state.getPhase().isActive() && relevant ? NightManager.dangerLevel(player) : 0);
		buf.writeBoolean(state.isInShelter(player));
		BlockPos shelter = state.getShelterPos();
		buf.writeBoolean(shelter != null);
		if (shelter != null) buf.writeBlockPos(shelter);

		List<BlockPos> list = new ArrayList<>(state.getRequired());
		// pendientes primero
		list.sort((a, b) -> Boolean.compare(state.getCompleted().contains(a), state.getCompleted().contains(b)));
		int n = state.getPhase().isActive() ? Math.min(list.size(), MAX_OBJECTIVES) : 0;
		buf.writeVarInt(n);
		for (int i = 0; i < n; i++) {
			BlockPos p = list.get(i);
			RepairType type = RepairType.GENERATOR;
			if (player.getWorld().isChunkLoaded(p) && player.getWorld().getBlockEntity(p) instanceof RepairStationBlockEntity be) {
				type = be.getRepairType();
			}
			buf.writeBlockPos(p);
			buf.writeVarInt(type.ordinal());
			buf.writeBoolean(state.getCompleted().contains(p));
			buf.writeBoolean(state.getExtraBreakdowns().containsKey(p));
		}
		// 1.1: ventilación, puertas acechadas, puertas atascadas y averías adicionales
		boolean active = state.getPhase().isActive() && relevant;
		buf.writeBoolean(active && com.fnaf.nightshift.config.ModConfig.get().ventilationEnabled);
		buf.writeVarInt(state.getVentTicks());
		buf.writeVarInt(active ? NightManager.definition(player.getServer()).ventIntervalTicks() : 1);
		buf.writeBoolean(state.isVentFailed());
		java.util.Set<String> lurking = active && state.isInShelter(player)
				? com.fnaf.nightshift.campaign.OfficeManager.lurkingSides(player.getServerWorld(), state) : java.util.Set.of();
		buf.writeVarInt(lurking.size());
		for (String side : lurking) buf.writeString(side);
		buf.writeVarInt(active ? state.getJammedDoors().size() : 0);
		buf.writeBoolean(active && com.fnaf.nightshift.campaign.CameraManager.isViewing(player.getUuid()));
		ServerPlayNetworking.send(player, NIGHT_STATE, buf);
	}

	public static void sendResult(ServerPlayerEntity player, boolean victory, int night, int done, int required, int catches,
								  int timeTicks, @Nullable NightManager.DefeatReason reason) {
		if (!ServerPlayNetworking.canSend(player, NIGHT_RESULT)) return;
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(victory);
		buf.writeVarInt(night);
		buf.writeVarInt(done);
		buf.writeVarInt(required);
		buf.writeVarInt(catches);
		buf.writeVarInt(timeTicks);
		buf.writeVarInt(reason == null ? -1 : reason.ordinal());
		ServerPlayNetworking.send(player, NIGHT_RESULT, buf);
	}

	public static void sendJumpscare(ServerPlayerEntity player, AnimatronicKind kind) {
		if (!ServerPlayNetworking.canSend(player, JUMPSCARE)) return;
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(kind.ordinal());
		ServerPlayNetworking.send(player, JUMPSCARE, buf);
	}

	public static void sendOpenMinigame(ServerPlayerEntity player, BlockPos pos, RepairType type, long seed, int level) {
		if (!ServerPlayNetworking.canSend(player, OPEN_MINIGAME)) return;
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		buf.writeVarInt(type.ordinal());
		buf.writeLong(seed);
		buf.writeVarInt(level);
		ServerPlayNetworking.send(player, OPEN_MINIGAME, buf);
	}

	// ------------------------------------------------------------------
	// 1.1
	// ------------------------------------------------------------------

	public static void sendStamina(ServerPlayerEntity player, float fraction, boolean exhausted) {
		if (!ServerPlayNetworking.canSend(player, STAMINA)) return;
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeFloat(fraction);
		buf.writeBoolean(exhausted);
		ServerPlayNetworking.send(player, STAMINA, buf);
	}

	/** Estado para el menú de noches (reutiliza el progreso de CampaignState). */
	public static void sendMenu(ServerPlayerEntity player) {
		if (!ServerPlayNetworking.canSend(player, MENU)) return;
		CampaignState s = CampaignState.get(player.getServer());
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(s.getCurrentNight());
		buf.writeVarInt(s.getHighestUnlocked());
		buf.writeVarInt(s.getCompletedMask());
		buf.writeVarInt(s.getDifficulty().ordinal());
		buf.writeVarInt(s.getPhase().ordinal());
		buf.writeVarInt(s.getActiveNight());
		buf.writeBoolean(com.fnaf.nightshift.campaign.MenuActions.canControl(player));
		buf.writeBoolean(com.fnaf.nightshift.campaign.MenuActions.isAdmin(player));
		buf.writeBoolean(s.getShelterPos() != null);
		int cams = 0;
		net.minecraft.server.world.ServerWorld fw = s.getFacilityWorld(player.getServer());
		if (fw != null) cams = com.fnaf.nightshift.campaign.CameraManager.cameras(fw, s).size();
		buf.writeVarInt(s.getStations().size());
		buf.writeVarInt(cams);
		buf.writeVarInt(s.getLockers().size());
		buf.writeBoolean(fw != null && com.fnaf.nightshift.world.FacilityUpgrade.needsUpgrade(fw, s));
		buf.writeVarInt(s.getTotalCatches());
		ServerPlayNetworking.send(player, MENU, buf);
	}

	public static void sendCameras(ServerPlayerEntity player, BlockPos monitor, List<com.fnaf.nightshift.campaign.CameraManager.CameraInfo> cams,
								   boolean disrupted, boolean open) {
		if (!ServerPlayNetworking.canSend(player, CAMERAS)) return;
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(open);
		buf.writeBlockPos(monitor);
		buf.writeBoolean(disrupted);
		buf.writeVarInt(cams.size());
		for (com.fnaf.nightshift.campaign.CameraManager.CameraInfo c : cams) {
			buf.writeBlockPos(c.pos());
			buf.writeVarInt(c.facing().getHorizontal());
			buf.writeString(c.name());
			buf.writeBoolean(c.broken());
		}
		ServerPlayNetworking.send(player, CAMERAS, buf);
	}

	public static void sendCloseCameras(ServerPlayerEntity player) {
		if (!ServerPlayNetworking.canSend(player, CAMERAS_CLOSE)) return;
		ServerPlayNetworking.send(player, CAMERAS_CLOSE, PacketByteBufs.create());
	}
}
