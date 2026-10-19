package com.fnaf.nightshift.registry;

import com.fnaf.nightshift.FnafNightShift;
import com.fnaf.nightshift.item.BatteryItem;
import com.fnaf.nightshift.item.FlashlightItem;
import com.fnaf.nightshift.item.RepairToolItem;
import com.fnaf.nightshift.item.TooltipItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.ArrayList;
import java.util.List;

public final class ModItems {
	public static final List<Item> CREATIVE_ORDER = new ArrayList<>();

	public static final Item FLASHLIGHT = register("flashlight", new FlashlightItem(new Item.Settings().maxCount(1)));
	public static final Item WRENCH = register("wrench", new RepairToolItem(new Item.Settings().maxDamage(96), "wrench"));
	public static final Item SCREWDRIVER = register("screwdriver", new RepairToolItem(new Item.Settings().maxDamage(96), "screwdriver"));
	public static final Item PLIERS = register("pliers", new RepairToolItem(new Item.Settings().maxDamage(96), "pliers"));
	public static final Item FUSE = register("fuse", new TooltipItem(new Item.Settings().maxCount(16), "fuse"));
	public static final Item CABLE = register("cable", new TooltipItem(new Item.Settings().maxCount(16), "cable"));
	public static final Item ELECTRONIC_PARTS = register("electronic_parts", new TooltipItem(new Item.Settings().maxCount(16), "electronic_parts"));
	public static final Item BATTERY = register("battery", new BatteryItem(new Item.Settings().maxCount(8)));

	private ModItems() {
	}

	static <T extends Item> T register(String id, T item) {
		Registry.register(Registries.ITEM, FnafNightShift.id(id), item);
		CREATIVE_ORDER.add(item);
		return item;
	}

	public static void register() {
	}
}
