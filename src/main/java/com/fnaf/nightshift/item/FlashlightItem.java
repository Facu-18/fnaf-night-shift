package com.fnaf.nightshift.item;

import com.fnaf.nightshift.campaign.CampaignState;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.registry.ModSounds;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Linterna con batería. Clic derecho la enciende/apaga. Mientras está
 * encendida y en la mano, ilumina el entorno (ver {@link FlashlightLightManager})
 * y consume batería. Se recarga dentro del refugio o con baterías.
 */
public class FlashlightItem extends Item {
	public static final String NBT_ON = "On";
	public static final String NBT_BATTERY = "Battery";
	public static final String NBT_MAX = "MaxBattery";

	public FlashlightItem(Settings settings) {
		super(settings);
	}

	public static int maxBattery(@Nullable World world) {
		double mult = 1.0;
		if (world instanceof ServerWorld sw) mult = CampaignState.get(sw.getServer()).getDifficulty().battery;
		return (int) (ModConfig.get().flashlightBatterySeconds * 20 * mult);
	}

	public static boolean isOn(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		return nbt != null && nbt.getBoolean(NBT_ON);
	}

	public static int getBattery(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		if (nbt == null || !nbt.contains(NBT_BATTERY)) return -1; // -1 = nueva (llena)
		return nbt.getInt(NBT_BATTERY);
	}

	/** Fracción 0..1 usando el máximo de la configuración local. */
	public static float getBatteryFraction(ItemStack stack) {
		int b = getBattery(stack);
		NbtCompound nbt = stack.getNbt();
		int max = nbt != null && nbt.getInt(NBT_MAX) > 0 ? nbt.getInt(NBT_MAX) : ModConfig.get().flashlightBatterySeconds * 20;
		if (b < 0) return 1.0f;
		return MathHelper.clamp(b / (float) max, 0f, 1f);
	}

	public static void setBattery(ItemStack stack, int value) {
		stack.getOrCreateNbt().putInt(NBT_BATTERY, Math.max(0, value));
	}

	public static void setOn(ItemStack stack, boolean on) {
		stack.getOrCreateNbt().putBoolean(NBT_ON, on);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient) {
			int max = maxBattery(world);
			if (getBattery(stack) < 0) setBattery(stack, max);
			if (!isOn(stack) && getBattery(stack) <= 0) {
				world.playSound(null, user.getBlockPos(), ModSounds.FLASHLIGHT_DEAD, SoundCategory.PLAYERS, 0.8f, 1.0f);
				user.sendMessage(Text.translatable("message.fnaf_night_shift.flashlight_empty").formatted(Formatting.RED), true);
			} else {
				setOn(stack, !isOn(stack));
				world.playSound(null, user.getBlockPos(), ModSounds.FLASHLIGHT_CLICK, SoundCategory.PLAYERS, 0.8f, isOn(stack) ? 1.2f : 0.9f);
			}
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (world.isClient || !(entity instanceof ServerPlayerEntity player)) return;
		// Se procesa cada 10 ticks para no resincronizar el objeto en cada tick.
		if (world.getTime() % 10 != 0) return;
		int max = maxBattery(world);
		if (stack.getOrCreateNbt().getInt(NBT_MAX) != max) stack.getOrCreateNbt().putInt(NBT_MAX, max);
		int battery = getBattery(stack);
		if (battery < 0) battery = max;
		int before = battery;

		boolean inHand = selected || player.getOffHandStack() == stack;
		if (isOn(stack) && inHand && !player.isCreative()) {
			battery -= 10;
			if (battery <= 0) {
				battery = 0;
				setOn(stack, false);
				world.playSound(null, player.getBlockPos(), ModSounds.FLASHLIGHT_DEAD, SoundCategory.PLAYERS, 1.0f, 0.8f);
				player.sendMessage(Text.translatable("message.fnaf_night_shift.flashlight_died").formatted(Formatting.RED), true);
			}
		}
		if (battery < max && CampaignState.get(player.getServer()).isInShelter(player)) {
			battery = Math.min(max, battery + (int) (ModConfig.get().flashlightRechargePerSecond * 10));
		}
		battery = Math.min(battery, max);
		if (battery != before || getBattery(stack) < 0) setBattery(stack, battery);
	}

	@Override
	public boolean isItemBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getItemBarStep(ItemStack stack) {
		return Math.round(13.0f * getBatteryFraction(stack));
	}

	@Override
	public int getItemBarColor(ItemStack stack) {
		float f = getBatteryFraction(stack);
		return MathHelper.hsvToRgb(f / 3.0f, 0.9f, 1.0f);
	}

	@Override
	public boolean allowNbtUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
		return false;
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		int pct = Math.round(getBatteryFraction(stack) * 100);
		tooltip.add(Text.translatable("tooltip.fnaf_night_shift.flashlight_battery", pct).formatted(pct > 25 ? Formatting.GREEN : Formatting.RED));
		tooltip.add(Text.translatable("tooltip.fnaf_night_shift.flashlight").formatted(Formatting.GRAY));
	}
}
