package com.fnaf.nightshift.campaign;

import com.fnaf.nightshift.block.ToolLockerBlock;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.item.FlashlightItem;
import com.fnaf.nightshift.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Recursos de cada noche. Reglas:
 * <ul>
 *   <li>Cada participante empieza sólo con la linterna (batería llena).</li>
 *   <li>Herramientas y repuestos de la noche anterior se retiran (inventarios y objetos tirados en el complejo),
 *       así un reinicio no acumula ni duplica objetos.</li>
 *   <li>Las herramientas están en casilleros; cuantas más noches, más se reparten por zonas peligrosas.
 *       Se abastece una copia de cada herramienta por participante (máximo 3).</li>
 *   <li>Los repuestos siguen saliendo de las cajas de suministros, que se rellenan cada noche.</li>
 *   <li>Si el mapa no tiene casilleros, se entrega el kit completo para que la noche sea completable.</li>
 * </ul>
 */
public final class ResourceManager {
	private static final Set<Item> NIGHT_ITEMS = Set.of(ModItems.WRENCH, ModItems.SCREWDRIVER, ModItems.PLIERS, ModItems.FUSE,
			ModItems.CABLE, ModItems.ELECTRONIC_PARTS, ModItems.BATTERY);

	private ResourceManager() {
	}

	public static boolean isNightItem(ItemStack s) {
		return NIGHT_ITEMS.contains(s.getItem());
	}

	public static void prepareNight(ServerWorld world, CampaignState s, NightDefinition def, List<ServerPlayerEntity> participants) {
		ModConfig c = ModConfig.get();
		if (c.resetInventoryEachNight) {
			for (ServerPlayerEntity p : participants) strip(p);
			clearDroppedItems(world, s);
		}
		boolean lockersAvailable = stockLockers(world, s, def, Math.max(1, participants.size()));
		for (ServerPlayerEntity p : participants) {
			if (c.giveStarterKit) {
				giveIfMissing(p, ModItems.FLASHLIGHT);
				for (int i = 0; i < p.getInventory().size(); i++) {
					ItemStack st = p.getInventory().getStack(i);
					if (st.isOf(ModItems.FLASHLIGHT)) {
						FlashlightItem.setBattery(st, FlashlightItem.maxBattery(world));
						FlashlightItem.setOn(st, false);
					}
				}
			}
			if (c.starterTools || !lockersAvailable) {
				NightManager.giveStarterKit(p);
				if (!lockersAvailable && !c.starterTools) {
					p.sendMessage(Text.translatable("message.fnaf_night_shift.no_lockers").formatted(Formatting.YELLOW), false);
				}
			}
		}
	}

	private static void strip(ServerPlayerEntity p) {
		for (int i = 0; i < p.getInventory().size(); i++) {
			if (isNightItem(p.getInventory().getStack(i))) p.getInventory().setStack(i, ItemStack.EMPTY);
		}
		p.getInventory().markDirty();
	}

	private static void clearDroppedItems(ServerWorld world, CampaignState s) {
		BlockPos shelter = s.getShelter(world);
		if (shelter == null) return;
		Box box = new Box(shelter).expand(110, 30, 110);
		for (ItemEntity e : world.getEntitiesByClass(ItemEntity.class, box, e -> isNightItem(e.getStack()))) e.discard();
	}

	/** Abastece los casilleros. Devuelve false si el mapa no tiene ninguno. */
	public static boolean stockLockers(ServerWorld world, CampaignState s, NightDefinition def, int copies) {
		List<BlockPos> lockers = new ArrayList<>();
		for (BlockPos p : new ArrayList<>(s.getLockers())) {
			BlockState st = world.getBlockState(p);
			if (st.getBlock() instanceof ToolLockerBlock) {
				lockers.add(p);
				if (st.get(ToolLockerBlock.STOCKED)) world.setBlockState(p, st.with(ToolLockerBlock.STOCKED, false), Block.NOTIFY_ALL);
			} else {
				s.unregisterLocker(world, p);
			}
		}
		if (lockers.isEmpty()) return false;
		copies = Math.max(1, Math.min(3, copies));
		List<BlockPos> free = new ArrayList<>(lockers);
		for (ToolLockerBlock.Tool tool : ToolLockerBlock.Tool.values()) {
			for (int i = 0; i < copies && !free.isEmpty(); i++) {
				BlockPos pick = weightedPick(world, free, def.toolRisk());
				free.remove(pick);
				BlockState st = world.getBlockState(pick);
				world.setBlockState(pick, st.with(ToolLockerBlock.TOOL, tool).with(ToolLockerBlock.STOCKED, true), Block.NOTIFY_ALL);
			}
		}
		return true;
	}

	/** Noches tempranas: casilleros seguros (nivel 0). Noches avanzadas: zonas peligrosas (nivel 2). */
	private static BlockPos weightedPick(ServerWorld world, List<BlockPos> lockers, double risk) {
		double[] weights = new double[lockers.size()];
		double total = 0;
		for (int i = 0; i < lockers.size(); i++) {
			int tier = world.getBlockState(lockers.get(i)).get(ToolLockerBlock.TIER);
			double target = risk * 2.0;
			weights[i] = 1.0 / (1.0 + 2.5 * Math.abs(tier - target));
			total += weights[i];
		}
		double r = world.random.nextDouble() * total;
		for (int i = 0; i < lockers.size(); i++) {
			r -= weights[i];
			if (r <= 0) return lockers.get(i);
		}
		return lockers.get(lockers.size() - 1);
	}

	private static void giveIfMissing(ServerPlayerEntity p, Item item) {
		if (p.getInventory().count(item) == 0) {
			ItemStack stack = new ItemStack(item);
			if (!p.getInventory().insertStack(stack)) p.dropItem(stack, false);
		}
	}
}
