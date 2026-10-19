package com.fnaf.nightshift.test;

import com.fnaf.nightshift.ai.AiState;
import com.fnaf.nightshift.campaign.CampaignState;
import com.fnaf.nightshift.campaign.Difficulty;
import com.fnaf.nightshift.campaign.NightDefinition;
import com.fnaf.nightshift.campaign.NightManager;
import com.fnaf.nightshift.campaign.NightPhase;
import com.fnaf.nightshift.entity.AnimatronicEntity;
import com.fnaf.nightshift.entity.AnimatronicKind;
import com.fnaf.nightshift.registry.ModItems;
import com.fnaf.nightshift.repair.MinigameLogic;
import com.fnaf.nightshift.repair.MinigameType;
import com.fnaf.nightshift.repair.RepairStationBlockEntity;
import com.fnaf.nightshift.repair.RepairType;
import com.fnaf.nightshift.world.FacilityGenerator;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Pruebas automáticas ejecutadas en un servidor real sin interfaz
 * (./gradlew runGametest). Cubren la lógica de minijuegos, la IA de los
 * animatrónicos y el ciclo completo de campaña sobre el mapa generado.
 */
public class FnafGameTests implements FabricGameTest {

	private static void check(boolean condition, String message) {
		if (!condition) throw new GameTestException(message);
	}

