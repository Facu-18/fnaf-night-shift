package com.fnaf.nightshift.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Objeto simple con una línea de ayuda traducible. */
public class TooltipItem extends Item {
	private final String key;

	public TooltipItem(Settings settings, String key) {
		super(settings);
		this.key = key;
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("tooltip.fnaf_night_shift." + key).formatted(Formatting.GRAY));
	}
}
