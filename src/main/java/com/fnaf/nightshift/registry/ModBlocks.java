package com.fnaf.nightshift.registry;

import com.fnaf.nightshift.FnafNightShift;
import com.fnaf.nightshift.block.FlashlightLightBlock;
import com.fnaf.nightshift.block.FlickeringLightBlock;
import com.fnaf.nightshift.block.PatrolNodeBlock;
import com.fnaf.nightshift.block.SecurityDoorBlock;
import com.fnaf.nightshift.block.ShapedDecorBlock;
import com.fnaf.nightshift.block.ShelterCoreBlock;
import com.fnaf.nightshift.block.SpawnMarkerBlock;
import com.fnaf.nightshift.block.SupplyCrateBlock;
import com.fnaf.nightshift.block.VentGrateBlock;
import com.fnaf.nightshift.block.WallPanelBlock;
import com.fnaf.nightshift.repair.RepairStationBlock;
import com.fnaf.nightshift.repair.RepairType;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.Instrument;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModBlocks {
	/** Bloques que tienen objeto y aparecen en la pestaña creativa, en orden. */
	public static final List<Block> CREATIVE_ORDER = new ArrayList<>();
	public static final Map<RepairType, RepairStationBlock> STATIONS = new EnumMap<>(RepairType.class);

	// --- Materiales del escenario ---
	public static final Block INDUSTRIAL_CONCRETE = decor("industrial_concrete", MapColor.GRAY, BlockSoundGroup.STONE, 2.0f);
	public static final Block CRACKED_CONCRETE = decor("cracked_concrete", MapColor.GRAY, BlockSoundGroup.STONE, 2.0f);
	public static final Block METAL_PLATING = decor("metal_plating", MapColor.IRON_GRAY, BlockSoundGroup.METAL, 3.0f);
	public static final Block RUSTY_METAL = decor("rusty_metal", MapColor.ORANGE, BlockSoundGroup.METAL, 3.0f);
	public static final Block INDUSTRIAL_WALL = decor("industrial_wall", MapColor.LIGHT_GRAY, BlockSoundGroup.STONE, 2.0f);
	public static final Block INDUSTRIAL_WALL_STRIPE = decor("industrial_wall_stripe", MapColor.LIGHT_GRAY, BlockSoundGroup.STONE, 2.0f);
	public static final Block CHECKERED_FLOOR = decor("checkered_floor", MapColor.WHITE, BlockSoundGroup.STONE, 1.5f);
	public static final Block DIRTY_TILES = decor("dirty_tiles", MapColor.TERRACOTTA_WHITE, BlockSoundGroup.STONE, 1.5f);
	public static final Block PIZZERIA_WALL = decor("pizzeria_wall", MapColor.TERRACOTTA_RED, BlockSoundGroup.STONE, 1.5f);
	public static final Block HAZARD_BLOCK = decor("hazard_block", MapColor.YELLOW, BlockSoundGroup.METAL, 3.0f);
	public static final Block CEILING_PANEL = decor("ceiling_panel", MapColor.DEEPSLATE_GRAY, BlockSoundGroup.METAL, 2.0f);
	public static final Block STAGE_FLOOR = decor("stage_floor", MapColor.DARK_CRIMSON, BlockSoundGroup.WOOD, 2.0f);
	public static final Block STAGE_CURTAIN = decor("stage_curtain", MapColor.PURPLE, BlockSoundGroup.WOOL, 0.8f);
	public static final Block CABLE_BUNDLE = decor("cable_bundle", MapColor.BLACK, BlockSoundGroup.WOOL, 1.0f);
	public static final Block GENERATOR_CASING = decor("generator_casing", MapColor.GREEN, BlockSoundGroup.METAL, 3.0f);
	public static final Block METAL_GRATE = register("metal_grate", new Block(AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY)
			.strength(3.0f).sounds(BlockSoundGroup.METAL).nonOpaque().allowsSpawning((s, w, p, e) -> false)
			.solidBlock((s, w, p) -> false).suffocates((s, w, p) -> false).blockVision((s, w, p) -> false)), true);

	// --- Funcionales ---
	public static final Block FLICKERING_LIGHT = register("flickering_light", new FlickeringLightBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.WHITE).strength(0.8f).sounds(BlockSoundGroup.GLASS).nonOpaque().noCollision()
			.luminance(s -> s.get(FlickeringLightBlock.LIT) ? 13 : 0)), true);
	public static final Block EMERGENCY_LIGHT = register("emergency_light", new WallPanelBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.RED).strength(0.8f).sounds(BlockSoundGroup.GLASS).nonOpaque().noCollision().luminance(s -> 7), 5, 4, 5), true);
	public static final Block SECURITY_DOOR = register("security_door", new SecurityDoorBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(5.0f).sounds(BlockSoundGroup.METAL).nonOpaque().pistonBehavior(PistonBehavior.DESTROY)), true);
	public static final Block VENT_GRATE = register("vent_grate", new VentGrateBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(3.0f).sounds(BlockSoundGroup.METAL).nonOpaque()
			.allowsSpawning((s, w, p, e) -> false)), true);
	public static final Block SUPPLY_CRATE = register("supply_crate", new SupplyCrateBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.OAK_TAN).strength(2.0f).sounds(BlockSoundGroup.WOOD).instrument(Instrument.BASS)), true);
	public static final Block SIGN_EXIT = sign("sign_exit");
	public static final Block SIGN_DANGER = sign("sign_danger");
	public static final Block SIGN_PIZZERIA = sign("sign_pizzeria");
	public static final Block SIGN_STAFF = sign("sign_staff");
	public static final Block PARTY_TABLE = register("party_table", new ShapedDecorBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.WHITE).strength(1.0f).sounds(BlockSoundGroup.WOOD).nonOpaque(),
			Block.createCuboidShape(0, 0, 0, 16, 13, 16)), true);
	public static final Block SHELTER_CORE = register("shelter_core", new ShelterCoreBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.CYAN).strength(-1.0f, 3600000.0f).sounds(BlockSoundGroup.METAL).luminance(s -> 12).dropsNothing()), true);
	public static final Block PATROL_NODE = register("patrol_node", new PatrolNodeBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.BLUE).strength(0.5f).sounds(BlockSoundGroup.METAL).nonOpaque().noCollision()), true);
	public static final Block SPAWN_MARKER = register("spawn_marker", new SpawnMarkerBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.RED).strength(0.5f).sounds(BlockSoundGroup.METAL).nonOpaque().noCollision()), true);
	public static final Block FLASHLIGHT_LIGHT = register("flashlight_light", new FlashlightLightBlock(AbstractBlock.Settings.create()
			.replaceable().noCollision().dropsNothing().air().luminance(s -> 14).strength(-1.0f, 3600000.0f)
			.pistonBehavior(PistonBehavior.DESTROY).nonOpaque()), false);

	// --- Actualización 1.1 ---
	public static final Block CAMERA_MONITOR = register("camera_monitor", new com.fnaf.nightshift.block.CameraMonitorBlock(
			AbstractBlock.Settings.create().mapColor(MapColor.BLACK).strength(-1.0f, 3600000.0f).sounds(BlockSoundGroup.METAL)
					.nonOpaque().luminance(s -> 5)), true);
	public static final Block VENT_CONTROL_PANEL = register("vent_control_panel", new com.fnaf.nightshift.block.VentControlPanelBlock(
			AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(-1.0f, 3600000.0f).sounds(BlockSoundGroup.METAL)
					.nonOpaque().noCollision().luminance(s -> 4)), true);
	public static final Block TOOL_LOCKER = register("tool_locker", new com.fnaf.nightshift.block.ToolLockerBlock(
			AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(-1.0f, 3600000.0f).sounds(BlockSoundGroup.METAL)), true);

	static {
		for (RepairType type : RepairType.values()) {
			if (type == RepairType.CAMERA) {
				RepairStationBlock camera = new com.fnaf.nightshift.block.SecurityCameraBlock(AbstractBlock.Settings.create()
						.mapColor(MapColor.BLACK).strength(-1.0f, 3600000.0f).sounds(BlockSoundGroup.METAL).nonOpaque().noCollision()
						.luminance(s -> s.get(RepairStationBlock.BROKEN) ? 0 : 3));
				register("security_camera", camera, true);
				STATIONS.put(type, camera);
				continue;
			}
			RepairStationBlock block = new RepairStationBlock(type, AbstractBlock.Settings.create()
					.mapColor(MapColor.IRON_GRAY).strength(-1.0f, 3600000.0f).sounds(BlockSoundGroup.METAL).nonOpaque()
					.luminance(s -> s.get(RepairStationBlock.BROKEN) ? 2 : 6));
			register("station_" + type.id(), block, true);
			STATIONS.put(type, block);
		}
	}

	private ModBlocks() {
	}

	private static Block decor(String id, MapColor color, BlockSoundGroup sounds, float hardness) {
		return register(id, new Block(AbstractBlock.Settings.create().mapColor(color).strength(hardness, 6.0f).sounds(sounds)), true);
	}

	private static Block sign(String id) {
		return register(id, new WallPanelBlock(AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(1.0f)
				.sounds(BlockSoundGroup.METAL).nonOpaque().noCollision().luminance(s -> id.equals("sign_exit") ? 5 : 0), 1, 0, 3), true);
	}

	private static <T extends Block> T register(String id, T block, boolean withItem) {
		Registry.register(Registries.BLOCK, FnafNightShift.id(id), block);
		if (withItem) {
			Registry.register(Registries.ITEM, FnafNightShift.id(id), new BlockItem(block, new Item.Settings()));
			CREATIVE_ORDER.add(block);
		}
		return block;
	}

	public static void register() {
		// La carga de la clase registra todo.
	}
}
