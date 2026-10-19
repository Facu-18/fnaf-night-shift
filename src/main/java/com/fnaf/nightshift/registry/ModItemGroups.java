package com.fnaf.nightshift.registry;

import com.fnaf.nightshift.FnafNightShift;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItemGroups {
	private ModItemGroups() {
	}

	public static void register() {
		ItemGroup group = FabricItemGroup.builder()
				.icon(() -> new ItemStack(ModItems.FLASHLIGHT))
				.displayName(Text.translatable("itemGroup.fnaf_night_shift.main"))
				.entries((context, entries) -> {
					for (Item item : ModItems.CREATIVE_ORDER) entries.add(item);
					for (Item egg : ModEntities.SPAWN_EGGS) entries.add(egg);
					for (Block block : ModBlocks.CREATIVE_ORDER) entries.add(block);
				})
				.build();
		Registry.register(Registries.ITEM_GROUP, FnafNightShift.id("main"), group);
	}
}
