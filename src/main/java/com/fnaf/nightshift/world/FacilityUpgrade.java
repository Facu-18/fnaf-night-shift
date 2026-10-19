package com.fnaf.nightshift.world;

import com.fnaf.nightshift.FnafNightShift;
import com.fnaf.nightshift.block.SecurityDoorBlock;
import com.fnaf.nightshift.block.ToolLockerBlock;
import com.fnaf.nightshift.block.WallPanelBlock;
import com.fnaf.nightshift.campaign.CampaignState;
import com.fnaf.nightshift.registry.ModBlocks;
import com.fnaf.nightshift.repair.RepairStationBlock;
import com.fnaf.nightshift.repair.RepairStationBlockEntity;
import com.fnaf.nightshift.repair.RepairType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Actualización 1.1 del complejo generado (mismo origen que {@link FacilityGenerator}).
 * Se aplica a mapas nuevos y existentes. Sólo coloca bloques en huecos de
 * aire (o sobre la pared del dintel de las puertas de la oficina), por lo
 * que no destruye construcciones ni repite elementos ya colocados.
 * <ul>
 *   <li>Dinteles de las 4 puertas de la oficina: 3 bloques de alto, para que los animatrónicos
 *       puedan entrar si la puerta está abierta (cerrada sigue bloqueando el paso).</li>
 *   <li>14 cámaras en pasillos de acceso a la oficina, escenario, cala pirata y sectores técnicos.</li>
 *   <li>Monitores de cámaras (oficina y sala de seguridad) y panel de ventilación de la oficina.</li>
 *   <li>10 casilleros de herramientas en zonas de riesgo creciente.</li>
 * </ul>
 */
public final class FacilityUpgrade {
	private record Cam(int x, int y, int z, Direction facing, String name) {
	}

	private record Locker(int x, int y, int z, Direction facing, int tier) {
	}

	static final Cam[] CAMERAS = {
			new Cam(18, 6, 35, Direction.NORTH, "CAM 1A · Escenario"),
			new Cam(6, 6, 34, Direction.EAST, "CAM 1B · Comedor"),
			new Cam(28, 6, 29, Direction.EAST, "CAM 1C · Cala pirata"),
			new Cam(40, 4, 60, Direction.NORTH, "CAM 2A · Pasillo oeste"),
			new Cam(37, 4, 50, Direction.WEST, "CAM 2B · Acceso oeste"),
			new Cam(50, 4, 37, Direction.NORTH, "CAM 3 · Pasillo norte"),
			new Cam(60, 4, 40, Direction.SOUTH, "CAM 4A · Pasillo este"),
			new Cam(63, 4, 50, Direction.EAST, "CAM 4B · Acceso este"),
			new Cam(60, 4, 60, Direction.WEST, "CAM 5 · Pasillo sur"),
			new Cam(17, 5, 57, Direction.NORTH, "CAM 6 · Cocina"),
			new Cam(93, 8, 31, Direction.WEST, "CAM 7 · Central eléctrica"),
			new Cam(79, 5, 39, Direction.WEST, "CAM 8 · Depósito"),
			new Cam(29, 6, 67, Direction.SOUTH, "CAM 9 · Almacén"),
			new Cam(55, -4, 35, Direction.WEST, "CAM 10 · Túneles"),
	};

	static final Locker[] LOCKERS = {
			// nivel 0: cerca de la oficina / zonas vigiladas
			new Locker(76, 1, 46, Direction.NORTH, 0),
			new Locker(78, 1, 46, Direction.NORTH, 0),
			new Locker(24, 1, 61, Direction.EAST, 0),
			// nivel 1: zonas de servicio
			new Locker(18, 1, 52, Direction.WEST, 1),
			new Locker(30, 1, 72, Direction.WEST, 1),
			new Locker(66, 1, 76, Direction.EAST, 1),
			// nivel 2: sectores peligrosos
			new Locker(94, 1, 8, Direction.WEST, 2),
			new Locker(56, -6, 26, Direction.WEST, 2),
			new Locker(96, 1, 54, Direction.WEST, 2),
			new Locker(9, 1, 12, Direction.EAST, 2),
	};

	private FacilityUpgrade() {
	}