	// ------------------------------------------------------------------
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "logic")
	public void minigameLogicValidatesAnswers(TestContext ctx) {
		for (long seed = 0; seed < 300; seed++) {
			int level = 1 + (int) (seed % 7);
			int[] perm = MinigameLogic.wires(seed);
			int[] answer = new int[MinigameLogic.WIRE_COUNT];
			for (int j = 0; j < perm.length; j++) answer[perm[j]] = j;
			check(MinigameLogic.validate(MinigameType.WIRES, seed, level, answer), "cables correctos rechazados");
			int[] wrong = answer.clone();
			int t = wrong[0];
			wrong[0] = wrong[1];
			wrong[1] = t;
			check(!MinigameLogic.validate(MinigameType.WIRES, seed, level, wrong), "cables incorrectos aceptados");

			int[] seq = MinigameLogic.sequence(seed, level);
			check(MinigameLogic.validate(MinigameType.SEQUENCE, seed, level, seq), "secuencia correcta rechazada");
			int[] badSeq = seq.clone();
			badSeq[0] = (badSeq[0] + 1) % MinigameLogic.SEQUENCE_BUTTONS;
			check(!MinigameLogic.validate(MinigameType.SEQUENCE, seed, level, badSeq), "secuencia incorrecta aceptada");

			int[] code = MinigameLogic.code(seed);
			check(MinigameLogic.validate(MinigameType.CODE, seed, level, code), "código correcto rechazado");
			int[] badCode = code.clone();
			badCode[3] = (badCode[3] + 1) % 10;
			check(!MinigameLogic.validate(MinigameType.CODE, seed, level, badCode), "código incorrecto aceptado");

			boolean[] burnt = MinigameLogic.burntFuses(seed, level);
			List<Integer> idx = new ArrayList<>();
			for (int i = 0; i < burnt.length; i++) if (burnt[i]) idx.add(i);
			int[] fuses = idx.stream().mapToInt(Integer::intValue).toArray();
			check(MinigameLogic.validate(MinigameType.REPLACE_PART, seed, level, fuses), "fusibles correctos rechazados");
			int healthy = -1;
			for (int i = 0; i < burnt.length; i++) if (!burnt[i]) healthy = i;
			int[] extra = new int[fuses.length + 1];
			System.arraycopy(fuses, 0, extra, 0, fuses.length);
			extra[fuses.length] = healthy;
			check(!MinigameLogic.validate(MinigameType.REPLACE_PART, seed, level, extra), "cambiar un fusible sano debería fallar");
			check(MinigameLogic.burntCount(seed, level) == fuses.length, "recuento de fusibles inconsistente");
		}
		ctx.complete();
	}

	// ------------------------------------------------------------------
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "logic")
	public void nightDefinitionsProgress(TestContext ctx) {
		int lastRepairs = 0;
		for (int n = 1; n <= 7; n++) {
			NightDefinition d = NightDefinition.of(n, Difficulty.NORMAL);
			check(d.night() == n, "número de noche");
			check(d.repairs() >= lastRepairs || n == 7, "las reparaciones no deben disminuir (noche " + n + ")");
			lastRepairs = d.repairs();
			check(d.durationTicks() > 0, "duración positiva");
		}
		check(NightDefinition.of(1, Difficulty.NORMAL).roster().equals(List.of(AnimatronicKind.BONNIE)), "noche 1: sólo Bonnie");
		check(NightDefinition.of(2, Difficulty.NORMAL).roster().contains(AnimatronicKind.CHICA), "noche 2 incorpora a Chica");
		check(NightDefinition.of(3, Difficulty.NORMAL).roster().contains(AnimatronicKind.FOXY), "noche 3 incorpora a Foxy");
		check(NightDefinition.of(4, Difficulty.NORMAL).roster().size() == 4, "noche 4: los cuatro");
		check(NightDefinition.of(7, Difficulty.NORMAL).roster().size() >= 4, "noche 7: todos más extras");
		check(NightDefinition.of(6, Difficulty.PESADILLA).aggression() > NightDefinition.of(6, Difficulty.FACIL).aggression(), "la dificultad escala");
		check(NightDefinition.of(5, Difficulty.NORMAL).aggression() > NightDefinition.of(4, Difficulty.NORMAL).aggression(), "la noche 5 es más agresiva");
		ctx.complete();
	}

	// ------------------------------------------------------------------
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "entities", tickLimit = 500)
	public void animatronicsSpawnAndThink(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos base = ctx.getAbsolutePos(new BlockPos(0, 0, 0)).add(0, 40, 0);
		for (int x = -12; x <= 12; x++)
			for (int z = -12; z <= 12; z++)
				world.setBlockState(base.add(x, -1, z), Blocks.STONE.getDefaultState());
		List<AnimatronicEntity> list = new ArrayList<>();
		int i = 0;
		for (AnimatronicKind kind : AnimatronicKind.values()) {
			if (kind == AnimatronicKind.ANY) continue;
			AnimatronicEntity e = NightManager.spawn(world, kind, base.add(-8 + i * 4, 0, 0), -1);
			check(e != null, "no se pudo crear " + kind);
			list.add(e);
			i++;
		}
		Set<AiState> seen = EnumSet.noneOf(AiState.class);
		for (int t = 10; t < 400; t += 10) {
			ctx.runAtTick(t, () -> {
				for (AnimatronicEntity e : list) seen.add(e.getAi().getState());
			});
		}
		ctx.runAtTick(400, () -> {
			for (AnimatronicEntity e : list) {
				check(e.isAlive() && !e.isRemoved(), e.kind() + " desapareció");
				check(e.getY() > base.getY() - 2, e.kind() + " cayó del suelo");
			}
			check(seen.contains(AiState.PATROL) || seen.contains(AiState.IDLE), "la IA nunca salió de un estado válido");
			list.forEach(Entity::discard);
			ctx.complete();
		});
	}

	// ------------------------------------------------------------------
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "campaign", tickLimit = 1200)
	public void fullCampaignFlowOnGeneratedMap(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		MinecraftServer server = world.getServer();
		BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN).add(16, 30, 16);
		for (int cx = (origin.getX() >> 4) - 1; cx <= ((origin.getX() + FacilityGenerator.SIZE_X) >> 4) + 1; cx++)
			for (int cz = (origin.getZ() >> 4) - 1; cz <= ((origin.getZ() + FacilityGenerator.SIZE_Z) >> 4) + 1; cz++)
				world.setChunkForced(cx, cz, true);

		FakePlayer fake = FakePlayer.get(world);
		BlockPos[] generator = new BlockPos[1];

		ctx.runAtTick(2, () -> {
			FacilityGenerator.Report report = FacilityGenerator.check(world, origin);
			check(report.ok(), "el volumen debería estar libre, bloqueos: " + report.blocking());
			FacilityGenerator.Result result = FacilityGenerator.generate(world, origin);
			check(result.stations() == 26, "se esperaban 26 instalaciones, hay " + result.stations());
			CampaignState st = CampaignState.get(server);
			check(result.shelter().equals(st.getShelterPos()), "el refugio no se registró");
			check(st.getStations().size() >= 26, "instalaciones registradas: " + st.getStations().size());
			check(st.nodeCount() >= 30, "nodos registrados: " + st.nodeCount());
			check(st.getSpawns().size() >= 7, "marcadores registrados: " + st.getSpawns().size());
			check(!FacilityGenerator.check(world, origin).ok(), "no debe permitir generar encima de construcciones");

			check(NightManager.startNight(server, 1, null), "la noche 1 no arrancó");
			check(st.getPhase() == NightPhase.REPAIRING, "fase inicial incorrecta");
			check(st.getRequired().size() == NightDefinition.of(1, st.getDifficulty()).repairs(), "reparaciones requeridas: " + st.getRequired().size());
			for (BlockPos p : st.getRequired()) {
				check(world.getBlockEntity(p) instanceof RepairStationBlockEntity be && be.isBroken(), "instalación requerida no averiada");
			}
			st.setGraceTicks(1);
		});

		ctx.runAtTick(15, () -> {
			CampaignState st = CampaignState.get(server);
			List<AnimatronicEntity> roster = world.getEntitiesByClass(AnimatronicEntity.class,
					new net.minecraft.util.math.Box(origin).expand(200), e -> e.belongsTo(st));
			check(roster.size() == 1 && roster.get(0).kind() == AnimatronicKind.BONNIE, "noche 1 debe tener sólo a Bonnie, hay " + roster.size());

			// reparación de mantener pulsado sobre un generador que no es objetivo
			for (BlockPos p : st.getStations()) {
				if (!st.getRequired().contains(p) && world.getBlockEntity(p) instanceof RepairStationBlockEntity be
						&& be.getRepairType() == RepairType.GENERATOR) {
					generator[0] = p;
					be.breakStation(42L, 1);
					break;
				}
			}
			check(generator[0] != null, "no se encontró un generador libre");
			fake.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
		});

		for (int t = 20; t < 400; t += 4) {
			ctx.runAtTick(t, () -> {
				if (world.getBlockEntity(generator[0]) instanceof RepairStationBlockEntity be && be.isBroken()) be.interact(fake);
			});
		}

		ctx.runAtTick(420, () -> {
			CampaignState st = CampaignState.get(server);
			check(world.getBlockEntity(generator[0]) instanceof RepairStationBlockEntity be && !be.isBroken(), "el generador no se reparó manteniendo pulsado");
			check(st.getCompleted().isEmpty(), "una instalación no requerida no debe contar como objetivo");

			for (BlockPos p : new ArrayList<>(st.getRequired())) {
				if (world.getBlockEntity(p) instanceof RepairStationBlockEntity be) be.resetRepaired();
				NightManager.onRepairCompleted(world, p, fake);
				NightManager.onRepairCompleted(world, p, fake); // duplicado: no debe contar dos veces
			}
			check(st.getCompleted().size() == st.getRequired().size(), "objetivos completados: " + st.getCompleted().size());
			check(st.getPhase() == NightPhase.RETURN_TO_SHELTER, "tras reparar todo hay que volver al refugio");
			st.setTicksRemaining(3);
		});

		ctx.runAtTick(430, () -> {
			CampaignState st = CampaignState.get(server);
			check(st.getPhase() == NightPhase.VICTORY, "debería ser victoria, fase " + st.getPhase());
			check(st.getCurrentNight() == 2 && st.isNightCompleted(1), "la noche 2 debería estar desbloqueada");
			check(world.getEntitiesByClass(AnimatronicEntity.class, new net.minecraft.util.math.Box(origin).expand(200), e -> e.getSession() >= 0).isEmpty(),
					"los animatrónicos de la noche deben retirarse");

			check(NightManager.startNight(server, 2, null), "la noche 2 no arrancó");
			st.setGraceTicks(1);
		});

		ctx.runAtTick(445, () -> {
			CampaignState st = CampaignState.get(server);
			long count = world.getEntitiesByClass(AnimatronicEntity.class, new net.minecraft.util.math.Box(origin).expand(200), e -> e.belongsTo(st)).size();
			check(count == 2, "noche 2: Bonnie y Chica, hay " + count);
			st.setTicksRemaining(3);
		});

		ctx.runAtTick(455, () -> {
			CampaignState st = CampaignState.get(server);
			check(st.getPhase() == NightPhase.DEFEAT, "sin reparaciones a las 6 AM debería ser derrota, fase " + st.getPhase());
			NightManager.restartNight(server, null);
			check(st.getPhase() == NightPhase.REPAIRING && st.getActiveNight() == 2, "reiniciar debe repetir la noche 2");
			NightManager.stopNight(server);
			check(st.getPhase() == NightPhase.NONE, "detener la noche");
			st.resetProgress();
			check(st.getCurrentNight() == 1, "restablecer progreso");
			ctx.complete();
		});
	}
}
