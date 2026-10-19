package com.fnaf.nightshift.test;

import com.fnaf.nightshift.ai.AiState;
import com.fnaf.nightshift.block.ToolLockerBlock;
import com.fnaf.nightshift.campaign.BreakdownManager;
import com.fnaf.nightshift.campaign.CameraManager;
import com.fnaf.nightshift.campaign.CampaignState;
import com.fnaf.nightshift.campaign.MenuActions;
import com.fnaf.nightshift.campaign.NightDefinition;
import com.fnaf.nightshift.campaign.NightManager;
import com.fnaf.nightshift.campaign.NightPhase;
import com.fnaf.nightshift.campaign.OfficeManager;
import com.fnaf.nightshift.campaign.StaminaManager;
import com.fnaf.nightshift.campaign.VentilationSystem;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.entity.AnimatronicEntity;
import com.fnaf.nightshift.entity.AnimatronicKind;
import com.fnaf.nightshift.registry.ModBlocks;
import com.fnaf.nightshift.registry.ModItems;
import com.fnaf.nightshift.repair.RepairStationBlockEntity;
import com.fnaf.nightshift.world.FacilityGenerator;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Pruebas de la actualización 1.1 sobre el mapa generado, con un jugador
 * simulado dentro del mundo: cámaras, casilleros y recursos, ventilación,
 * estamina, averías adicionales, puertas forzadas, entrada de un
 * animatrónico en la oficina, menú y regla de las 6 AM.
 */
public class FnafUpdateGameTests implements FabricGameTest {

	private static void check(boolean c, String m) {
		if (!c) throw new GameTestException(m);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "update", tickLimit = 2400)
	public void officeCamerasVentStaminaAndBreakdowns(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		MinecraftServer server = world.getServer();
		BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN).add(16, 75, 16);
		for (int cx = (origin.getX() >> 4) - 1; cx <= ((origin.getX() + FacilityGenerator.SIZE_X) >> 4) + 1; cx++)
			for (int cz = (origin.getZ() >> 4) - 1; cz <= ((origin.getZ() + FacilityGenerator.SIZE_Z) >> 4) + 1; cz++)
				world.setChunkForced(cx, cz, true);
		FakePlayer fake = FakePlayer.get(world);
		BlockPos[] shelter = new BlockPos[1];
		OfficeManager.OfficeDoor[] west = new OfficeManager.OfficeDoor[1];
		AnimatronicEntity[] bonnie = new AnimatronicEntity[1];

		ctx.runAtTick(2, () -> {
			FacilityGenerator.Result r = FacilityGenerator.generate(world, origin);
			shelter[0] = r.shelter();
			CampaignState s = CampaignState.get(server);
			check(r.shelter().equals(s.getShelterPos()), "refugio no registrado");
			// --- mapa actualizado ---
			check(CameraManager.cameras(world, s).size() >= 12, "cámaras: " + CameraManager.cameras(world, s).size());
			check(s.getLockers().size() >= 10, "casilleros: " + s.getLockers().size());
			check(world.getBlockState(origin.add(47, 1, 44)).isOf(ModBlocks.CAMERA_MONITOR), "falta el monitor de la oficina");
			check(world.getBlockState(origin.add(43, 2, 47)).isOf(ModBlocks.VENT_CONTROL_PANEL), "falta el panel de ventilación");
			check(world.getBlockState(origin.add(42, 3, 50)).isAir(), "el dintel de la puerta oeste debería estar abierto");
			check(OfficeManager.doors(world, s).size() == 4, "puertas de oficina: " + OfficeManager.doors(world, s).size());

			// --- jugador dentro del mundo, con herramientas viejas que deben retirarse ---
			fake.changeGameMode(GameMode.SURVIVAL);
			fake.refreshPositionAndAngles(shelter[0].getX() + 2.5, shelter[0].getY(), shelter[0].getZ() + 0.5, 0, 0);
			world.onPlayerConnected(fake);
			fake.getInventory().insertStack(new ItemStackHelper().wrench());
			check(world.getPlayers().contains(fake), "el jugador simulado no entró al mundo");

			// --- el menú respeta las noches bloqueadas y luego las inicia con la lógica de siempre ---
			s.resetProgress();
			MenuActions.handle(fake, MenuActions.START_NIGHT, 3);
			check(!s.getPhase().isActive(), "la noche 3 bloqueada no debería iniciarse desde el menú");
			s.setCurrentNight(3);
			MenuActions.handle(fake, MenuActions.START_NIGHT, 3);
			check(s.getPhase() == NightPhase.REPAIRING && s.getActiveNight() == 3, "el menú no inició la noche 3");
			check(s.getParticipants().contains(fake.getUuid()), "el jugador no es participante");
			check(fake.getInventory().count(ModItems.FLASHLIGHT) == 1, "debe empezar con la linterna");
			check(fake.getInventory().count(ModItems.WRENCH) == 0, "las herramientas de noches anteriores deben retirarse");
			int stocked = 0;
			for (BlockPos p : s.getLockers()) if (world.getBlockState(p).get(ToolLockerBlock.STOCKED)) stocked++;
			check(stocked == 3, "casilleros abastecidos: " + stocked);
			// sin animatrónicos de la noche durante la prueba (se prueba uno controlado más abajo)
			s.setGraceTicks(0);
			s.setSpawned(true);
		});

