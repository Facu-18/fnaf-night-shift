package com.fnaf.nightshift.client.hud;

import com.fnaf.nightshift.FnafNightShift;
import com.fnaf.nightshift.client.ClientNightState;
import com.fnaf.nightshift.client.screen.CameraScreen;
import com.fnaf.nightshift.config.ModConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Filtro de terror del mod: post-procesado vanilla (no necesita Iris ni
 * OptiFine) que se aplica al mundo durante las noches. Tiene tres niveles de
 * intensidad (calma, algo cerca, persecución) y una variante de visión
 * borrosa para el fallo de ventilación. Si hay un paquete de shaders de Iris
 * activo se desactiva solo: el paquete se encarga del aspecto visual.
 */
public final class HorrorFilter {
	@Nullable
	private static PostEffectProcessor processor;
	@Nullable
	private static Identifier loaded;
	private static int width, height;
	private static boolean failed;

	private HorrorFilter() {
	}

	public static void render(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!shouldApply(client)) return;
		int level = ClientNightState.danger >= 2 ? 2 : ClientNightState.danger == 1 ? 1 : 0;
		Identifier id = FnafNightShift.id("shaders/post/horror_" + level + "_" + (ClientNightState.ventFailed ? 1 : 0) + ".json");
		Framebuffer main = client.getFramebuffer();
		try {
			if (processor == null || !id.equals(loaded)) {
				close();
				processor = new PostEffectProcessor(client.getTextureManager(), client.getResourceManager(), main, id);
				loaded = id;
				width = -1;
			}
			if (width != main.textureWidth || height != main.textureHeight) {
				width = main.textureWidth;
				height = main.textureHeight;
				processor.setupDimensions(width, height);
			}
			RenderSystem.disableBlend();
			RenderSystem.disableDepthTest();
			RenderSystem.resetTextureMatrix();
			processor.render(tickDelta);
			main.beginWrite(false);
			RenderSystem.enableDepthTest();
		} catch (Exception e) {
			// si el controlador gráfico no soporta el efecto, no se vuelve a intentar
			failed = true;
			FnafNightShift.LOGGER.warn("Filtro de terror desactivado: {}", e.toString());
			close();
		}
	}

	private static boolean shouldApply(MinecraftClient client) {
		if (failed || !ModConfig.get().horrorFilter || client.world == null) return false;
		if (!ClientNightState.phase.isActive() || ClientNightState.caught) return false;
		if (CameraScreen.current() != null) return false; // las cámaras tienen su propio aspecto
		return !irisShaderPackInUse();
	}

	/** Detección de Iris por reflexión (dependencia opcional). */
	public static boolean irisShaderPackInUse() {
		for (String cls : new String[]{"net.irisshaders.iris.api.v0.IrisApi", "net.coderbot.iris.apiimpl.IrisApiV0Impl"}) {
			try {
				Class<?> api = Class.forName(cls);
				Object inst = api.getMethod("getInstance").invoke(null);
				return (Boolean) inst.getClass().getMethod("isShaderPackInUse").invoke(inst);
			} catch (Throwable ignored) {
			}
		}
		return false;
	}

	public static void close() {
		if (processor != null) processor.close();
		processor = null;
		loaded = null;
	}
}
