package com.fnaf.nightshift.dev;

import com.fnaf.nightshift.FnafNightShift;
import com.fnaf.nightshift.campaign.CampaignState;
import com.fnaf.nightshift.campaign.NightManager;
import com.fnaf.nightshift.client.hud.JumpscareOverlay;
import com.fnaf.nightshift.client.screen.WireMinigameScreen;
import com.fnaf.nightshift.entity.AnimatronicEntity;
import com.fnaf.nightshift.entity.AnimatronicKind;
import com.fnaf.nightshift.repair.RepairType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Herramienta de desarrollo (no se distribuye con el mod). Con
 * -Dfnaf.devshots=true recorre escenas predefinidas dentro de un mundo de
 * prueba y guarda capturas del propio framebuffer del juego en run/screenshots.
 */
public class DevScreenshotClient implements ClientModInitializer {
	private record Step(int delay, Runnable action) {
	}

	private final List<Step> steps = new ArrayList<>();
	private int index = -1;
	private int timer;
	private BlockPos origin;
	private final List<AnimatronicEntity> spawned = new ArrayList<>();

	@Override
	public void onInitializeClient() {
		if (System.getProperty("fnaf.materialReview") != null) {
			new MaterialReviewClient().onInitializeClient();
			return;
		}
		if (System.getProperty("fnaf.devshots") == null) return;
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(MinecraftClient client) {
		if (client.world == null || client.player == null || client.getServer() == null) return;
		if (index < 0) {
			if (++timer < 80) return;
			buildSteps(client);
			index = 0;
			timer = 0;
		}
		if (index >= steps.size()) return;
		Step s = steps.get(index);
		if (timer++ >= s.delay) {
			timer = 0;
			index++;
			try {
				s.action.run();
			} catch (Exception e) {
				FnafNightShift.LOGGER.error("devshots step failed", e);
			}
		}
	}

	private void server(MinecraftClient client, java.util.function.Consumer<MinecraftServer> action) {
		MinecraftServer server = client.getServer();
		server.execute(() -> action.accept(server));
	}

	private void cmd(MinecraftClient client, String command) {
		server(client, s -> s.getCommandManager().executeWithPrefix(s.getCommandSource(), command));
	}

	private void tp(MinecraftClient client, BlockPos p, float yaw, float pitch) {
		cmd(client, String.format("tp @p %d %d %d %.1f %.1f", p.getX(), p.getY(), p.getZ(), yaw, pitch));
	}

	private void shot(MinecraftClient client, String name) {
		ScreenshotRecorder.saveScreenshot(client.runDirectory, "fnaf_" + name + ".png", client.getFramebuffer(), t -> {
		});
		FnafNightShift.LOGGER.info("devshots: captured {}", name);
	}

	private void lineup(MinecraftClient client, BlockPos center, boolean frozen) {
		server(client, s -> {
			ServerWorld w = s.getOverworld();
			for (AnimatronicEntity e : spawned) e.discard();
			spawned.clear();
			int i = 0;
			for (AnimatronicKind k : new AnimatronicKind[]{AnimatronicKind.BONNIE, AnimatronicKind.CHICA, AnimatronicKind.FREDDY,
					AnimatronicKind.FOXY, AnimatronicKind.GOLDEN_FREDDY}) {
				AnimatronicEntity e = NightManager.spawn(w, k, center.add(-6 + i * 3, 0, 0), -1);
				if (e != null) {
					e.refreshPositionAndAngles(e.getX(), e.getY(), e.getZ(), 0f, 0f);
					e.setHeadYaw(0f);
					e.setBodyYaw(0f);
					e.setAiDisabled(frozen);
					spawned.add(e);
				}
				i++;
			}
		});
	}

	private void buildSteps(MinecraftClient client) {
		MinecraftServer server = client.getServer();
		BlockPos shelter = CampaignState.get(server).getShelterPos();
		origin = shelter == null ? client.player.getBlockPos() : shelter.add(-50, -1, -50);
		client.options.hudHidden = false;
		cmd(client, "gamemode creative @p");
		cmd(client, "gamerule doDaylightCycle false");
		cmd(client, "gamerule doMobSpawning false");
		cmd(client, "gamerule sendCommandFeedback false");
		cmd(client, "time set noon");

		// 1-2) animatrónicos a plena luz, fuera del complejo
		BlockPos out = new BlockPos(origin.getX() + 40, 0, origin.getZ() - 30);
		BlockPos ground = server.getOverworld().getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, out);
		steps.add(new Step(5, () -> {
			lineup(client, ground, true);
			tp(client, ground.add(0, 1, 9), 180f, 8f);
		}));
		steps.add(new Step(70, () -> shot(client, "01_lineup_front")));
		steps.add(new Step(5, () -> tp(client, ground.add(9, 1, 7), 130f, 10f)));
		steps.add(new Step(50, () -> shot(client, "02_lineup_angle")));
		steps.add(new Step(5, () -> tp(client, ground.add(-1, 2, 3), 180f, 15f)));
		steps.add(new Step(40, () -> shot(client, "03_closeup_freddy")));
		// 3) en movimiento (IA activa)
		steps.add(new Step(5, () -> server(client, s -> spawned.forEach(e -> e.setAiDisabled(false)))));
		steps.add(new Step(5, () -> tp(client, ground.add(0, 3, 12), 180f, 15f)));
		steps.add(new Step(70, () -> shot(client, "04_walking")));

		// 4) interior del complejo de noche
		steps.add(new Step(5, () -> {
			server(client, s -> spawned.forEach(net.minecraft.entity.Entity::discard));
			cmd(client, "time set midnight");
			tp(client, origin.add(18, 1, 33), 180f, 5f);
		}));
		steps.add(new Step(80, () -> shot(client, "05_dining_stage")));
		steps.add(new Step(5, () -> tp(client, origin.add(46, 1, 52), -90f, 10f)));
		steps.add(new Step(60, () -> shot(client, "06_shelter")));
		steps.add(new Step(5, () -> tp(client, origin.add(68, 1, 30), -150f, 0f)));
		steps.add(new Step(60, () -> shot(client, "07_power_plant")));
		steps.add(new Step(5, () -> tp(client, origin.add(50, 1, 37), 180f, 0f)));
		steps.add(new Step(60, () -> shot(client, "08_corridor")));
		steps.add(new Step(5, () -> tp(client, origin.add(11, -6, 52), 180f, 0f)));
		steps.add(new Step(60, () -> shot(client, "09_tunnel")));

		// 5) noche en curso: interfaz y animatrónicos en la pizzería
		steps.add(new Step(5, () -> {
			cmd(client, "gamemode survival @p");
			cmd(client, "fnaf night start 3");
		}));
		steps.add(new Step(40, () -> tp(client, origin.add(18, 1, 33), 180f, 5f)));
		steps.add(new Step(10, () -> lineup(client, origin.add(18, 1, 26), true)));
		steps.add(new Step(80, () -> shot(client, "10_night_hud")));
		steps.add(new Step(5, () -> client.setScreen(new WireMinigameScreen(client.player.getBlockPos(), RepairType.WIRING, 1234L, 2))));
		steps.add(new Step(20, () -> shot(client, "11_minigame_wires")));
		steps.add(new Step(5, () -> {
			client.setScreen(null);
			JumpscareOverlay.start(AnimatronicKind.FREDDY);
		}));
		steps.add(new Step(9, () -> shot(client, "12_jumpscare")));
		steps.add(new Step(5, () -> JumpscareOverlay.start(AnimatronicKind.BONNIE)));
		steps.add(new Step(12, () -> shot(client, "13_jumpscare_bonnie")));
		// ---- 1.1: menú, cámaras, oficina, ventilación, estamina, casilleros ----
		steps.add(new Step(10, () -> {
			client.setScreen(null);
			cmd(client, "fnaf night start 2");
		}));
		steps.add(new Step(30, () -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				com.fnaf.nightshift.network.ModPackets.MENU_REQUEST, net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create())));
		steps.add(new Step(20, () -> shot(client, "14_menu")));
		// animatrónico frente a la CAM 1A (comedor/escenario) y vista desde el monitor de la oficina
		steps.add(new Step(5, () -> {
			client.setScreen(null);
			tp(client, origin.add(47, 1, 45), 180f, 20f);
			server(client, s -> {
				ServerWorld w = s.getOverworld();
				for (AnimatronicEntity e : spawned) e.discard();
				spawned.clear();
				CampaignState st = CampaignState.get(s);
				st.setGraceTicks(0);
				st.setSpawned(true);
				AnimatronicEntity b = NightManager.spawn(w, AnimatronicKind.BONNIE, origin.add(18, 1, 27), -1);
				AnimatronicEntity c = NightManager.spawn(w, AnimatronicKind.CHICA, origin.add(14, 1, 25), -1);
				for (AnimatronicEntity e : new AnimatronicEntity[]{b, c}) {
					if (e == null) continue;
					e.refreshPositionAndAngles(e.getX(), e.getY(), e.getZ(), 0f, 0f);
					e.setBodyYaw(0f);
					e.setHeadYaw(0f);
					e.setAiDisabled(true);
					spawned.add(e);
				}
			});
		}));
		steps.add(new Step(40, () -> server(client, s -> {
			ServerPlayerEntity p = s.getPlayerManager().getPlayerList().get(0);
			com.fnaf.nightshift.campaign.CameraManager.open(p, origin.add(47, 1, 44));
		})));
		steps.add(new Step(60, () -> shot(client, "15_camera_stage")));
		steps.add(new Step(5, () -> {
			if (client.currentScreen != null) client.currentScreen.keyPressed(262, 0, 0);
			if (client.currentScreen != null) client.currentScreen.keyPressed(262, 0, 0);
		}));
		steps.add(new Step(50, () -> shot(client, "16_camera_cove")));
		// cámara averiada: sin señal
		steps.add(new Step(5, () -> server(client, s -> {
			CampaignState st = CampaignState.get(s);
			for (BlockPos p : st.getStations()) {
				if (s.getOverworld().getBlockEntity(p) instanceof com.fnaf.nightshift.repair.RepairStationBlockEntity be
						&& be.getRepairType() == RepairType.CAMERA && be.getCamName().startsWith("CAM 1C")) be.breakStation(1L, 1);
			}
		})));
		steps.add(new Step(50, () -> shot(client, "17_camera_no_signal")));
		// oficina: animatrónico acechando en la puerta oeste, HUD de ventilación y estamina
		steps.add(new Step(5, () -> {
			client.setScreen(null);
			tp(client, origin.add(46, 1, 50), 90f, 5f);
			server(client, s -> {
				ServerWorld w = s.getOverworld();
				for (AnimatronicEntity e : spawned) e.discard();
				spawned.clear();
				CampaignState st = CampaignState.get(s);
				com.fnaf.nightshift.campaign.OfficeManager.OfficeDoor west = com.fnaf.nightshift.campaign.OfficeManager.doors(w, st).stream()
						.filter(d -> d.outward() == net.minecraft.util.math.Direction.WEST).findFirst().orElse(null);
				if (west == null) return;
				net.minecraft.block.BlockState door = w.getBlockState(west.lower());
				((net.minecraft.block.DoorBlock) door.getBlock()).setOpen(null, w, door, west.lower(), false);
				// sesión -1: entidad de prueba (la noche 2 no incluye a Freddy y el control anti-acumulación la retiraría)
				AnimatronicEntity b = NightManager.spawn(w, AnimatronicKind.FREDDY, west.approach().west(), -1);
				if (b != null) {
					b.getAi().startLurk(west);
					spawned.add(b);
				}
				st.setVentTicks(25 * 20);
			});
		}));
		steps.add(new Step(45, () -> {
			com.fnaf.nightshift.client.ClientNightState.stamina = 0.35f;
			shot(client, "18_office_lurk_hud");
		}));
		// se abre la puerta: entra
		steps.add(new Step(5, () -> server(client, s -> {
			ServerWorld w = s.getOverworld();
			CampaignState st = CampaignState.get(s);
			com.fnaf.nightshift.campaign.OfficeManager.doors(w, st).stream()
					.filter(d -> d.outward() == net.minecraft.util.math.Direction.WEST).findFirst().ifPresent(west -> {
						net.minecraft.block.BlockState door = w.getBlockState(west.lower());
						((net.minecraft.block.DoorBlock) door.getBlock()).setOpen(null, w, door, west.lower(), true);
					});
		})));
		steps.add(new Step(30, () -> shot(client, "18b_office_entry")));
		// fallo de ventilación: visión borrosa + náuseas + oscuridad (reversibles)
		steps.add(new Step(5, () -> server(client, s -> {
			for (AnimatronicEntity e : spawned) e.discard();
			spawned.clear();
			CampaignState.get(s).setVentTicks(1);
		})));
		steps.add(new Step(70, () -> shot(client, "19_vent_failure")));
		steps.add(new Step(5, () -> server(client, s -> {
			ServerPlayerEntity p = s.getPlayerManager().getPlayerList().get(0);
			com.fnaf.nightshift.campaign.VentilationSystem.reset(p, origin.add(43, 2, 47));
		})));
		// casilleros del depósito de herramientas
		steps.add(new Step(30, () -> tp(client, origin.add(77, 1, 43), 0f, 15f)));
		steps.add(new Step(50, () -> shot(client, "20_tool_lockers")));
		steps.add(new Step(5, () -> {
			com.fnaf.nightshift.client.ClientNightState.exhausted = true;
			com.fnaf.nightshift.client.ClientNightState.stamina = 0.05f;
		}));
		steps.add(new Step(5, () -> shot(client, "21_exhausted")));
		steps.add(new Step(5, () -> {
			JumpscareOverlay.start(AnimatronicKind.FOXY);
		}));
		steps.add(new Step(10, () -> shot(client, "22_jumpscare_foxy")));
		steps.add(new Step(40, () -> server(client, s -> {
			spawned.forEach(net.minecraft.entity.Entity::discard);
			NightManager.stopNight(s);
		})));
		steps.add(new Step(20, client::scheduleStop));
	}
}
