package com.fnaf.nightshift.world;

import com.fnaf.nightshift.block.FlickeringLightBlock;
import com.fnaf.nightshift.block.PatrolNodeBlock;
import com.fnaf.nightshift.block.SpawnMarkerBlock;
import com.fnaf.nightshift.block.WallPanelBlock;
import com.fnaf.nightshift.block.Zone;
import com.fnaf.nightshift.entity.AnimatronicKind;
import com.fnaf.nightshift.registry.ModBlocks;
import com.fnaf.nightshift.repair.RepairStationBlock;
import com.fnaf.nightshift.repair.RepairType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Genera el complejo industrial completo a partir de un origen (esquina
 * noroeste, nivel del suelo). Huella: 100 × 92 bloques, más un nivel de
 * túneles 7 bloques por debajo.
 * <p>
 * Zonas: refugio central, anillo de corredores, pizzería (comedor, escenario,
 * cala pirata), cocina, sala de seguridad, central eléctrica, depósito de
 * herramientas, almacén, tres salas técnicas, túneles de mantenimiento con
 * sala de máquinas, conductos de ventilación (sólo jugadores) y escaleras.
 * <p>
 * Antes de construir comprueba que el volumen sólo contenga terreno natural:
 * nunca reemplaza construcciones del jugador ni bloques con inventario.
 */
public final class FacilityGenerator {
	public static final int SIZE_X = 100;
	public static final int SIZE_Z = 92;
	public static final int MIN_Y = -8;
	public static final int MAX_Y = 10;
	private static final int TUNNEL_FLOOR = -7;

	public record Report(boolean ok, int blocking, @Nullable BlockPos firstBlocked) {
	}

	public record Result(int blocks, int stations, int nodes, BlockPos shelter) {
	}

	private record Space(int x1, int z1, int x2, int z2, int floorY, int height, BlockState floor, BlockState wall, BlockState ceiling) {
		int top() {
			return floorY + height + 1;
		}
	}

	private FacilityGenerator() {
	}

	public static String describeBounds(BlockPos o) {
		return o.add(-1, MIN_Y, -1).toShortString() + " → " + o.add(SIZE_X, MAX_Y, SIZE_Z).toShortString();
	}

	// ------------------------------------------------------------------
	// Comprobación de seguridad
	// ------------------------------------------------------------------

