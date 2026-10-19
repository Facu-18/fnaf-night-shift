package com.fnaf.nightshift.campaign;

import com.fnaf.nightshift.block.FlickeringLightBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Oscuridad progresiva: al comenzar cada noche una parte de las lámparas
 * de las zonas peligrosas (nunca las de la oficina) se funde o empieza a
 * fallar. La proporción crece con la noche. Al empezar la noche siguiente
 * se restauran antes de volver a elegir, así el mapa nunca queda roto.
 */
public final class AtmosphereManager {
	private static List<BlockPos> lights = List.of();
	private static BlockPos scannedFor;

	private AtmosphereManager() {
	}

	private static List<BlockPos> scan(ServerWorld world, BlockPos shelter) {
		if (shelter.equals(scannedFor)) return lights;
		List<BlockPos> found = new ArrayList<>();
		for (BlockPos p : BlockPos.iterate(shelter.add(-75, -12, -75), shelter.add(75, 12, 75))) {
			if (world.isChunkLoaded(p) && world.getBlockState(p).getBlock() instanceof FlickeringLightBlock) found.add(p.toImmutable());
		}
		lights = found;
		scannedFor = shelter.toImmutable();
		return lights;
	}

	public static void applyNightDarkness(ServerWorld world, CampaignState s, NightDefinition def) {
		BlockPos shelter = s.getShelter(world);
		if (shelter == null) return;
		List<BlockPos> candidates = new ArrayList<>();
		double officeR = s.shelterRadius() + 2;
		for (BlockPos p : scan(world, shelter)) {
			BlockState st = world.getBlockState(p);
			if (!(st.getBlock() instanceof FlickeringLightBlock)) continue;
			if (st.get(FlickeringLightBlock.DEAD)) {
				world.setBlockState(p, st.with(FlickeringLightBlock.DEAD, false).with(FlickeringLightBlock.LIT, true), Block.NOTIFY_LISTENERS);
				if (st.get(FlickeringLightBlock.FAULTY)) world.scheduleBlockTick(p, st.getBlock(), 20);
			}
			if (p.getSquaredDistance(shelter) > officeR * officeR) candidates.add(p);
		}
		Collections.shuffle(candidates, new java.util.Random(world.random.nextLong()));
		int n = (int) (candidates.size() * def.darkness());
		for (int i = 0; i < n; i++) {
			BlockPos p = candidates.get(i);
			BlockState st = world.getBlockState(p);
			if (i < n * 0.55) {
				world.setBlockState(p, st.with(FlickeringLightBlock.DEAD, true).with(FlickeringLightBlock.LIT, false), Block.NOTIFY_LISTENERS);
			} else if (!st.get(FlickeringLightBlock.FAULTY)) {
				world.setBlockState(p, st.with(FlickeringLightBlock.FAULTY, true), Block.NOTIFY_LISTENERS);
				world.scheduleBlockTick(p, st.getBlock(), 10 + world.random.nextInt(40));
			}
		}
	}

	/** Restaura las lámparas fundidas (fin de noche). */
	public static void restore(ServerWorld world, CampaignState s) {
		BlockPos shelter = s.getShelter(world);
		if (shelter == null) return;
		for (BlockPos p : scan(world, shelter)) {
			BlockState st = world.getBlockState(p);
			if (st.getBlock() instanceof FlickeringLightBlock && st.get(FlickeringLightBlock.DEAD)) {
				world.setBlockState(p, st.with(FlickeringLightBlock.DEAD, false).with(FlickeringLightBlock.LIT, true), Block.NOTIFY_LISTENERS);
				if (st.get(FlickeringLightBlock.FAULTY)) world.scheduleBlockTick(p, st.getBlock(), 20);
			}
		}
	}

	public static void invalidate() {
		scannedFor = null;
	}
}
