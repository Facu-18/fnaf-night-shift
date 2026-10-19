package com.fnaf.nightshift;

import com.fnaf.nightshift.campaign.NightManager;
import com.fnaf.nightshift.command.FnafCommand;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.item.FlashlightLightManager;
import com.fnaf.nightshift.network.ModPackets;
import com.fnaf.nightshift.registry.ModBlockEntities;
import com.fnaf.nightshift.registry.ModBlocks;
import com.fnaf.nightshift.registry.ModEntities;
import com.fnaf.nightshift.registry.ModItemGroups;
import com.fnaf.nightshift.registry.ModItems;
import com.fnaf.nightshift.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Punto de entrada común (cliente y servidor) de FNAF: NIGHT SHIFT.
 */
public class FnafNightShift implements ModInitializer {
	public static final String MOD_ID = "fnaf_night_shift";
	public static final Logger LOGGER = LoggerFactory.getLogger("FNAF Night Shift");

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModConfig.load();

		ModSounds.register();
		ModBlocks.register();
		ModItems.register();
		ModBlockEntities.register();
		ModEntities.register();
		ModItemGroups.register();
		ModPackets.registerServerReceivers();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> FnafCommand.register(dispatcher));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			NightManager.tick(server);
			FlashlightLightManager.tick(server);
			com.fnaf.nightshift.campaign.StaminaManager.tick(server);
			com.fnaf.nightshift.campaign.CameraManager.tick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> FlashlightLightManager.clearAll(server));
		// Cachés en memoria de la oficina y la atmósfera: se vacían al cambiar de mundo.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			com.fnaf.nightshift.campaign.OfficeManager.reset();
			com.fnaf.nightshift.campaign.AtmosphereManager.invalidate();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> NightManager.onPlayerJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			FlashlightLightManager.remove(handler.player);
			com.fnaf.nightshift.campaign.StaminaManager.remove(handler.player);
			com.fnaf.nightshift.campaign.CameraManager.remove(handler.player.getUuid());
		});

		LOGGER.info("FNAF: NIGHT SHIFT inicializado. Que empiece el turno nocturno.");
	}
}