	public static Report check(ServerWorld world, BlockPos origin) {
		if (origin.getY() + MIN_Y <= world.getBottomY() + 1 || origin.getY() + MAX_Y >= world.getTopY() - 1) {
			return new Report(false, 1, origin);
		}
		int blocking = 0;
		BlockPos first = null;
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = -1; x <= SIZE_X; x++) {
			for (int z = -1; z <= SIZE_Z; z++) {
				for (int y = MIN_Y; y <= MAX_Y; y++) {
					m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
					BlockState s = world.getBlockState(m);
					if (!isNatural(s) || world.getBlockEntity(m) != null) {
						blocking++;
						if (first == null) first = m.toImmutable();
					}
				}
			}
		}
		return new Report(blocking == 0, blocking, first);
	}

	private static boolean isNatural(BlockState s) {
		if (s.isAir() || s.isReplaceable()) return true;
		if (s.isOf(Blocks.BEDROCK)) return false;
		return s.isIn(BlockTags.DIRT) || s.isIn(BlockTags.BASE_STONE_OVERWORLD) || s.isIn(BlockTags.SAND)
				|| s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.LOGS) || s.isIn(BlockTags.FLOWERS) || s.isIn(BlockTags.SAPLINGS)
				|| s.isIn(BlockTags.TERRACOTTA) || s.isIn(BlockTags.ICE) || s.isIn(BlockTags.SNOW)
				|| s.isIn(BlockTags.COAL_ORES) || s.isIn(BlockTags.IRON_ORES) || s.isIn(BlockTags.COPPER_ORES)
				|| s.isIn(BlockTags.GOLD_ORES) || s.isIn(BlockTags.REDSTONE_ORES) || s.isIn(BlockTags.LAPIS_ORES)
				|| s.isIn(BlockTags.DIAMOND_ORES) || s.isIn(BlockTags.EMERALD_ORES)
				|| s.isOf(Blocks.GRAVEL) || s.isOf(Blocks.CLAY) || s.isOf(Blocks.SANDSTONE) || s.isOf(Blocks.RED_SANDSTONE)
				|| s.isOf(Blocks.CALCITE) || s.isOf(Blocks.SMOOTH_BASALT) || s.isOf(Blocks.AMETHYST_BLOCK)
				|| s.isOf(Blocks.MOSS_BLOCK) || s.isOf(Blocks.DRIPSTONE_BLOCK) || s.isOf(Blocks.POINTED_DRIPSTONE)
				|| s.isOf(Blocks.CACTUS) || s.isOf(Blocks.SUGAR_CANE) || s.isOf(Blocks.BAMBOO) || s.isOf(Blocks.PUMPKIN)
				|| s.isOf(Blocks.MELON) || s.isOf(Blocks.BROWN_MUSHROOM_BLOCK) || s.isOf(Blocks.RED_MUSHROOM_BLOCK)
				|| s.isOf(Blocks.MUSHROOM_STEM) || s.isOf(Blocks.COBWEB) || s.isOf(Blocks.OBSIDIAN) || s.isOf(Blocks.MAGMA_BLOCK);
	}

	// ------------------------------------------------------------------
	// Generación
	// ------------------------------------------------------------------

	private static final class Plan {
		final Map<Long, BlockState> blocks = new LinkedHashMap<>();
		final Set<Long> interior = new HashSet<>();
		final BlockPos origin;
		int stations, nodes;

		Plan(BlockPos origin) {
			this.origin = origin;
		}

		static long key(int x, int y, int z) {
			return BlockPos.asLong(x, y, z);
		}

		void set(int x, int y, int z, BlockState s) {
			blocks.put(key(x, y, z), s);
		}

		@Nullable
		BlockState get(int x, int y, int z) {
			return blocks.get(key(x, y, z));
		}

		void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
			for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
				for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
					for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
						set(x, y, z, s);
		}
	}

	public static Result generate(ServerWorld world, BlockPos origin) {
		Plan plan = new Plan(origin);
		Random rand = new Random(origin.asLong() ^ 0xF4AFL);

		BlockState concrete = ModBlocks.INDUSTRIAL_CONCRETE.getDefaultState();
		BlockState cracked = ModBlocks.CRACKED_CONCRETE.getDefaultState();
		BlockState metal = ModBlocks.METAL_PLATING.getDefaultState();
		BlockState rusty = ModBlocks.RUSTY_METAL.getDefaultState();
		BlockState wall = ModBlocks.INDUSTRIAL_WALL.getDefaultState();
		BlockState stripe = ModBlocks.INDUSTRIAL_WALL_STRIPE.getDefaultState();
		BlockState checker = ModBlocks.CHECKERED_FLOOR.getDefaultState();
		BlockState tiles = ModBlocks.DIRTY_TILES.getDefaultState();
		BlockState pizzaWall = ModBlocks.PIZZERIA_WALL.getDefaultState();
		BlockState ceiling = ModBlocks.CEILING_PANEL.getDefaultState();
		BlockState stage = ModBlocks.STAGE_FLOOR.getDefaultState();

		List<Space> spaces = new ArrayList<>();
		// --- Salas (interior inclusive) ---
		Space shelter = new Space(43, 43, 57, 57, 0, 5, metal, stripe, ceiling);
		Space dining = new Space(4, 16, 32, 36, 0, 6, checker, pizzaWall, ceiling);
		Space stageRoom = new Space(8, 4, 28, 13, 0, 7, stage, pizzaWall, ceiling);
		Space stageOpening = new Space(10, 14, 26, 15, 0, 6, checker, pizzaWall, ceiling);
		Space cove = new Space(34, 26, 38, 32, 0, 5, stage, pizzaWall, ceiling);
		Space coveOpening = new Space(33, 28, 33, 30, 0, 4, stage, pizzaWall, ceiling);
		Space kitchen = new Space(4, 42, 18, 58, 0, 5, tiles, wall, ceiling);
		Space security = new Space(24, 54, 34, 62, 0, 4, tiles, stripe, ceiling);
		Space power = new Space(66, 4, 94, 32, 0, 8, concrete, rusty, ceiling);
		Space tools = new Space(66, 38, 80, 46, 0, 5, concrete, wall, ceiling);
		Space techA = new Space(86, 44, 96, 56, 0, 4, metal, metal, ceiling);
		Space techB = new Space(66, 64, 80, 78, 0, 4, metal, stripe, ceiling);
		Space techC = new Space(42, 68, 58, 82, 0, 4, tiles, wall, ceiling);
		Space warehouse = new Space(4, 66, 30, 88, 0, 6, concrete, rusty, ceiling);
		spaces.addAll(List.of(shelter, dining, stageRoom, stageOpening, cove, coveOpening, kitchen, security, power, tools, techA, techB, techC, warehouse));

		// --- Anillo alrededor del refugio y corredores ---
		List<Space> corridors = new ArrayList<>();
		corridors.add(new Space(39, 39, 41, 61, 0, 4, cracked, wall, ceiling)); // anillo oeste
		corridors.add(new Space(59, 39, 61, 61, 0, 4, cracked, wall, ceiling)); // anillo este
		corridors.add(new Space(39, 39, 61, 41, 0, 4, cracked, wall, ceiling)); // anillo norte
		corridors.add(new Space(39, 59, 61, 61, 0, 4, cracked, wall, ceiling)); // anillo sur
		corridors.add(new Space(19, 49, 38, 51, 0, 4, cracked, wall, ceiling)); // a) radial oeste → cocina
		corridors.add(new Space(49, 18, 51, 38, 0, 4, cracked, wall, ceiling)); // b) radial norte
		corridors.add(new Space(62, 49, 85, 51, 0, 4, cracked, wall, ceiling)); // c) radial este → sala técnica A
		corridors.add(new Space(49, 62, 51, 67, 0, 4, cracked, wall, ceiling)); // d) radial sur → sala técnica C
		corridors.add(new Space(24, 37, 26, 48, 0, 4, cracked, wall, ceiling)); // e) comedor
		corridors.add(new Space(33, 18, 66, 20, 0, 4, cracked, wall, ceiling)); // f) corredor norte (comedor ↔ central)
		corridors.add(new Space(70, 33, 72, 37, 0, 4, cracked, wall, ceiling)); // h) central ↔ depósito
		corridors.add(new Space(66, 52, 68, 63, 0, 4, cracked, wall, ceiling)); // i) → sala técnica B
		corridors.add(new Space(20, 52, 22, 65, 0, 4, cracked, wall, ceiling)); // j) → almacén
		corridors.add(new Space(31, 74, 41, 76, 0, 4, cracked, wall, ceiling)); // k) almacén ↔ técnica C
		corridors.add(new Space(59, 72, 65, 74, 0, 4, cracked, wall, ceiling)); // l) técnica C ↔ técnica B
		corridors.add(new Space(28, 52, 30, 53, 0, 3, tiles, stripe, ceiling)); // puerta sala de seguridad
		corridors.add(new Space(72, 47, 74, 48, 0, 3, concrete, wall, ceiling)); // puerta depósito
		spaces.addAll(corridors);

		// --- Túneles de mantenimiento ---
		BlockState tunnelFloor = metal;
		List<Space> tunnels = new ArrayList<>();
		tunnels.add(new Space(10, 30, 90, 32, TUNNEL_FLOOR, 3, tunnelFloor, cracked, concrete));
		tunnels.add(new Space(88, 30, 90, 72, TUNNEL_FLOOR, 3, tunnelFloor, cracked, concrete));
		tunnels.add(new Space(10, 70, 90, 72, TUNNEL_FLOOR, 3, tunnelFloor, cracked, concrete));
		tunnels.add(new Space(10, 30, 12, 72, TUNNEL_FLOOR, 3, tunnelFloor, cracked, concrete));
		Space machineRoom = new Space(44, 24, 56, 36, TUNNEL_FLOOR, 3, concrete, rusty, concrete);
		tunnels.add(machineRoom);
		spaces.addAll(tunnels);

		// --- Conductos de ventilación (2 de alto: los animatrónicos no caben) ---
		List<Space> vents = new ArrayList<>();
		vents.add(new Space(19, 56, 23, 57, 0, 2, metal, metal, metal)); // cocina ↔ sala de seguridad
		vents.add(new Space(81, 45, 85, 46, 0, 2, metal, metal, metal)); // depósito ↔ técnica A
		spaces.addAll(vents);

		// 1) Interiores
		for (Space s : spaces) {
			for (int x = s.x1; x <= s.x2; x++)
				for (int z = s.z1; z <= s.z2; z++)
					for (int y = s.floorY + 1; y <= s.floorY + s.height; y++)
						plan.interior.add(Plan.key(x, y, z));
		}
		// 2) Carcasas (suelo, paredes, techo) donde no haya interior
		for (Space s : spaces) {
			for (int x = s.x1 - 1; x <= s.x2 + 1; x++) {
				for (int z = s.z1 - 1; z <= s.z2 + 1; z++) {
					for (int y = s.floorY; y <= s.top(); y++) {
						long k = Plan.key(x, y, z);
						if (plan.interior.contains(k)) continue;
						BlockState existing = plan.blocks.get(k);
						BlockState mat = y == s.floorY ? s.floor : y == s.top() ? s.ceiling : s.wall;
						// los suelos/techos ya colocados por otra sala tienen prioridad sobre paredes
						if (existing != null && mat == s.wall && existing != s.wall) continue;
						plan.blocks.put(k, mat);
					}
				}
			}
		}
		// 3) Aire en interiores
		for (long k : plan.interior) plan.blocks.put(k, Blocks.AIR.getDefaultState());

		// --- Refugio: puertas de seguridad (1×2, sólo jugadores) ---
		door(plan, 42, 1, 50, Direction.EAST);
		door(plan, 58, 1, 50, Direction.WEST);
		door(plan, 50, 1, 42, Direction.SOUTH);
		door(plan, 50, 1, 58, Direction.NORTH);
		plan.set(50, 1, 50, ModBlocks.SHELTER_CORE.getDefaultState());
		plan.fill(47, 0, 47, 53, 0, 53, ModBlocks.HAZARD_BLOCK.getDefaultState());
		plan.fill(48, 0, 48, 52, 0, 52, metal);
		for (int[] l : new int[][]{{46, 46}, {54, 46}, {46, 54}, {54, 54}, {50, 46}, {50, 54}, {46, 50}, {54, 50}}) {
			light(plan, l[0], 5, l[1], false);
		}
		for (int[] c : new int[][]{{44, 44}, {56, 44}, {44, 56}, {56, 56}}) plan.set(c[0], 1, c[1], ModBlocks.SUPPLY_CRATE.getDefaultState());
		wallPanel(plan, ModBlocks.SIGN_EXIT, 41, 3, 50, Direction.WEST);
		wallPanel(plan, ModBlocks.SIGN_EXIT, 59, 3, 50, Direction.EAST);
		wallPanel(plan, ModBlocks.SIGN_EXIT, 50, 3, 41, Direction.NORTH);
		wallPanel(plan, ModBlocks.SIGN_EXIT, 50, 3, 59, Direction.SOUTH);

		// --- Pizzería ---
		plan.fill(9, 1, 5, 27, 1, 9, stage); // tarima del escenario
		plan.fill(8, 2, 4, 28, 7, 4, ModBlocks.STAGE_CURTAIN.getDefaultState());
		plan.fill(8, 2, 5, 8, 7, 8, ModBlocks.STAGE_CURTAIN.getDefaultState());
		plan.fill(28, 2, 5, 28, 7, 8, ModBlocks.STAGE_CURTAIN.getDefaultState());
		spawn(plan, 12, 2, 7, AnimatronicKind.BONNIE);
		spawn(plan, 18, 2, 7, AnimatronicKind.FREDDY);
		spawn(plan, 24, 2, 7, AnimatronicKind.CHICA);
		for (int x = 8; x <= 28; x += 5) {
			for (int z = 20; z <= 32; z += 6) {
				plan.fill(x, 1, z, x + 2, 1, z, ModBlocks.PARTY_TABLE.getDefaultState());
			}
		}
		wallPanel(plan, ModBlocks.SIGN_PIZZERIA, 18, 5, 37, Direction.NORTH);
		// cala pirata
		plan.set(33, 1, 27, ModBlocks.STAGE_CURTAIN.getDefaultState());
		plan.set(33, 1, 31, ModBlocks.STAGE_CURTAIN.getDefaultState());
		plan.fill(33, 3, 28, 33, 4, 30, ModBlocks.STAGE_CURTAIN.getDefaultState());
		spawn(plan, 37, 1, 29, AnimatronicKind.FOXY);
		wallPanel(plan, ModBlocks.SIGN_DANGER, 33, 5, 27, Direction.WEST);
		lightsGrid(plan, dining, rand, 0.5);
		lightsGrid(plan, stageRoom, rand, 0.3);
		light(plan, 36, 5, 29, true);

		// --- Cocina ---
		plan.fill(6, 1, 43, 16, 1, 43, metal);
		plan.fill(6, 1, 57, 9, 1, 57, metal);
		plan.fill(8, 1, 48, 12, 1, 50, metal);
		lightsGrid(plan, kitchen, rand, 0.6);

		// --- Sala de seguridad ---
		plan.fill(26, 1, 56, 32, 1, 56, metal);
		lightsGrid(plan, security, rand, 0.4);
		wallPanel(plan, ModBlocks.SIGN_STAFF, 29, 3, 52, Direction.NORTH);

		// --- Central eléctrica ---
		BlockState casing = ModBlocks.GENERATOR_CASING.getDefaultState();
		BlockState hazard = ModBlocks.HAZARD_BLOCK.getDefaultState();
		for (int gx : new int[]{72, 84}) {
			plan.fill(gx - 1, 0, 13, gx + 5, 0, 19, hazard);
			plan.fill(gx, 1, 14, gx + 4, 4, 18, casing);
			plan.fill(gx + 1, 5, 15, gx + 3, 5, 17, metal);
			plan.fill(gx + 2, 6, 16, gx + 2, 8, 16, ModBlocks.CABLE_BUNDLE.getDefaultState());
		}
		plan.fill(67, 1, 24, 93, 1, 24, ModBlocks.CABLE_BUNDLE.getDefaultState());
		lightsGrid(plan, power, rand, 0.55);
		wallPanel(plan, ModBlocks.SIGN_DANGER, 65, 3, 17, Direction.WEST);

		// --- Depósito de herramientas ---
		for (int x = 68; x <= 78; x += 2) plan.set(x, 1, 38, ModBlocks.SUPPLY_CRATE.getDefaultState());
		plan.fill(68, 1, 42, 76, 2, 42, metal);
		lightsGrid(plan, tools, rand, 0.3);

		// --- Salas técnicas ---
		for (int z = 46; z <= 54; z += 4) plan.fill(88, 1, z, 88, 3, z + 1, metal);
		for (int x = 69; x <= 78; x += 3) plan.fill(x, 1, 67, x, 3, 74, metal);
		for (int x = 69; x <= 78; x += 3) plan.fill(x, 1, 70, x, 1, 71, Blocks.AIR.getDefaultState());
		plan.fill(45, 1, 72, 55, 1, 72, metal);
		plan.fill(45, 1, 77, 55, 2, 77, ModBlocks.CABLE_BUNDLE.getDefaultState());
		plan.set(44, 1, 70, ModBlocks.SUPPLY_CRATE.getDefaultState());
		lightsGrid(plan, techA, rand, 0.7);
		lightsGrid(plan, techB, rand, 0.7);
		lightsGrid(plan, techC, rand, 0.6);
		wallPanel(plan, ModBlocks.SIGN_STAFF, 85, 3, 53, Direction.WEST);

		// --- Almacén ---
		for (int z = 70; z <= 84; z += 5) {
			plan.fill(8, 1, z, 14, 3, z, rusty);
			plan.fill(19, 1, z, 26, 3, z, rusty);
		}
		plan.fill(20, 1, 70, 20, 3, 70, Blocks.AIR.getDefaultState());
		plan.set(8, 1, 68, ModBlocks.SUPPLY_CRATE.getDefaultState());
		plan.set(28, 1, 86, ModBlocks.SUPPLY_CRATE.getDefaultState());
		lightsGrid(plan, warehouse, rand, 0.6);

		// --- Corredores oscuros: pocas luces, muchas averiadas, luces de emergencia ---
		for (Space c : corridors) {
			int cx = (c.x1 + c.x2) / 2, cz = (c.z1 + c.z2) / 2;
			boolean alongX = (c.x2 - c.x1) > (c.z2 - c.z1);
			int len = alongX ? c.x2 - c.x1 : c.z2 - c.z1;
			for (int i = 3; i < len; i += 9) {
				int x = alongX ? c.x1 + i : cx;
				int z = alongX ? cz : c.z1 + i;
				if (rand.nextFloat() < 0.65f) light(plan, x, c.floorY + c.height, z, rand.nextFloat() < 0.7f);
				else if (alongX) wallPanel(plan, ModBlocks.EMERGENCY_LIGHT, x, c.floorY + 3, c.z1, Direction.SOUTH);
				else wallPanel(plan, ModBlocks.EMERGENCY_LIGHT, c.x1, c.floorY + 3, z, Direction.EAST);
			}
		}

		// --- Túneles ---
		for (Space t : tunnels) {
			boolean alongX = (t.x2 - t.x1) > (t.z2 - t.z1);
			int len = alongX ? t.x2 - t.x1 : t.z2 - t.z1;
			for (int i = 4; i < len; i += 10) {
				int x = alongX ? t.x1 + i : t.x1;
				int z = alongX ? t.z1 : t.z1 + i;
				if (alongX) wallPanel(plan, ModBlocks.EMERGENCY_LIGHT, x, TUNNEL_FLOOR + 2, z, Direction.SOUTH);
				else wallPanel(plan, ModBlocks.EMERGENCY_LIGHT, x, TUNNEL_FLOOR + 2, z, Direction.EAST);
			}
		}
		plan.fill(45, TUNNEL_FLOOR + 1, 35, 47, TUNNEL_FLOOR + 1, 35, ModBlocks.CABLE_BUNDLE.getDefaultState());
		plan.set(54, TUNNEL_FLOOR + 1, 34, ModBlocks.SUPPLY_CRATE.getDefaultState());
		light(plan, 50, TUNNEL_FLOOR + 3, 30, true);

		// --- Escaleras entre niveles (columna con escalera y rejilla) ---
		shaft(plan, 10, 45, Direction.EAST);   // cocina
		shaft(plan, 80, 30, Direction.SOUTH);  // central eléctrica
		shaft(plan, 70, 70, Direction.SOUTH);  // sala técnica B
		shaft(plan, 20, 70, Direction.SOUTH);  // almacén
		shaft(plan, 90, 50, Direction.WEST);   // sala técnica A

		// --- Conductos: suelo de rejilla metálica ---
		plan.fill(19, 0, 56, 23, 0, 57, ModBlocks.METAL_GRATE.getDefaultState());
		plan.fill(81, 0, 45, 85, 0, 46, ModBlocks.METAL_GRATE.getDefaultState());

		// --- Instalaciones reparables ---
		station(plan, RepairType.GENERATOR, 70, 1, 4, Direction.SOUTH);
		station(plan, RepairType.GENERATOR, 80, 1, 4, Direction.SOUTH);
		station(plan, RepairType.GENERATOR, 90, 1, 4, Direction.SOUTH);
		station(plan, RepairType.ELECTRICAL_PANEL, 94, 1, 20, Direction.WEST);
		station(plan, RepairType.WIRING, 66, 1, 28, Direction.EAST);
		station(plan, RepairType.GENERATOR, 50, TUNNEL_FLOOR + 1, 24, Direction.SOUTH);
		station(plan, RepairType.VENTILATION, 90, TUNNEL_FLOOR + 1, 60, Direction.WEST);
		station(plan, RepairType.WIRING, 30, TUNNEL_FLOOR + 1, 72, Direction.NORTH);
		station(plan, RepairType.FUSE_BOX, 66, 1, 42, Direction.EAST);
		station(plan, RepairType.VENTILATION, 4, 1, 52, Direction.EAST);
		station(plan, RepairType.LIGHTING, 12, 1, 58, Direction.NORTH);
		station(plan, RepairType.LIGHTING, 4, 1, 30, Direction.EAST);
		station(plan, RepairType.FUSE_BOX, 32, 1, 34, Direction.WEST);
		station(plan, RepairType.ELECTRICAL_PANEL, 28, 1, 12, Direction.WEST);
		station(plan, RepairType.SECURITY, 29, 1, 62, Direction.NORTH);
		station(plan, RepairType.TERMINAL, 34, 1, 58, Direction.WEST);
		station(plan, RepairType.TERMINAL, 96, 1, 48, Direction.WEST);
		station(plan, RepairType.AUTO_DOOR, 92, 1, 56, Direction.NORTH);
		station(plan, RepairType.WIRING, 80, 1, 66, Direction.WEST);
		station(plan, RepairType.SECURITY, 74, 1, 78, Direction.NORTH);
		station(plan, RepairType.FUSE_BOX, 42, 1, 79, Direction.EAST);
		station(plan, RepairType.ELECTRICAL_PANEL, 58, 1, 79, Direction.WEST);
		station(plan, RepairType.TERMINAL, 50, 1, 82, Direction.NORTH);
		station(plan, RepairType.AUTO_DOOR, 4, 1, 80, Direction.EAST);
		station(plan, RepairType.GENERATOR, 16, 1, 88, Direction.NORTH);
		station(plan, RepairType.AUTO_DOOR, 45, 1, 20, Direction.NORTH);

		// --- Nodos de patrulla ---
		node(plan, dining, Zone.PIZZERIA, 18, 26);
		node(plan, dining, Zone.PIZZERIA, 8, 18);
		node(plan, dining, Zone.PIZZERIA, 30, 35);
		node(plan, stageRoom, Zone.PIZZERIA, 18, 11);
		node(plan, cove, Zone.PIZZERIA, 35, 29);
		node(plan, kitchen, Zone.SERVICE, 14, 46);
		node(plan, kitchen, Zone.SERVICE, 6, 55);
		node(plan, tools, Zone.SERVICE, 73, 44);
		node(plan, warehouse, Zone.SERVICE, 17, 77);
		node(plan, warehouse, Zone.SERVICE, 6, 86);
		node(plan, warehouse, Zone.SERVICE, 28, 68);
		node(plan, security, Zone.TECHNICAL, 30, 59);
		node(plan, power, Zone.TECHNICAL, 70, 10);
		node(plan, power, Zone.TECHNICAL, 90, 28);
		node(plan, power, Zone.TECHNICAL, 79, 21);
		node(plan, techA, Zone.TECHNICAL, 92, 50);
		node(plan, techB, Zone.TECHNICAL, 73, 76);
		node(plan, techC, Zone.TECHNICAL, 50, 75);
		node(plan, null, Zone.GENERAL, 40, 40);
		node(plan, null, Zone.GENERAL, 60, 40);
		node(plan, null, Zone.GENERAL, 40, 60);
		node(plan, null, Zone.GENERAL, 60, 60);
		node(plan, null, Zone.GENERAL, 28, 50);
		node(plan, null, Zone.GENERAL, 50, 25);
		node(plan, null, Zone.GENERAL, 40, 19);
		node(plan, null, Zone.GENERAL, 60, 19);
		node(plan, null, Zone.GENERAL, 75, 50);
		node(plan, null, Zone.GENERAL, 67, 58);
		node(plan, null, Zone.GENERAL, 21, 60);
		node(plan, null, Zone.GENERAL, 36, 75);
		node(plan, null, Zone.GENERAL, 62, 73);
		node(plan, null, Zone.GENERAL, 50, 65);
		node(plan, null, Zone.GENERAL, 25, 42);
		// túneles (bajan por sus propios accesos)
		tunnelNode(plan, 30, 31);
		tunnelNode(plan, 70, 31);
		tunnelNode(plan, 89, 45);
		tunnelNode(plan, 89, 64);
		tunnelNode(plan, 60, 71);
		tunnelNode(plan, 40, 71);
		tunnelNode(plan, 11, 50);
		tunnelNode(plan, 50, 30);
		spawn(plan, 52, TUNNEL_FLOOR + 1, 32, AnimatronicKind.ANY);
		spawn(plan, 92, 1, 30, AnimatronicKind.ANY);
		spawn(plan, 26, 1, 80, AnimatronicKind.ANY);

		// --- Colocación en el mundo ---
		int placed = 0;
		BlockPos.Mutable m = new BlockPos.Mutable();
		List<Map.Entry<Long, BlockState>> deferred = new ArrayList<>();
		for (Map.Entry<Long, BlockState> e : plan.blocks.entrySet()) {
			BlockState s = e.getValue();
			// los bloques que dependen de un soporte se colocan al final
			if (s.getBlock() instanceof DoorBlock || s.isOf(Blocks.LADDER) || s.getBlock() instanceof WallPanelBlock
					|| s.getBlock() instanceof FlickeringLightBlock || s.getBlock() instanceof TrapdoorBlock
					|| s.getBlock() instanceof RepairStationBlock || s.getBlock() instanceof PatrolNodeBlock
					|| s.getBlock() instanceof SpawnMarkerBlock || s.isOf(ModBlocks.SHELTER_CORE)) {
				deferred.add(e);
				continue;
			}
			m.set(origin.getX() + BlockPos.unpackLongX(e.getKey()), origin.getY() + BlockPos.unpackLongY(e.getKey()), origin.getZ() + BlockPos.unpackLongZ(e.getKey()));
			world.setBlockState(m, s, Block.NOTIFY_LISTENERS);
			placed++;
		}
		for (Map.Entry<Long, BlockState> e : deferred) {
			m.set(origin.getX() + BlockPos.unpackLongX(e.getKey()), origin.getY() + BlockPos.unpackLongY(e.getKey()), origin.getZ() + BlockPos.unpackLongZ(e.getKey()));
			world.setBlockState(m, e.getValue(), Block.NOTIFY_LISTENERS);
			placed++;
		}
		BlockPos shelterPos = origin.add(50, 1, 50);
		// Actualización 1.1: cámaras, monitores, ventilación, casilleros y puertas de la oficina.
		placed += FacilityUpgrade.apply(world, origin);
		return new Result(placed, plan.stations, plan.nodes, shelterPos);
	}

	// ------------------------------------------------------------------
	// Ayudantes de construcción
	// ------------------------------------------------------------------

	private static void door(Plan plan, int x, int y, int z, Direction facing) {
		BlockState base = ModBlocks.SECURITY_DOOR.getDefaultState().with(DoorBlock.FACING, facing).with(DoorBlock.OPEN, false);
		plan.set(x, y, z, base.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		plan.set(x, y + 1, z, base.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		// El hueco de la puerta mide 2 de alto: por encima queda pared.
		for (int dy = 2; dy <= 3; dy++) {
			BlockState above = plan.get(x, y + dy, z);
			if (above == null || above.isAir()) plan.set(x, y + dy, z, ModBlocks.INDUSTRIAL_WALL_STRIPE.getDefaultState());
		}
		// suelo bajo la puerta
		plan.set(x, y - 1, z, ModBlocks.METAL_PLATING.getDefaultState());
	}

	private static void light(Plan plan, int x, int y, int z, boolean faulty) {
		plan.set(x, y, z, ModBlocks.FLICKERING_LIGHT.getDefaultState().with(FlickeringLightBlock.FAULTY, faulty).with(FlickeringLightBlock.LIT, true));
	}

	private static void lightsGrid(Plan plan, Space s, Random rand, double faultyChance) {
		int y = s.floorY + s.height;
		for (int x = s.x1 + 3; x <= s.x2 - 2; x += 7) {
			for (int z = s.z1 + 3; z <= s.z2 - 2; z += 7) {
				BlockState cur = plan.get(x, y, z);
				if (cur == null || cur.isAir()) {
					if (rand.nextFloat() < 0.85f) light(plan, x, y, z, rand.nextDouble() < faultyChance);
				}
			}
		}
	}

	private static void wallPanel(Plan plan, Block block, int x, int y, int z, Direction facing) {
		plan.set(x, y, z, block.getDefaultState().with(WallPanelBlock.FACING, facing));
	}

	private static void station(Plan plan, RepairType type, int x, int y, int z, Direction facing) {
		plan.set(x, y, z, ModBlocks.STATIONS.get(type).getDefaultState().with(RepairStationBlock.FACING, facing));
		plan.stations++;
	}

	private static void node(Plan plan, @Nullable Space room, Zone zone, int x, int z) {
		int y = room == null ? 1 : room.floorY + 1;
		BlockState cur = plan.get(x, y, z);
		if (cur != null && !cur.isAir()) return;
		plan.set(x, y, z, ModBlocks.PATROL_NODE.getDefaultState().with(PatrolNodeBlock.ZONE, zone));
		plan.nodes++;
	}

	private static void tunnelNode(Plan plan, int x, int z) {
		BlockState cur = plan.get(x, TUNNEL_FLOOR + 1, z);
		if (cur != null && !cur.isAir()) return;
		plan.set(x, TUNNEL_FLOOR + 1, z, ModBlocks.PATROL_NODE.getDefaultState().with(PatrolNodeBlock.ZONE, Zone.TUNNEL));
		plan.nodes++;
	}

	private static void spawn(Plan plan, int x, int y, int z, AnimatronicKind kind) {
		plan.set(x, y, z, ModBlocks.SPAWN_MARKER.getDefaultState().with(SpawnMarkerBlock.ANIMATRONIC, kind));
	}

	/**
	 * Pozo vertical con escalera desde el túnel hasta el suelo del nivel
	 * principal, tapado con una rejilla abatible. {@code facing} es hacia
	 * donde mira la escalera (la pared de apoyo queda detrás).
	 */
	private static void shaft(Plan plan, int x, int z, Direction facing) {
		Direction back = facing.getOpposite();
		int bx = x + back.getOffsetX(), bz = z + back.getOffsetZ();
		for (int y = TUNNEL_FLOOR + 1; y <= -1; y++) {
			// paredes del pozo en la zona de tierra
			if (y > TUNNEL_FLOOR + 3) {
				for (Direction d : Direction.Type.HORIZONTAL) {
					plan.set(x + d.getOffsetX(), y, z + d.getOffsetZ(), ModBlocks.INDUSTRIAL_CONCRETE.getDefaultState());
				}
			}
			plan.set(bx, y, bz, ModBlocks.METAL_PLATING.getDefaultState());
			plan.set(x, y, z, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, facing));
		}
		plan.set(x, 0, z, ModBlocks.VENT_GRATE.getDefaultState().with(TrapdoorBlock.FACING, facing)
				.with(TrapdoorBlock.HALF, BlockHalf.TOP).with(TrapdoorBlock.OPEN, false));
	}
}
