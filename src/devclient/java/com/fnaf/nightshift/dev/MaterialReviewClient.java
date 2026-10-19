package com.fnaf.nightshift.dev;

import com.fnaf.nightshift.FnafNightShift;
import com.fnaf.nightshift.campaign.NightManager;
import com.fnaf.nightshift.entity.AnimatronicEntity;
import com.fnaf.nightshift.entity.AnimatronicKind;
import com.fnaf.nightshift.item.FlashlightItem;
import com.fnaf.nightshift.registry.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Visual smoke test, only registered in the development source set. */
public final class MaterialReviewClient implements ClientModInitializer {
	private record Step(int delay, Runnable action) {}
	private final List<Step> steps = new ArrayList<>();
	private final List<AnimatronicEntity> actors = new ArrayList<>();
	private int timer;
	private int index = -1;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.world == null || client.player == null || client.getServer() == null) return;
			if (index < 0) {
				if (++timer < 80) return;
				setup(client);
				index = 0;
				timer = 0;
			}
			if (index < steps.size() && ++timer >= steps.get(index).delay()) {
				timer = 0;
				steps.get(index++).action().run();
			}
		});
	}

	private void command(MinecraftClient client, String command) {
		MinecraftServer server = client.getServer();
		server.execute(() -> server.getCommandManager().executeWithPrefix(server.getCommandSource(), command));
	}

	private void lights(MinecraftClient client, int level) {
		for (int x = 3; x <= 27; x += 4) {
			for (int z = 3; z <= 19; z += 4) {
				command(client, "setblock " + x + " 204 " + z + " minecraft:light[level=" + level + "]");
			}
		}
	}

	private void flashlight(MinecraftClient client, boolean on) {
		client.getServer().execute(() -> {
			var player = client.getServer().getPlayerManager().getPlayerList().get(0);
			ItemStack stack = new ItemStack(ModItems.FLASHLIGHT);
			FlashlightItem.setOn(stack, on);
			FlashlightItem.setBattery(stack, 6000);
			player.getInventory().setStack(player.getInventory().selectedSlot, stack);
		});
	}

	private void shot(MinecraftClient client, String name) {
		ScreenshotRecorder.saveScreenshot(client.runDirectory, "materials_" + name + ".png", client.getFramebuffer(), text -> {});
		FnafNightShift.LOGGER.info("material-review captured {}", name);
	}

	private void setup(MinecraftClient client) {
		client.options.pauseOnLostFocus = false;
		client.options.hudHidden = true;
		command(client, "gamemode creative @p");
		command(client, "gamerule doMobSpawning false");
		command(client, "gamerule doDaylightCycle false");
		command(client, "time set midnight");
		client.getServer().execute(() -> NightManager.stopNight(client.getServer()));
		client.getServer().execute(() -> {
			for (var entity : client.getServer().getOverworld().iterateEntities()) {
				if (entity instanceof AnimatronicEntity) entity.discard();
			}
		});
		command(client, "tp @p 15 200 21 180 0");
		steps.add(new Step(80, () -> {
			command(client, "fill 0 198 0 30 210 24 fnaf_night_shift:industrial_concrete hollow");
			command(client, "fill 1 199 1 29 199 23 fnaf_night_shift:checkered_floor");
			command(client, "fill 1 200 0 29 205 0 fnaf_night_shift:pizzeria_wall");
			command(client, "fill 0 200 1 0 205 23 fnaf_night_shift:industrial_wall_stripe");
			command(client, "fill 30 200 1 30 205 23 fnaf_night_shift:rusty_metal");
			String[] stations = {"generator", "fuse_box", "wiring", "electrical_panel", "lighting", "auto_door", "ventilation", "terminal", "security"};
			for (int i = 0; i < stations.length; i++) {
				command(client, "setblock " + (3 + i * 3) + " 200 2 fnaf_night_shift:station_" + stations[i] + "[facing=south,broken=false]");
			}
			command(client, "setblock 4 200 15 fnaf_night_shift:party_table");
			command(client, "setblock 26 200 15 fnaf_night_shift:supply_crate");
			// Teleport after the floor exists; otherwise the player falls before capture.
			command(client, "tp @p 15 200 21 180 0");
			flashlight(client, false);
			lights(client, 15);
			client.getServer().execute(() -> {
				int i = 0;
				for (AnimatronicKind kind : AnimatronicKind.values()) {
					AnimatronicEntity entity = NightManager.spawn(client.getServer().getOverworld(), kind, new BlockPos(5 + i++ * 5, 200, 9), -1);
					if (entity != null) {
						entity.setAiDisabled(true);
						entity.setYaw(0);
						entity.setHeadYaw(0);
						entity.setBodyYaw(0);
						actors.add(entity);
					}
				}
			});
		}));
		steps.add(new Step(100, () -> shot(client, "01_clear")));
		steps.add(new Step(5, () -> command(client, "tp @p 20 200 13 180 0")));
		steps.add(new Step(60, () -> shot(client, "02_closeup")));
		steps.add(new Step(5, () -> {
			command(client, "tp @p 15 200 21 180 0");
			lights(client, 5);
		}));
		steps.add(new Step(80, () -> shot(client, "03_dim")));
		steps.add(new Step(5, () -> {
			lights(client, 0);
			command(client, "setblock 14 202 1 fnaf_night_shift:emergency_light[facing=south]");
		}));
		steps.add(new Step(80, () -> shot(client, "04_emergency")));
		steps.add(new Step(5, () -> {
			command(client, "setblock 14 202 1 air");
			flashlight(client, false);
		}));
		steps.add(new Step(80, () -> shot(client, "05_flashlight_off")));
		steps.add(new Step(5, () -> flashlight(client, true)));
		steps.add(new Step(80, () -> shot(client, "06_flashlight_on")));
		steps.add(new Step(5, () -> {
			lights(client, 15);
			command(client, "tp @p 15 200 6 180 25");
		}));
		steps.add(new Step(80, () -> shot(client, "07_materials_close")));
		steps.add(new Step(5, () -> {
			command(client, "tp @p 15 200 21 180 0");
			client.getServer().execute(() -> actors.forEach(e -> e.setAiDisabled(false)));
		}));
		steps.add(new Step(70, () -> shot(client, "08_animation")));
		steps.add(new Step(30, client::scheduleStop));
	}
}
