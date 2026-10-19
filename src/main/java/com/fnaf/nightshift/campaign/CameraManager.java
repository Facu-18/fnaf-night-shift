package com.fnaf.nightshift.campaign;

import com.fnaf.nightshift.block.CameraMonitorBlock;
import com.fnaf.nightshift.network.ModPackets;
import com.fnaf.nightshift.repair.RepairStationBlock;
import com.fnaf.nightshift.repair.RepairStationBlockEntity;
import com.fnaf.nightshift.repair.RepairType;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sistema de vigilancia propio (SecurityCraft no existe para Fabric 1.20.1).
 * Las cámaras son instalaciones reparables; el servidor lleva quién está
 * mirando (los animatrónicos aprovechan esa distracción) y envía el estado
 * de las cámaras. La imagen la renderiza el cliente desde la posición de la
 * cámara, con las entidades reales que el servidor ya sincroniza.
 */
public final class CameraManager {
	public record CameraInfo(BlockPos pos, Direction facing, String name, boolean broken) {
	}

	/** jugador -> monitor que está usando */
	private static final Map<UUID, BlockPos> viewers = new HashMap<>();

	private CameraManager() {
	}

	public static List<CameraInfo> cameras(ServerWorld world, CampaignState s) {
		List<CameraInfo> list = new ArrayList<>();
		BlockPos shelter = s.getShelter(world);
		for (BlockPos p : s.getStations()) {
			if (!world.isChunkLoaded(p)) continue;
			// sólo las cámaras del complejo de la oficina actual
			if (shelter != null && (p.getSquaredDistance(shelter) > 130 * 130 || Math.abs(p.getY() - shelter.getY()) > 20)) continue;
			if (world.getBlockEntity(p) instanceof RepairStationBlockEntity be && be.getRepairType() == RepairType.CAMERA) {
				BlockState st = world.getBlockState(p);
				Direction f = st.contains(RepairStationBlock.FACING) ? st.get(RepairStationBlock.FACING) : Direction.NORTH;
				String name = be.getCamName().isEmpty() ? "CAM" : be.getCamName();
				list.add(new CameraInfo(p, f, name, be.isBroken()));
			}
		}
		list.sort(Comparator.comparingInt((CameraInfo c) -> camNumber(c.name())).thenComparing(CameraInfo::name));
		return list;
	}

	/** "CAM 10 · Túneles" -> 10, para ordenar 1A, 1B, 2A... 10 de forma natural. */
	private static int camNumber(String name) {
		int i = 0;
		while (i < name.length() && !Character.isDigit(name.charAt(i))) i++;
		int n = 0;
		boolean any = false;
		while (i < name.length() && Character.isDigit(name.charAt(i))) {
			n = n * 10 + (name.charAt(i) - '0');
			i++;
			any = true;
		}
		return any ? n : Integer.MAX_VALUE;
	}

	public static void open(ServerPlayerEntity player, BlockPos monitor) {
		ServerWorld world = player.getServerWorld();
		CampaignState s = CampaignState.get(world.getServer());
		if (s.isCaught(player.getUuid())) {
			player.sendMessage(Text.translatable("message.fnaf_night_shift.caught_cannot_repair").formatted(Formatting.RED), true);
			return;
		}
		List<CameraInfo> cams = cameras(world, s);
		if (cams.isEmpty()) {
			player.sendMessage(Text.translatable("message.fnaf_night_shift.no_cameras").formatted(Formatting.YELLOW), false);
			return;
		}
		viewers.put(player.getUuid(), monitor.toImmutable());
		ModPackets.sendCameras(player, monitor, cams, BreakdownManager.camerasDisrupted(s), true);
	}

	public static void stop(ServerPlayerEntity player) {
		viewers.remove(player.getUuid());
	}

	/** Cierra la vista de cámaras (p. ej. al ser atrapado). */
	public static void forceClose(ServerPlayerEntity player) {
		if (viewers.remove(player.getUuid()) != null) ModPackets.sendCloseCameras(player);
	}

	public static boolean isViewing(UUID id) {
		return viewers.containsKey(id);
	}

	public static int viewerCount() {
		return viewers.size();
	}

	@Nullable
	public static BlockPos monitorOf(UUID id) {
		return viewers.get(id);
	}

	public static void tick(MinecraftServer server) {
		if (viewers.isEmpty() || server.getTicks() % 20 != 0) return;
		CampaignState s = CampaignState.get(server);
		for (UUID id : new ArrayList<>(viewers.keySet())) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
			BlockPos monitor = viewers.get(id);
			if (p == null || monitor == null) {
				viewers.remove(id);
				continue;
			}
			boolean valid = p.isAlive() && !s.isCaught(id) && p.squaredDistanceTo(monitor.toCenterPos()) < 6 * 6
					&& p.getServerWorld().getBlockState(monitor).getBlock() instanceof CameraMonitorBlock;
			if (!valid) {
				forceClose(p);
				continue;
			}
			ModPackets.sendCameras(p, monitor, cameras(p.getServerWorld(), s), BreakdownManager.camerasDisrupted(s), false);
		}
	}

	public static void remove(UUID id) {
		viewers.remove(id);
	}
}
