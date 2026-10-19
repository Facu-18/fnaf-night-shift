package com.fnaf.nightshift.client.hud;

import com.fnaf.nightshift.ai.AiState;
import com.fnaf.nightshift.entity.AnimatronicEntity;
import com.fnaf.nightshift.entity.AnimatronicKind;
import com.fnaf.nightshift.registry.ModEntities;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * Susto a pantalla completa: renderiza el modelo 3D real del animatrónico
 * (con su animación de ataque) pegado a la cámara, con temblor y destello rojo.
 */
public final class JumpscareOverlay {
	private static final int DURATION = 36;
	private static final Random RANDOM = new Random();
	@Nullable
	private static AnimatronicEntity dummy;
	private static int ticks;
	/** Destello subliminal (alucinación): sin grito ni temblor. */
	private static boolean flash;

	private JumpscareOverlay() {
	}

	/** Alucinación: la cara de un animatrónico aparece un instante entre estática. */
	public static void flash(AnimatronicKind kind) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || isActive()) return;
		AnimatronicEntity e = ModEntities.typeOf(kind).create(client.world);
		if (e == null) return;
		e.syncAiState(AiState.IDLE, false);
		dummy = e;
		ticks = 4;
		flash = true;
		client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
				com.fnaf.nightshift.registry.ModSounds.AMBIENT_STATIC, 0.8f, 0.7f));
	}

	public static void start(AnimatronicKind kind) {
		flash = false;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) return;
		AnimatronicEntity e = ModEntities.typeOf(kind).create(client.world);
		if (e == null) return;
		e.syncAiState(AiState.ATTACK, false);
		e.setYaw(0);
		e.setBodyYaw(0);
		e.setHeadYaw(0);
		dummy = e;
		ticks = DURATION;
		e.triggerAnim(AnimatronicEntity.CTRL_ACTION, "attack");
		// grito propio de cada animatrónico, sin posición y por el canal maestro: siempre se oye
		net.minecraft.sound.SoundEvent scream = switch (kind) {
			case FOXY -> com.fnaf.nightshift.registry.ModSounds.JUMPSCARE_FOXY;
			case GOLDEN_FREDDY -> com.fnaf.nightshift.registry.ModSounds.JUMPSCARE_GOLDEN;
			default -> com.fnaf.nightshift.registry.ModSounds.JUMPSCARE;
		};
		client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(scream, 1.0f, 1.0f));
		if (client.currentScreen != null) client.setScreen(null);
	}

	public static void tick(MinecraftClient client) {
		if (ticks > 0) {
			ticks--;
			if (dummy != null) dummy.age++;
			if (ticks == 0) dummy = null;
		}
	}

	public static boolean isActive() {
		return ticks > 0 && dummy != null;
	}

	public static void render(DrawContext ctx, float tickDelta) {
		if (!isActive()) return;
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		if (flash) {
			ctx.fill(0, 0, w, h, 0xC0000000);
			int size = (int) (h * 1.1f);
			dummy.prevBodyYaw = 180f;
			dummy.prevHeadYaw = 180f;
			dummy.prevYaw = 180f;
			ctx.getMatrices().push();
			ctx.getMatrices().translate(0, 0, 200);
			InventoryScreen.drawEntity(ctx, w / 2 + RANDOM.nextInt(9) - 4, (int) (h / 2f + size * 2.04f), size, 0f, 0f, dummy);
			ctx.getMatrices().pop();
			for (int i = 0; i < 1500; i++) {
				int x = RANDOM.nextInt(Math.max(1, w)), y = RANDOM.nextInt(Math.max(1, h));
				int g = 100 + RANDOM.nextInt(150);
				ctx.fill(x, y, x + 2, y + 1, 0xA0000000 | (g << 16) | (g << 8) | g);
			}
			return;
		}
		float t = (DURATION - ticks + tickDelta) / DURATION;

		ctx.fill(0, 0, w, h, 0xF0050505);
		// zoom hacia la cámara durante los primeros instantes
		float zoom = Math.min(1f, t * 4f);
		int size = (int) (h * (0.6f + 0.7f * zoom));
		int shake = (int) (12 * (1f - t));
		int ox = shake > 0 ? RANDOM.nextInt(shake * 2 + 1) - shake : 0;
		int oy = shake > 0 ? RANDOM.nextInt(shake * 2 + 1) - shake : 0;
		// la cara (≈2,1 bloques sobre los pies) queda centrada en pantalla
		int feetY = (int) (h / 2f + size * 2.04f) + oy;
		// drawEntity fija la rotación actual pero GeckoLib interpola con la anterior: igualarlas
		dummy.prevBodyYaw = 180f;
		dummy.prevHeadYaw = 180f;
		dummy.prevYaw = 180f;
		dummy.prevPitch = 0f;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 200);
		InventoryScreen.drawEntity(ctx, w / 2 + ox, feetY, size, 0f, 0f, dummy);
		ctx.getMatrices().pop();

		if (t < 0.15f || (t > 0.4f && t < 0.45f)) {
			ctx.fill(0, 0, w, h, 0x60FF0000);
		}
		if (t > 0.8f) {
			int a = (int) (255 * (t - 0.8f) / 0.2f);
			ctx.fill(0, 0, w, h, (Math.min(255, a) << 24));
		}
	}
}
