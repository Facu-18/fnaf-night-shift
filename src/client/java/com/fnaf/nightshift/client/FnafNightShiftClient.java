package com.fnaf.nightshift.client;

import com.fnaf.nightshift.FnafNightShift;
import com.fnaf.nightshift.client.hud.JumpscareOverlay;
import com.fnaf.nightshift.client.hud.NightHud;
import com.fnaf.nightshift.client.render.AnimatronicRenderer;
import com.fnaf.nightshift.client.screen.CodeMinigameScreen;
import com.fnaf.nightshift.client.screen.NightResultScreen;
import com.fnaf.nightshift.client.screen.ReplacePartScreen;
import com.fnaf.nightshift.client.screen.SequenceMinigameScreen;
import com.fnaf.nightshift.client.screen.WireMinigameScreen;
import com.fnaf.nightshift.entity.AnimatronicKind;
import com.fnaf.nightshift.item.FlashlightItem;
import com.fnaf.nightshift.network.ModPackets;
import com.fnaf.nightshift.registry.ModBlocks;
import com.fnaf.nightshift.registry.ModEntities;
import com.fnaf.nightshift.registry.ModItems;
import com.fnaf.nightshift.registry.ModSounds;
import com.fnaf.nightshift.repair.RepairType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.math.BlockPos;

/**
 * Punto de entrada del cliente: renderizado, interfaz, minijuegos y
 * recepción de paquetes. Nada de esto se carga en un servidor dedicado.
 */