		ctx.runAtTick(4, () -> {
			CampaignState s = CampaignState.get(server);
			// --- casillero: entrega una vez, no duplica ---
			BlockPos locker = s.getLockers().stream().filter(p -> world.getBlockState(p).get(ToolLockerBlock.STOCKED)).findFirst().orElseThrow();
			BlockState st = world.getBlockState(locker);
			ToolLockerBlock.Tool tool = st.get(ToolLockerBlock.TOOL);
			BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(locker), Direction.UP, locker, false);
			st.onUse(world, fake, Hand.MAIN_HAND, hit);
			check(fake.getInventory().count(tool.item()) == 1, "el casillero no entregó la herramienta");
			world.getBlockState(locker).onUse(world, fake, Hand.MAIN_HAND, hit);
			check(fake.getInventory().count(tool.item()) == 1, "el casillero duplicó la herramienta");
			// --- ventilación: falla ---
			s.setVentTicks(2);
		});

		ctx.runAtTick(50, () -> {
			CampaignState s = CampaignState.get(server);
			check(s.isVentFailed(), "la ventilación debería haber fallado");
			check(fake.hasStatusEffect(StatusEffects.NAUSEA) && fake.hasStatusEffect(StatusEffects.DARKNESS), "faltan los efectos de la ventilación");
			VentilationSystem.reset(fake, origin.add(43, 2, 47));
			check(!s.isVentFailed() && s.getVentTicks() == NightManager.definition(server).ventIntervalTicks(), "el reinicio no restauró el temporizador");
			check(!fake.hasStatusEffect(StatusEffects.NAUSEA) && !fake.hasStatusEffect(StatusEffects.DARKNESS), "los efectos deben ser reversibles");

			// --- estamina: correr la agota, descansar la recupera ---
			ModConfig c = ModConfig.get();
			StaminaManager.reset(fake.getUuid());
			for (int i = 0; i < 400 && !StaminaManager.isExhausted(fake.getUuid()); i++) {
				fake.setSprinting(true);
				fake.setPosition(fake.getX() + 0.28, fake.getY(), fake.getZ());
				StaminaManager.tickPlayer(fake, s, c, true);
			}
			check(StaminaManager.isExhausted(fake.getUuid()), "correr debería agotar la estamina");
			check(!fake.isSprinting(), "agotado no puede seguir corriendo");
			for (int i = 0; i < 400 && StaminaManager.isExhausted(fake.getUuid()); i++) StaminaManager.tickPlayer(fake, s, c, true);
			check(!StaminaManager.isExhausted(fake.getUuid()), "descansar debería recuperar la estamina");
			fake.setPosition(shelter[0].getX() + 2.5, shelter[0].getY(), shelter[0].getZ() + 0.5);

			// --- avería adicional: estado, ubicación, objetivo obligatorio y consecuencia ---
			int before = s.getRequired().size() - s.getCompleted().size();
			s.setNextExtraTick(0);
			BreakdownManager.tick(world, s, NightManager.definition(server));
			check(s.getExtraCount() == 1 && s.getExtraBreakdowns().size() == 1, "no se produjo la avería adicional");
			BlockPos extra = s.getExtraBreakdowns().keySet().iterator().next();
			check(world.getBlockEntity(extra) instanceof RepairStationBlockEntity be && be.isBroken(), "la avería adicional no tiene estado real");
			check(s.getRequired().size() - s.getCompleted().size() == before + 1, "la avería adicional debe ser un objetivo obligatorio");
			((RepairStationBlockEntity) world.getBlockEntity(extra)).resetRepaired();
			NightManager.onRepairCompleted(world, extra, fake);
			check(s.getExtraBreakdowns().isEmpty(), "reparar debe revertir la consecuencia");

			// --- puerta cerrada forzada a golpes ---
			west[0] = OfficeManager.doors(world, s).stream().filter(d -> d.outward() == Direction.WEST).findFirst().orElseThrow();
			BlockState door = world.getBlockState(west[0].lower());
			((DoorBlock) door.getBlock()).setOpen(null, world, door, west[0].lower(), false);
			NightDefinition def = NightManager.definition(server);
			boolean forced = false;
			for (int i = 0; i < def.doorStrength() + 1 && !forced; i++) forced = OfficeManager.bang(world, s, def, west[0], null, 1);
			check(forced, "la puerta debería ceder tras " + def.doorStrength() + " golpes");
			check(s.isDoorJammed(west[0].lower()), "la puerta forzada debe quedar atascada");
			check(world.getBlockState(west[0].lower()).get(DoorBlock.OPEN), "la puerta forzada debe quedar abierta");

			// --- un animatrónico llega por el pasillo y entra en la oficina ---
			// se usa el Bonnie del plantel de la noche (uno extra sería eliminado por el control anti-acumulación)
			BlockPos corridor = west[0].approach().offset(Direction.WEST, 6);
			bonnie[0] = world.getEntitiesByClass(AnimatronicEntity.class, new net.minecraft.util.math.Box(shelter[0]).expand(150),
					e -> e.belongsTo(s) && e.kind() == AnimatronicKind.BONNIE).stream().findFirst().orElse(null);
			if (bonnie[0] == null) bonnie[0] = NightManager.spawn(world, AnimatronicKind.BONNIE, corridor, s.getSession());
			check(bonnie[0] != null, "no apareció Bonnie");
			bonnie[0].refreshPositionAndAngles(corridor.getX() + 0.5, corridor.getY(), corridor.getZ() + 0.5, 0, 0);
			bonnie[0].getAi().goToOffice(s);
			check(bonnie[0].getAi().getState() == AiState.APPROACH, "Bonnie debería acercarse a la oficina");
		});

		List<AiState> seen = new ArrayList<>();
		for (int t = 60; t < 900; t += 5) {
			ctx.runAtTick(t, () -> {
				if (bonnie[0] != null && bonnie[0].isAlive()) seen.add(bonnie[0].getAi().getState());
				CampaignState dbg = CampaignState.get(server);
				if (bonnie[0] != null && ctx.getTick() % 20 == 0) com.fnaf.nightshift.FnafNightShift.LOGGER.info("[test] t={} bonnie alive={} removed={} state={} pos={} phase={} caught={}",
						ctx.getTick(), bonnie[0].isAlive(), bonnie[0].getRemovalReason(), bonnie[0].getAi().getState(), bonnie[0].getBlockPos(),
						dbg.getPhase(), dbg.isCaught(fake.getUuid()));
			});
		}

		ctx.runAtTick(900, () -> {
			CampaignState s = CampaignState.get(server);
			check(seen.contains(AiState.LURK) || seen.contains(AiState.CHASE), "Bonnie nunca llegó a la puerta: " + seen.subList(0, Math.min(10, seen.size())));
			check(s.isCaught(fake.getUuid()) || s.getPhase() == NightPhase.DEFEAT,
					"Bonnie debería haber entrado en la oficina y atrapado al jugador (estados: " + new java.util.LinkedHashSet<>(seen) + ")");
		});

		ctx.runAtTick(1000, () -> {
			CampaignState s = CampaignState.get(server);
			check(s.getPhase() == NightPhase.DEFEAT, "con todos atrapados la noche se pierde, fase " + s.getPhase());
			check(s.getJammedDoors().isEmpty(), "al terminar la noche se liberan las puertas");

			// --- 6 AM: completar antes no termina la noche; pendientes = derrota; todo hecho = victoria ---
			MenuActions.handle(fake, MenuActions.START_NIGHT, 1);
			s.setGraceTicks(0);
			s.setSpawned(true);
			for (BlockPos p : new ArrayList<>(s.getRequired())) {
				if (world.getBlockEntity(p) instanceof RepairStationBlockEntity be) be.resetRepaired();
				NightManager.onRepairCompleted(world, p, fake);
			}
			check(s.getPhase() == NightPhase.RETURN_TO_SHELTER, "con todo reparado la noche sigue hasta las 6 AM");
			s.setTicksRemaining(3);
		});

		ctx.runAtTick(1010, () -> {
			CampaignState s = CampaignState.get(server);
			check(s.getPhase() == NightPhase.VICTORY, "a las 6 AM con todo hecho y vivo: victoria, fase " + s.getPhase());
			world.removePlayer(fake, Entity.RemovalReason.DISCARDED);
			StaminaManager.remove(fake);
			s.resetProgress();
			ctx.complete();
		});
	}

	/** Pequeño ayudante para crear objetos sin importar ItemStack en las lambdas. */
	private static final class ItemStackHelper {
		net.minecraft.item.ItemStack wrench() {
			return new net.minecraft.item.ItemStack(ModItems.WRENCH);
		}
	}
}
