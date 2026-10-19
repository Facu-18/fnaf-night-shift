package com.fnaf.nightshift.item;

import com.fnaf.nightshift.entity.AnimatronicEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Herramienta de reparación (llave inglesa, destornillador o alicate).
 * Es obligatoria para reparar ciertas instalaciones y se desgasta con el uso.
 * Golpear a un animatrónico con ella lo aturde un instante (último recurso).
 */
public class RepairToolItem extends TooltipItem {
	public RepairToolItem(Settings settings, String kind) {
		super(settings, kind);
	}

	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (target instanceof AnimatronicEntity animatronic) {
			animatronic.stun(25);
			stack.damage(4, attacker, e -> e.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND));
		}
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		super.appendTooltip(stack, world, tooltip, context);
		tooltip.add(Text.translatable("tooltip.fnaf_night_shift.tool_stun").formatted(Formatting.DARK_GRAY));
	}
}