	/** ¿El refugio registrado pertenece a un complejo generado por el mod? */
	public static boolean isGeneratedFacility(ServerWorld world, BlockPos shelter) {
		if (!world.getBlockState(shelter).isOf(ModBlocks.SHELTER_CORE)) return false;
		BlockPos origin = shelter.add(-50, -1, -50);
		return world.getBlockState(origin.add(42, 1, 50)).getBlock() instanceof SecurityDoorBlock
				|| world.getBlockState(origin.add(58, 1, 50)).getBlock() instanceof SecurityDoorBlock;
	}

	/** Aplica la actualización al complejo registrado. Devuelve los bloques cambiados o -1 si no es un complejo generado. */
	public static int upgradeExisting(MinecraftServer server) {
		CampaignState s = CampaignState.get(server);
		ServerWorld world = s.getFacilityWorld(server);
		BlockPos shelter = s.getShelterPos();
		if (world == null || shelter == null || !isGeneratedFacility(world, shelter)) return -1;
		return apply(world, shelter.add(-50, -1, -50));
	}

	/** ¿Le faltan los elementos de la actualización? (sin cámaras registradas) */
	public static boolean needsUpgrade(ServerWorld world, CampaignState s) {
		BlockPos shelter = s.getShelterPos();
		if (shelter == null || !isGeneratedFacility(world, shelter)) return false;
		BlockPos origin = shelter.add(-50, -1, -50);
		return !world.getBlockState(origin.add(47, 1, 44)).isOf(ModBlocks.CAMERA_MONITOR)
				|| world.getBlockState(origin.add(42, 3, 50)).isOpaque();
	}

	public static int apply(ServerWorld world, BlockPos origin) {
		int changes = 0;
		// 1) dinteles de las puertas de la oficina
		for (int[] d : new int[][]{{42, 50}, {58, 50}, {50, 42}, {50, 58}}) {
			BlockPos door = origin.add(d[0], 1, d[1]);
			BlockPos lintel = door.up(2);
			if (world.getBlockState(door).getBlock() instanceof SecurityDoorBlock && !world.getBlockState(lintel).isAir()
					&& !world.getBlockState(lintel).isOf(ModBlocks.SHELTER_CORE)) {
				world.setBlockState(lintel, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
				changes++;
			}
		}
		// 2) cámaras
		for (Cam c : CAMERAS) {
			BlockPos p = freeSpot(world, origin.add(c.x, c.y, c.z));
			if (p == null) continue;
			world.setBlockState(p, ModBlocks.STATIONS.get(RepairType.CAMERA).getDefaultState().with(RepairStationBlock.FACING, c.facing), Block.NOTIFY_ALL);
			if (world.getBlockEntity(p) instanceof RepairStationBlockEntity be) be.setCamName(c.name);
			changes++;
		}
		// 3) monitores y panel de ventilación
		changes += place(world, origin.add(47, 1, 44), ModBlocks.CAMERA_MONITOR.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
		changes += place(world, origin.add(53, 1, 44), ModBlocks.CAMERA_MONITOR.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
		changes += place(world, origin.add(29, 2, 56), ModBlocks.CAMERA_MONITOR.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
		changes += place(world, origin.add(43, 2, 47), ModBlocks.VENT_CONTROL_PANEL.getDefaultState().with(WallPanelBlock.FACING, Direction.EAST));
		// 4) casilleros de herramientas
		for (Locker l : LOCKERS) {
			changes += place(world, origin.add(l.x, l.y, l.z), ModBlocks.TOOL_LOCKER.getDefaultState()
					.with(HorizontalFacingBlock.FACING, l.facing).with(ToolLockerBlock.TIER, l.tier));
		}
		FnafNightShift.LOGGER.info("Complejo actualizado (1.1): {} bloques añadidos o ajustados", changes);
		return changes;
	}

	private static BlockPos freeSpot(ServerWorld world, BlockPos p) {
		for (int dy = 0; dy >= -1; dy--) {
			BlockState s = world.getBlockState(p.up(dy));
			if (s.isOf(ModBlocks.STATIONS.get(RepairType.CAMERA))) return null; // ya instalada
			if (s.isAir()) return p.up(dy);
		}
		return null;
	}

	private static int place(ServerWorld world, BlockPos p, BlockState state) {
		BlockState cur = world.getBlockState(p);
		if (cur.isOf(state.getBlock()) || !cur.isAir()) return 0;
		world.setBlockState(p, state, Block.NOTIFY_ALL);
		return 1;
	}
}
