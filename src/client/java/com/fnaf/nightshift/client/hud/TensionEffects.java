package com.fnaf.nightshift.client.hud;

import com.fnaf.nightshift.campaign.NightPhase;
import com.fnaf.nightshift.client.ClientNightState;
import com.fnaf.nightshift.registry.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * Efectos de tensión del lado del cliente. Todo es dosificado para no
 * repetir sonidos constantemente:
 * <ul>
 *   <li>latidos cuando un animatrónico está cerca (más rápidos si te persigue);</li>
 *   <li>interferencias breves (sonido + estática), más frecuentes en noches avanzadas;</li>
 *   <li>ambiente: zumbido industrial con silencios inquietantes y la pista de ambiente cada pocos minutos;</li>
 *   <li>respiración y bloqueo de la carrera al agotar la estamina;</li>
 *   <li>visión borrosa y mareo visual cuando falla la ventilación.</li>
 * </ul>
 * Todos los efectos visuales son superposiciones 2D de la interfaz: no usan
 * shaders de post-procesado, así que no interfieren con paquetes de shaders.
 */
public final class TensionEffects {
	private static final Random RANDOM = new Random();
	private static int heartbeatCooldown;
	private static int humCooldown = 200;
	private static int silenceTicks;
	private static int ambienceCooldown = 60;
	private static int staticFlash;
	private static NightPhase lastPhase = NightPhase.NONE;

	private TensionEffects() {
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null || client.isPaused()) return;
		NightPhase phase = ClientNightState.phase;
		boolean active = phase.isActive();

		// estamina agotada: no se puede correr (el servidor también lo comprueba)
		if (ClientNightState.exhausted) {
			client.player.setSprinting(false);
			client.options.sprintKey.setPressed(false);
		}

		if (active && !lastPhase.isActive()) {
			ambienceCooldown = 40;
			silenceTicks = 0;
		}
		lastPhase = phase;
		if (!active) return;
		int night = Math.max(1, ClientNightState.night);

		// latidos
		if (ClientNightState.danger > 0 && !ClientNightState.caught) {
			if (--heartbeatCooldown <= 0) {
				heartbeatCooldown = ClientNightState.danger == 2 ? 11 : 20;
				client.getSoundManager().play(PositionedSoundInstance.master(ModSounds.HEARTBEAT, 1.0f,
						ClientNightState.danger == 2 ? 1.0f : 0.75f));
			}
		}

		// silencios inquietantes y zumbido industrial
		if (silenceTicks > 0) {
			silenceTicks--;
		} else if (--humCooldown <= 0) {
			humCooldown = 220 + RANDOM.nextInt(200);
			if (RANDOM.nextInt(8) < 1 + night / 3) {
				silenceTicks = 300 + RANDOM.nextInt(400); // corte total del zumbido
			} else {
				client.getSoundManager().play(PositionedSoundInstance.ambient(ModSounds.AMBIENT_HUM, 1.0f, 0.6f));
			}
		}

		// pista de ambiente (dosificada: al empezar y luego cada 2-4 minutos)
		if (--ambienceCooldown <= 0) {
			ambienceCooldown = 20 * (120 + RANDOM.nextInt(120));
			client.getSoundManager().play(PositionedSoundInstance.ambient(ModSounds.AMBIENT_FNAF, 1.0f, 0.8f));
		}

		// alucinaciones: destellos subliminales y pasos fantasma (nunca durante un peligro real)
		tickPhantomSteps(client);
		int from = com.fnaf.nightshift.config.ModConfig.get().hallucinationsFromNight;
		if (night >= from && ClientNightState.danger == 0 && !ClientNightState.caught
				&& RANDOM.nextFloat() < 0.00025f * (night - from + 1)) {
			if (RANDOM.nextBoolean()) {
				com.fnaf.nightshift.entity.AnimatronicKind[] kinds = {com.fnaf.nightshift.entity.AnimatronicKind.BONNIE,
						com.fnaf.nightshift.entity.AnimatronicKind.CHICA, com.fnaf.nightshift.entity.AnimatronicKind.FOXY,
						com.fnaf.nightshift.entity.AnimatronicKind.FREDDY, com.fnaf.nightshift.entity.AnimatronicKind.GOLDEN_FREDDY};
				JumpscareOverlay.flash(kinds[RANDOM.nextInt(kinds.length)]);
			} else {
				phantomSteps = 4;
				phantomDelay = 0;
			}
		}

