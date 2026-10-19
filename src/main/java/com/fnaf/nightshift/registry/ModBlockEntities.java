package com.fnaf.nightshift.registry;

import com.fnaf.nightshift.FnafNightShift;
import com.fnaf.nightshift.repair.RepairStationBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {
	public static BlockEntityType<RepairStationBlockEntity> REPAIR_STATION;

	private ModBlockEntities() {
	}

	public static void register() {
		REPAIR_STATION = Registry.register(Registries.BLOCK_ENTITY_TYPE, FnafNightShift.id("repair_station"),
				FabricBlockEntityTypeBuilder.create(RepairStationBlockEntity::new, ModBlocks.STATIONS.values().toArray(new Block[0])).build());
	}
}
