package com.fnaf.nightshift.test;

import com.fnaf.nightshift.campaign.CampaignState;
import com.fnaf.nightshift.campaign.MenuActions;
import com.fnaf.nightshift.campaign.NightManager;
import com.fnaf.nightshift.campaign.OfficeManager;
import com.fnaf.nightshift.entity.AnimatronicEntity;
import com.fnaf.nightshift.entity.AnimatronicKind;
import com.fnaf.nightshift.world.FacilityGenerator;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;

import java.util.UUID;

/**
 * Puertas de la oficina ABIERTAS: cada animatrónico que camina (Bonnie,
 * Chica, Freddy) debe cruzar el marco y entrar en la oficina en poco tiempo.
 */
public class FnafDoorGameTests implements FabricGameTest {

	private static void check(boolean c, String m) {
		if (!c) throw new GameTestException(m);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "doors", tickLimit = 1400)
	public void animatronicsWalkThroughOpenOfficeDoors(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		MinecraftServer server = world.getServer();
		BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN).add(16, 145, 16);
		for (int cx = (origin.getX() >> 4) - 1; cx <= ((origin.getX() + FacilityGenerator.SIZE_X) >> 4) + 1; cx++)
			for (int cz = (origin.getZ() >> 4) - 1; cz <= ((origin.getZ() + FacilityGenerator.SIZE_Z) >> 4) + 1; cz++)
				world.setChunkForced(cx, cz, true);
		FakePlayer player = FakePlayer.get(world, new GameProfile(UUID.fromString("00000000-0000-0000-0000-0000000000d0"), "Guardia"));
		AnimatronicKind[] kinds = {AnimatronicKind.BONNIE, AnimatronicKind.CHICA, AnimatronicKind.FREDDY};
		Direction[] sides = {Direction.WEST, Direction.EAST, Direction.NORTH};
		AnimatronicEntity[] mobs = new AnimatronicEntity[3];
		int[] entered = {-1, -1, -1};
		BlockPos[] shelter = new BlockPos[1];

		ctx.runAtTick(2, () -> {
			for (net.minecraft.server.network.ServerPlayerEntity old : new java.util.ArrayList<>(world.getPlayers())) {
				if (old instanceof FakePlayer) world.removePlayer(old, Entity.RemovalReason.DISCARDED);
			}
			FacilityGenerator.Result r = FacilityGenerator.generate(world, origin);
			shelter[0] = r.shelter();
			CampaignState s = CampaignState.get(server);
			player.changeGameMode(GameMode.SURVIVAL);
			player.refreshPositionAndAngles(r.shelter().getX() + 0.5, r.shelter().getY(), r.shelter().getZ() + 2.5, 0, 0);
			world.onPlayerConnected(player);
			s.resetProgress();
			MenuActions.handle(player, MenuActions.CONTINUE, 0);
			s.setGraceTicks(0);
			s.setSpawned(true);
			for (OfficeManager.OfficeDoor d : OfficeManager.doors(world, s)) {
				BlockState st = world.getBlockState(d.lower());
				((DoorBlock) st.getBlock()).setOpen(null, world, st, d.lower(), true);
			}
		});

		for (int i = 0; i < 3; i++) {
			int idx = i;
			ctx.runAtTick(10 + idx * 400, () -> {
				CampaignState s = CampaignState.get(server);
				// el jugador vuelve a estar libre para cada animatrónico
				s.getCaught().clear();
				player.refreshPositionAndAngles(shelter[0].getX() + 0.5, shelter[0].getY(), shelter[0].getZ() + 2.5, 0, 0);
				OfficeManager.OfficeDoor door = OfficeManager.doors(world, s).stream().filter(d -> d.outward() == sides[idx]).findFirst().orElseThrow();
				BlockPos start = door.approach().offset(sides[idx], 5);
				mobs[idx] = NightManager.spawn(world, kinds[idx], start, -1);
				check(mobs[idx] != null, "no apareció " + kinds[idx]);
				mobs[idx].getAi().startLurk(door);
			});
			for (int t = 12 + idx * 400; t < 10 + (idx + 1) * 400; t += 2) {
				int tick = t;
				ctx.runAtTick(t, () -> {
					if (entered[idx] < 0 && mobs[idx] != null && CampaignState.get(server).isInShelter(fakeAt(mobs[idx]))) entered[idx] = tick - (10 + idx * 400);
				});
			}
			ctx.runAtTick(5 + (idx + 1) * 400, () -> {
				com.fnaf.nightshift.FnafNightShift.LOGGER.info("[puertas] {} entró en {} ticks", kinds[idx], entered[idx]);
				check(entered[idx] >= 0, kinds[idx] + " no consiguió cruzar la puerta abierta " + sides[idx]
						+ " (posición final " + mobs[idx].getBlockPos() + ", estado " + mobs[idx].getAi().getState() + ")");
				check(entered[idx] <= 200, kinds[idx] + " tardó demasiado en cruzar la puerta: " + entered[idx] + " ticks");
				mobs[idx].discard();
			});
		}
		ctx.runAtTick(1250, () -> {
			NightManager.stopNight(server);
			world.removePlayer(player, Entity.RemovalReason.DISCARDED);
			CampaignState.get(server).resetProgress();
			ctx.complete();
		});
	}

	/** isInShelter recibe un jugador; se usa un jugador simulado temporal en la posición del animatrónico. */
	private static net.minecraft.entity.player.PlayerEntity fakeAt(AnimatronicEntity e) {
		FakePlayer probe = FakePlayer.get((ServerWorld) e.getWorld(), new GameProfile(UUID.fromString("00000000-0000-0000-0000-0000000000ff"), "Sonda"));
		probe.setPosition(e.getX(), e.getY(), e.getZ());
		return probe;
	}
}