		// interferencias: más frecuentes en noches avanzadas y con peligro cerca
		float chance = 0.0006f * night + (ClientNightState.danger == 2 ? 0.006f : 0f);
		if (staticFlash > 0) staticFlash--;
		else if (RANDOM.nextFloat() < chance) {
			staticFlash = 3 + RANDOM.nextInt(4);
			client.getSoundManager().play(PositionedSoundInstance.master(ModSounds.AMBIENT_STATIC, 1.0f, 0.55f));
		}
	}

	private static int phantomSteps;
	private static int phantomDelay;

	/** Pasos metálicos que se acercan por detrás del jugador... y no hay nadie. */
	private static void tickPhantomSteps(MinecraftClient client) {
		if (phantomSteps <= 0 || --phantomDelay > 0) return;
		phantomDelay = 9;
		net.minecraft.util.math.Vec3d look = client.player.getRotationVec(1.0f).multiply(1, 0, 1).normalize();
		double dist = 2.0 + phantomSteps * 1.8;
		net.minecraft.util.math.Vec3d p = client.player.getPos().subtract(look.multiply(dist));
		client.getSoundManager().play(new PositionedSoundInstance(ModSounds.STEP_METAL, net.minecraft.sound.SoundCategory.HOSTILE,
				1.3f, 0.85f + RANDOM.nextFloat() * 0.2f, net.minecraft.util.math.random.Random.create(), p.x, p.y, p.z));
		phantomSteps--;
	}

	public static void render(DrawContext ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
		long ms = System.currentTimeMillis();
		if (ClientNightState.phase.isActive() && ClientNightState.ventFailed && !ClientNightState.caught) {
			// visión borrosa: velos translúcidos desplazados que oscilan + bordes oscuros
			float t = (ms % 4000) / 4000f;
			int off = (int) (6 * Math.sin(t * Math.PI * 2));
			ctx.fill(off, 0, w + off, h, 0x30707860);
			ctx.fill(-off, 2, w - off, h + 2, 0x28505848);
			int edge = (int) (h * 0.22);
			ctx.fillGradient(0, 0, w, edge, 0xB0101010, 0x00101010);
			ctx.fillGradient(0, h - edge, w, h, 0x00101010, 0xB0101010);
			for (int i = 0; i < edge; i += 3) {
				int a = (int) (0xB0 * (1f - i / (float) edge));
				ctx.fill(i, 0, i + 3, h, a << 24 | 0x101010);
				ctx.fill(w - i - 3, 0, w - i, h, a << 24 | 0x101010);
			}
		}
		if (staticFlash > 0) {
			for (int i = 0; i < 900; i++) {
				int x = RANDOM.nextInt(Math.max(1, w)), y = RANDOM.nextInt(Math.max(1, h));
				int g = 120 + RANDOM.nextInt(130);
				ctx.fill(x, y, x + 2, y + 1, 0x70000000 | (g << 16) | (g << 8) | g);
			}
		}
		if (ClientNightState.exhausted && client.player != null) {
			float pulse = (float) (0.5 + 0.5 * Math.sin(ms / 250.0));
			int a = MathHelper.clamp((int) (70 * pulse), 0, 255);
			ctx.fillGradient(0, 0, w, h / 5, a << 24, 0);
			ctx.fillGradient(0, h - h / 5, w, h, 0, a << 24);
		}
	}
}