public class FnafNightShiftClient implements ClientModInitializer {
	private static net.minecraft.client.option.KeyBinding menuKey;

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.BONNIE, ctx -> new AnimatronicRenderer<>(ctx, "bonnie", 1.0f));
		EntityRendererRegistry.register(ModEntities.CHICA, ctx -> new AnimatronicRenderer<>(ctx, "chica", 1.0f));
		EntityRendererRegistry.register(ModEntities.FOXY, ctx -> new AnimatronicRenderer<>(ctx, "foxy", 1.0f));
		EntityRendererRegistry.register(ModEntities.FREDDY, ctx -> new AnimatronicRenderer<>(ctx, "freddy", 1.0f));
		EntityRendererRegistry.register(ModEntities.GOLDEN_FREDDY, ctx -> new AnimatronicRenderer<>(ctx, "golden_freddy", 1.0f));

		BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), ModBlocks.CAMERA_MONITOR, ModBlocks.VENT_CONTROL_PANEL,
				ModBlocks.STATIONS.get(RepairType.CAMERA), ModBlocks.SECURITY_DOOR, ModBlocks.VENT_GRATE,
				ModBlocks.METAL_GRATE, ModBlocks.FLICKERING_LIGHT, ModBlocks.EMERGENCY_LIGHT, ModBlocks.PATROL_NODE,
				ModBlocks.SPAWN_MARKER, ModBlocks.PARTY_TABLE, ModBlocks.SIGN_EXIT, ModBlocks.SIGN_DANGER, ModBlocks.SIGN_PIZZERIA,
				ModBlocks.SIGN_STAFF);

		ModelPredicateProviderRegistry.register(ModItems.FLASHLIGHT, FnafNightShift.id("on"),
				(stack, world, entity, seed) -> FlashlightItem.isOn(stack) ? 1.0f : 0.0f);

		HudRenderCallback.EVENT.register((context, tickDelta) -> {
			// El texto se dibuja por lotes al final: durante el susto no se dibuja la interfaz.
			if (JumpscareOverlay.isActive()) {
				JumpscareOverlay.render(context, tickDelta);
			} else {
				com.fnaf.nightshift.client.hud.TensionEffects.render(context);
				NightHud.render(context, tickDelta);
			}
		});

		// Tecla del menú de noches (N por defecto, configurable en Controles)
		menuKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(new net.minecraft.client.option.KeyBinding(
				"key.fnaf_night_shift.menu", net.minecraft.client.util.InputUtil.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_N,
				"category.fnaf_night_shift"));

		// Sonidos de FNAF proporcionados para el proyecto: paquete integrado y desactivable que
		// sustituye a los sonidos sintetizados (que quedan como respaldo).
		net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(FnafNightShift.MOD_ID).ifPresent(container ->
				net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(FnafNightShift.id("fnaf_sounds"), container,
						net.minecraft.text.Text.translatable("pack.fnaf_night_shift.fnaf_sounds"),
						net.fabricmc.fabric.api.resource.ResourcePackActivationType.DEFAULT_ENABLED));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			JumpscareOverlay.tick(client);
			com.fnaf.nightshift.client.hud.TensionEffects.tick(client);
			while (menuKey.wasPressed()) {
				if (client.player != null && ClientPlayNetworking.canSend(ModPackets.MENU_REQUEST)) {
					ClientPlayNetworking.send(ModPackets.MENU_REQUEST, net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create());
				}
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientNightState.reset();
			com.fnaf.nightshift.client.hud.HorrorFilter.close();
		});
		// Filtro de terror al terminar de dibujar el mundo (antes de la mano y la interfaz)
		net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(ctx ->
				com.fnaf.nightshift.client.hud.HorrorFilter.render(ctx.tickDelta()));

		registerReceivers();
	}

	private static void registerReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.NIGHT_STATE, (client, handler, buf, sender) -> {
			ClientNightState.Snapshot snap = ClientNightState.read(buf);
			client.execute(() -> ClientNightState.apply(snap));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.NIGHT_RESULT, (client, handler, buf, sender) -> {
			boolean victory = buf.readBoolean();
			int night = buf.readVarInt();
			int done = buf.readVarInt();
			int required = buf.readVarInt();
			int catches = buf.readVarInt();
			int time = buf.readVarInt();
			int reason = buf.readVarInt();
			client.execute(() -> client.setScreen(new NightResultScreen(victory, night, done, required, catches, time, reason)));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.STAMINA, (client, handler, buf, sender) -> {
			float value = buf.readFloat();
			boolean exhausted = buf.readBoolean();
			client.execute(() -> {
				if (exhausted && !ClientNightState.exhausted && client.player != null) {
					client.getSoundManager().play(PositionedSoundInstance.master(ModSounds.BREATH, 1.0f, 0.8f));
				}
				ClientNightState.stamina = value;
				ClientNightState.exhausted = exhausted;
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.MENU, (client, handler, buf, sender) -> {
			com.fnaf.nightshift.client.screen.NightMenuScreen.Data data = com.fnaf.nightshift.client.screen.NightMenuScreen.Data.read(buf);
			client.execute(() -> {
				if (client.currentScreen instanceof com.fnaf.nightshift.client.screen.NightMenuScreen menu) menu.update(data);
				else client.setScreen(new com.fnaf.nightshift.client.screen.NightMenuScreen(data));
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CAMERAS, (client, handler, buf, sender) -> {
			boolean open = buf.readBoolean();
			BlockPos monitor = buf.readBlockPos();
			boolean disrupted = buf.readBoolean();
			int n = buf.readVarInt();
			java.util.List<com.fnaf.nightshift.client.screen.CameraScreen.Cam> cams = new java.util.ArrayList<>();
			for (int i = 0; i < n; i++) {
				BlockPos p = buf.readBlockPos();
				net.minecraft.util.math.Direction f = net.minecraft.util.math.Direction.fromHorizontal(buf.readVarInt());
				String name = buf.readString();
				boolean broken = buf.readBoolean();
				cams.add(new com.fnaf.nightshift.client.screen.CameraScreen.Cam(p, f, name, broken));
			}
			client.execute(() -> {
				com.fnaf.nightshift.client.screen.CameraScreen cur = com.fnaf.nightshift.client.screen.CameraScreen.current();
				if (cur != null) cur.update(cams, disrupted);
				else if (open && !cams.isEmpty()) client.setScreen(new com.fnaf.nightshift.client.screen.CameraScreen(monitor, cams, disrupted));
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CAMERAS_CLOSE, (client, handler, buf, sender) -> client.execute(() -> {
			if (client.currentScreen instanceof com.fnaf.nightshift.client.screen.CameraScreen) client.setScreen(null);
		}));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.JUMPSCARE, (client, handler, buf, sender) -> {
			int kind = buf.readVarInt();
			client.execute(() -> JumpscareOverlay.start(AnimatronicKind.values()[Math.max(0, Math.min(kind, AnimatronicKind.values().length - 1))]));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.OPEN_MINIGAME, (client, handler, buf, sender) -> {
			BlockPos pos = buf.readBlockPos();
			int typeIdx = buf.readVarInt();
			long seed = buf.readLong();
			int level = buf.readVarInt();
			client.execute(() -> {
				RepairType type = RepairType.values()[Math.max(0, Math.min(typeIdx, RepairType.values().length - 1))];
				switch (type.minigame()) {
					case WIRES -> client.setScreen(new WireMinigameScreen(pos, type, seed, level));
					case SEQUENCE -> client.setScreen(new SequenceMinigameScreen(pos, type, seed, level));
					case CODE -> client.setScreen(new CodeMinigameScreen(pos, type, seed, level));
					case REPLACE_PART -> client.setScreen(new ReplacePartScreen(pos, type, seed, level));
					default -> {
					}
				}
			});
		});
	}
}
