package com.fnaf.nightshift.test;

import com.fnaf.nightshift.block.ToolLockerBlock;
import com.fnaf.nightshift.campaign.CameraManager;
import com.fnaf.nightshift.campaign.CampaignState;
import com.fnaf.nightshift.campaign.MenuActions;
import com.fnaf.nightshift.campaign.NightManager;
import com.fnaf.nightshift.campaign.NightPhase;
import com.fnaf.nightshift.campaign.StaminaManager;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.entity.AnimatronicEntity;
import com.fnaf.nightshift.entity.AnimatronicKind;
import com.fnaf.nightshift.registry.ModItems;
import com.fnaf.nightshift.repair.RepairStationBlockEntity;
import com.fnaf.nightshift.world.FacilityGenerator;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.UUID;

/**
 * Multijugador en el servidor (autoridad): dos jugadores simulados en el
 * mismo mundo. Estado compartido único, estamina individual, recursos
 * escalados por jugador, cámaras por jugador y derrota sólo cuando atrapan
 * a todos los participantes.
 */
public class FnafMultiplayerGameTests implements FabricGameTest {

	private static void check(boolean c, String m) {
		if (!c) throw new GameTestException(m);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "multiplayer", tickLimit = 600)
	public void twoPlayersShareTheNight(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		MinecraftServer server = world.getServer();
		BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN).add(16, 110, 16);
		for (int cx = (origin.getX() >> 4) - 1; cx <= ((origin.getX() + FacilityGenerator.SIZE_X) >> 4) + 1; cx++)
			for (int cz = (origin.getZ() >> 4) - 1; cz <= ((origin.getZ() + FacilityGenerator.SIZE_Z) >> 4) + 1; cz++)
				world.setChunkForced(cx, cz, true);
		FakePlayer a = FakePlayer.get(world, new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000000a"), "JugadorA"));
		FakePlayer b = FakePlayer.get(world, new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000000b"), "JugadorB"));
		AnimatronicEntity[] bonnie = new AnimatronicEntity[1];

		ctx.runAtTick(2, () -> {
			FacilityGenerator.Result r = FacilityGenerator.generate(world, origin);
			CampaignState s = CampaignState.get(server);
			for (FakePlayer p : new FakePlayer[]{a, b}) {
				p.changeGameMode(GameMode.SURVIVAL);
				p.refreshPositionAndAngles(r.shelter().getX() + 2.5, r.shelter().getY(), r.shelter().getZ() + 0.5, 0, 0);
				world.onPlayerConnected(p);
			}
			s.resetProgress();
			MenuActions.handle(a, MenuActions.CONTINUE, 0);
			check(s.getPhase() == NightPhase.REPAIRING, "la noche no empezó");
			check(s.getParticipants().contains(a.getUuid()) && s.getParticipants().contains(b.getUuid()), "ambos deben ser participantes");
			check(a.getInventory().count(ModItems.FLASHLIGHT) == 1 && b.getInventory().count(ModItems.FLASHLIGHT) == 1, "cada uno con su linterna");
			int stocked = 0;
			for (BlockPos p : s.getLockers()) if (world.getBlockState(p).get(ToolLockerBlock.STOCKED)) stocked++;
			check(stocked == 6, "con 2 jugadores debe haber 2 copias de cada herramienta, hay " + stocked);
			s.setGraceTicks(0);
			s.setSpawned(true);

			// estamina individual
			ModConfig c = ModConfig.get();
			StaminaManager.reset(a.getUuid());
			StaminaManager.reset(b.getUuid());
			for (int i = 0; i < 400 && !StaminaManager.isExhausted(a.getUuid()); i++) {
				a.setSprinting(true);
				a.setPosition(a.getX() + 0.28, a.getY(), a.getZ());
				StaminaManager.tickPlayer(a, s, c, true);
				StaminaManager.tickPlayer(b, s, c, true);
			}
			check(StaminaManager.isExhausted(a.getUuid()), "A debería estar agotado");
			check(!StaminaManager.isExhausted(b.getUuid()) && StaminaManager.get(b.getUuid()) >= c.staminaMax - 0.01f, "la estamina de B no debe verse afectada");

			// objetivos compartidos: lo que repara A cuenta para todos
			BlockPos first = s.getRequired().iterator().next();
			((RepairStationBlockEntity) world.getBlockEntity(first)).resetRepaired();
			NightManager.onRepairCompleted(world, first, a);
			NightManager.onRepairCompleted(world, first, b);
			check(s.getCompleted().size() == 1, "la reparación compartida debe contar una sola vez");

			// cámaras: cada jugador por separado
			BlockPos monitor = origin.add(47, 1, 44);
			b.refreshPositionAndAngles(monitor.getX() + 0.5, monitor.getY(), monitor.getZ() + 1.5, 0, 0);
			CameraManager.open(b, monitor);
			check(CameraManager.isViewing(b.getUuid()) && !CameraManager.isViewing(a.getUuid()), "las cámaras son por jugador");

			// los animatrónicos atrapan a cualquiera; con uno libre la noche sigue
			bonnie[0] = NightManager.spawn(world, AnimatronicKind.BONNIE, r.shelter().add(3, 0, 3), -1);
			NightManager.catchPlayer(a, bonnie[0]);
			check(s.isCaught(a.getUuid()) && !s.isCaught(b.getUuid()), "sólo A atrapado");
		});

		ctx.runAtTick(80, () -> {
			CampaignState s = CampaignState.get(server);
			check(s.getPhase().isActive(), "con B libre la noche debe continuar, fase " + s.getPhase());
			NightManager.catchPlayer(b, bonnie[0]);
			check(!CameraManager.isViewing(b.getUuid()), "al ser atrapado se cierran sus cámaras");
		});

		ctx.runAtTick(160, () -> {
			CampaignState s = CampaignState.get(server);
			check(s.getPhase() == NightPhase.DEFEAT, "con todos atrapados la noche se pierde, fase " + s.getPhase());
			bonnie[0].discard();
			for (FakePlayer p : new FakePlayer[]{a, b}) {
				world.removePlayer(p, Entity.RemovalReason.DISCARDED);
				StaminaManager.remove(p);
			}
			s.resetProgress();
			ctx.complete();
		});
	}
}
