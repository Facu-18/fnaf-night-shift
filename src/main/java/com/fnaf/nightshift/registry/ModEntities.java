package com.fnaf.nightshift.registry;

import com.fnaf.nightshift.FnafNightShift;
import com.fnaf.nightshift.entity.AnimatronicEntity;
import com.fnaf.nightshift.entity.AnimatronicKind;
import com.fnaf.nightshift.entity.BonnieEntity;
import com.fnaf.nightshift.entity.ChicaEntity;
import com.fnaf.nightshift.entity.FoxyEntity;
import com.fnaf.nightshift.entity.FreddyEntity;
import com.fnaf.nightshift.entity.GoldenFreddyEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.ArrayList;
import java.util.List;

public final class ModEntities {
	public static final List<Item> SPAWN_EGGS = new ArrayList<>();

	public static EntityType<BonnieEntity> BONNIE;
	public static EntityType<ChicaEntity> CHICA;
	public static EntityType<FoxyEntity> FOXY;
	public static EntityType<FreddyEntity> FREDDY;
	public static EntityType<GoldenFreddyEntity> GOLDEN_FREDDY;

	private ModEntities() {
	}

	public static void register() {
		BONNIE = Registry.register(Registries.ENTITY_TYPE, FnafNightShift.id("bonnie"),
				FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, BonnieEntity::new)
						.dimensions(EntityDimensions.fixed(0.6f, 2.5f)).trackRangeBlocks(80).trackedUpdateRate(2).build());
		CHICA = Registry.register(Registries.ENTITY_TYPE, FnafNightShift.id("chica"),
				FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, ChicaEntity::new)
						.dimensions(EntityDimensions.fixed(0.6f, 2.35f)).trackRangeBlocks(80).trackedUpdateRate(2).build());
		FOXY = Registry.register(Registries.ENTITY_TYPE, FnafNightShift.id("foxy"),
				FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, FoxyEntity::new)
						.dimensions(EntityDimensions.fixed(0.6f, 2.45f)).trackRangeBlocks(96).trackedUpdateRate(2).build());
		FREDDY = Registry.register(Registries.ENTITY_TYPE, FnafNightShift.id("freddy"),
				FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, FreddyEntity::new)
						.dimensions(EntityDimensions.fixed(0.6f, 2.5f)).trackRangeBlocks(80).trackedUpdateRate(2).build());
		GOLDEN_FREDDY = Registry.register(Registries.ENTITY_TYPE, FnafNightShift.id("golden_freddy"),
				FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, GoldenFreddyEntity::new)
						.dimensions(EntityDimensions.fixed(0.9f, 2.3f)).trackRangeBlocks(64).trackedUpdateRate(4).build());

		FabricDefaultAttributeRegistry.register(BONNIE, BonnieEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(CHICA, ChicaEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(FOXY, FoxyEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(FREDDY, FreddyEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(GOLDEN_FREDDY, GoldenFreddyEntity.createAttributes());

		egg("bonnie", BONNIE, 0x5B3F99, 0xC2185B);
		egg("chica", CHICA, 0xE8C33A, 0xE07A1F);
		egg("foxy", FOXY, 0xA3402A, 0x5A3A22);
		egg("freddy", FREDDY, 0x6B4226, 0x111111);
		egg("golden_freddy", GOLDEN_FREDDY, 0xC9A227, 0x3A2A10);
	}

	private static void egg(String id, EntityType<? extends AnimatronicEntity> type, int primary, int secondary) {
		SPAWN_EGGS.add(Registry.register(Registries.ITEM, FnafNightShift.id(id + "_spawn_egg"),
				new SpawnEggItem(type, primary, secondary, new Item.Settings())));
	}

	public static EntityType<? extends AnimatronicEntity> typeOf(AnimatronicKind kind) {
		return switch (kind) {
			case CHICA -> CHICA;
			case FOXY -> FOXY;
			case FREDDY -> FREDDY;
			case GOLDEN_FREDDY -> GOLDEN_FREDDY;
			default -> BONNIE;
		};
	}
}
