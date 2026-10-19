package com.fnaf.nightshift.item;

import com.fnaf.nightshift.registry.ModItems;
import com.fnaf.nightshift.registry.ModSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Batería de repuesto: recarga por completo la linterna con menos carga del inventario. */
public class BatteryItem extends TooltipItem {
	public BatteryItem(Settings settings) {
		super(settings, "battery");
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack battery = user.getStackInHand(hand);
		if (world.isClient) return TypedActionResult.success(battery, true);
		ItemStack target = null;
		float lowest = 2f;
		for (int i = 0; i < user.getInventory().size(); i++) {
			ItemStack s = user.getInventory().getStack(i);
			if (s.isOf(ModItems.FLASHLIGHT)) {
				float f = FlashlightItem.getBatteryFraction(s);
				if (f < lowest) {
					lowest = f;
					target = s;
				}
			}
		}
		if (target == null || lowest >= 0.999f) {
			user.sendMessage(Text.translatable("message.fnaf_night_shift.battery_no_target").formatted(Formatting.GRAY), true);
			return TypedActionResult.fail(battery);
		}
		FlashlightItem.setBattery(target, FlashlightItem.maxBattery(world));
		if (!user.isCreative()) battery.decrement(1);
		world.playSound(null, user.getBlockPos(), ModSounds.FLASHLIGHT_CLICK, SoundCategory.PLAYERS, 1.0f, 0.6f);
		user.sendMessage(Text.translatable("message.fnaf_night_shift.battery_used").formatted(Formatting.GREEN), true);
		return TypedActionResult.consume(battery);
	}
}
