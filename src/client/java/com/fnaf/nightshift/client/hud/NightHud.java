package com.fnaf.nightshift.client.hud;

import com.fnaf.nightshift.campaign.NightPhase;
import com.fnaf.nightshift.client.ClientNightState;
import com.fnaf.nightshift.item.FlashlightItem;
import com.fnaf.nightshift.registry.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Interfaz de la noche: hora, tiempo restante, objetivos, reparaciones,
 * linterna, alertas de peligro y estado del refugio.
 */
public final class NightHud {
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private NightHud() {
	}

	public static void render(DrawContext ctx, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		// con las cámaras abiertas el HUD vanilla se oculta, pero el del mod sigue visible
		if (player == null || (client.options.hudHidden && com.fnaf.nightshift.client.screen.CameraScreen.current() == null)) return;
		TextRenderer tr = client.textRenderer;
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();

		NightPhase phase = ClientNightState.phase;
		if (phase.isActive()) {
			renderDanger(ctx, w, h, tr);
			renderPanel(ctx, tr, player);
			renderCenterMessages(ctx, tr, w, h);
			renderOfficeStatus(ctx, tr, w, h);
			renderStamina(ctx, tr, w, h);
		}
		if (com.fnaf.nightshift.client.screen.CameraScreen.current() == null) renderFlashlight(ctx, tr, player, w, h);
	}

	private static int interpolatedRemaining() {
		long elapsedMs = System.currentTimeMillis() - ClientNightState.lastUpdate;
		return Math.max(0, ClientNightState.ticksRemaining - (int) (elapsedMs / 50));
	}

	public static String clock(int remaining, int total) {
		double progress = 1.0 - remaining / (double) Math.max(1, total);
		double hours = MathHelper.clamp(progress, 0, 1) * 6.0;
		int hour = (int) hours;
		int minute = (int) ((hours - hour) * 60);
		int display = hour == 0 ? 12 : hour;
		return String.format("%d:%02d AM", display, minute);
	}

	private static void renderPanel(DrawContext ctx, TextRenderer tr, ClientPlayerEntity player) {
		int x = 6, y = 6, width = 168;
		int remaining = interpolatedRemaining();
		int lines = 4 + ClientNightState.objectives.size() + (ClientNightState.phase == NightPhase.RETURN_TO_SHELTER ? 1 : 0)
				+ (ClientNightState.ventEnabled ? 1 : 0);
		int height = 12 + lines * 11;
		ctx.fill(x - 3, y - 3, x + width, y + height, 0xA0101014);
		ctx.fill(x - 3, y - 3, x + width, y - 2, 0xFF8B1A1A);

		ctx.drawTextWithShadow(tr, Text.translatable("hud.fnaf_night_shift.night", ClientNightState.night).formatted(Formatting.BOLD), x, y, 0xFFE0E0E0);
		String clock = clock(remaining, ClientNightState.totalTicks);
		ctx.drawTextWithShadow(tr, clock, x + width - 6 - tr.getWidth(clock), y, 0xFFFFD27F);
		y += 12;

		// barra de tiempo hasta las 6 AM
		float progress = 1f - remaining / (float) Math.max(1, ClientNightState.totalTicks);
		ctx.fill(x, y, x + width - 8, y + 4, 0xFF2A2A2A);
		ctx.fill(x, y, x + (int) ((width - 8) * MathHelper.clamp(progress, 0f, 1f)), y + 4, 0xFFC9A227);
		y += 7;
		int secs = remaining / 20;
		ctx.drawTextWithShadow(tr, Text.translatable("hud.fnaf_night_shift.time_left", String.format("%d:%02d", secs / 60, secs % 60)), x, y, 0xFFB0B0B0);
		y += 11;

		int color = ClientNightState.completed >= ClientNightState.required ? 0xFF7CFC7C : 0xFFFFFFFF;
		if (ClientNightState.required == 0) {
			ctx.drawTextWithShadow(tr, Text.translatable("hud.fnaf_night_shift.survive"), x, y, 0xFFFFFFFF);
		} else {
			ctx.drawTextWithShadow(tr, Text.translatable("hud.fnaf_night_shift.repairs", ClientNightState.completed, ClientNightState.required), x, y, color);
		}
		y += 11;
		ctx.drawTextWithShadow(tr, Text.translatable("hud.fnaf_night_shift.objectives").formatted(Formatting.UNDERLINE), x, y, 0xFFC0C0C0);
		y += 11;

		for (ClientNightState.Objective o : ClientNightState.objectives) {
			String mark = o.done() ? "✔ " : o.extra() ? "⚠ " : "✖ ";
			int c = o.done() ? 0xFF5FAF5F : o.extra() ? 0xFFFFA040 : 0xFFFF7070;
			Text name = Text.literal(mark).append(Text.translatable(o.type().translationKey()));
			ctx.drawTextWithShadow(tr, name, x + 4, y, c);
			if (!o.done()) {
				String dir = direction(player, o.pos());
				ctx.drawTextWithShadow(tr, dir, x + width - 8 - tr.getWidth(dir), y, 0xFFAAAAAA);
			}
			y += 11;
		}
		if (ClientNightState.phase == NightPhase.RETURN_TO_SHELTER) {
			ctx.drawTextWithShadow(tr, Text.literal("► ").append(Text.translatable("hud.fnaf_night_shift.survive_until_6")), x + 4, y, 0xFF7FD7FF);
			y += 11;
		}
		if (ClientNightState.ventEnabled) {
			// estado de la ventilación de la oficina
			int vs = ClientNightState.ventTicks / 20;
			float frac = ClientNightState.ventTicks / (float) ClientNightState.ventInterval;
			int vc = ClientNightState.ventFailed ? 0xFFFF3030 : frac < 0.15f ? 0xFFFF8040 : frac < 0.35f ? 0xFFFFD27F : 0xFF7FD7FF;
			Text vt = ClientNightState.ventFailed ? Text.translatable("hud.fnaf_night_shift.vent_failed")
					: Text.translatable("hud.fnaf_night_shift.vent", String.format("%d:%02d", vs / 60, vs % 60));
			boolean blink = ClientNightState.ventFailed || frac < 0.15f;
			if (!blink || (System.currentTimeMillis() / 400) % 2 == 0) ctx.drawTextWithShadow(tr, vt, x, y, vc);
		}
	}

	/** Avisos de la oficina: animatrónicos acechando en las puertas y puertas forzadas. */
	private static void renderOfficeStatus(DrawContext ctx, TextRenderer tr, int w, int h) {
		int y = h / 2 + 24;
		if (!ClientNightState.lurking.isEmpty() && (System.currentTimeMillis() / 300) % 2 == 0) {
			for (String side : ClientNightState.lurking) {
				Text t = Text.translatable("hud.fnaf_night_shift.lurk_side", Text.translatable("direction.fnaf_night_shift." + side));
				ctx.drawCenteredTextWithShadow(tr, t, w / 2, y, 0xFFFF3030);
				y += 11;
			}
		}
		if (ClientNightState.jammedDoors > 0) {
			ctx.drawCenteredTextWithShadow(tr, Text.translatable("hud.fnaf_night_shift.door_jammed", ClientNightState.jammedDoors), w / 2, y, 0xFFFFA040);
		}
	}

	/** Barra de estamina sobre la barra de objetos. */
	private static void renderStamina(DrawContext ctx, TextRenderer tr, int w, int h) {
		if (ClientNightState.caught) return;
		float s = ClientNightState.stamina;
		if (s >= 0.999f && !ClientNightState.exhausted) return;
		int bw = 90;
		int x = w / 2 - bw / 2, y = h - 58;
		ctx.fill(x - 1, y - 1, x + bw + 1, y + 4, 0xA0000000);
		int col = ClientNightState.exhausted ? ((System.currentTimeMillis() / 250) % 2 == 0 ? 0xFFFF3030 : 0xFF802020)
				: s < 0.3f ? 0xFFFFA040 : 0xFFE0E0E0;
		ctx.fill(x, y, x + (int) (bw * s), y + 3, col);
		if (ClientNightState.exhausted) {
			ctx.drawCenteredTextWithShadow(tr, Text.translatable("hud.fnaf_night_shift.exhausted"), w / 2, y - 11, 0xFFFF6060);
		}
	}

	private static String direction(ClientPlayerEntity player, BlockPos target) {
		double dx = target.getX() + 0.5 - player.getX();
		double dz = target.getZ() + 0.5 - player.getZ();
		int dist = (int) Math.sqrt(dx * dx + dz * dz);
		double angleTo = Math.toDegrees(Math.atan2(-dx, dz));
		double rel = MathHelper.wrapDegrees(angleTo - player.getYaw());
		int idx = Math.floorMod((int) Math.round(rel / 45.0), 8);
		int dy = target.getY() - player.getBlockY();
		String level = dy > 3 ? " ▲" : dy < -3 ? " ▼" : "";
		return dist + "m " + ARROWS[idx] + level;
	}

	private static void renderCenterMessages(DrawContext ctx, TextRenderer tr, int w, int h) {
		if (ClientNightState.caught) {
			Text t = Text.translatable("hud.fnaf_night_shift.caught").formatted(Formatting.DARK_RED, Formatting.BOLD);
			ctx.drawCenteredTextWithShadow(tr, t, w / 2, h / 2 - 30, 0xFFFFFFFF);
			return;
		}
		if (ClientNightState.graceTicks > 0) {
			int s = ClientNightState.graceTicks / 20;
			ctx.drawCenteredTextWithShadow(tr, Text.translatable("hud.fnaf_night_shift.grace", s), w / 2, h / 2 + 48, 0xFFFFD27F);
		}
		if (ClientNightState.inShelter && com.fnaf.nightshift.client.screen.CameraScreen.current() == null) {
			ctx.drawCenteredTextWithShadow(tr, Text.translatable("hud.fnaf_night_shift.in_shelter"), w / 2, h - 72, 0xFF7FD7FF);
		}
		if (ClientNightState.danger == 2) {
			float pulse = (System.currentTimeMillis() % 600) / 600f;
			int alpha = (int) (155 + 100 * Math.sin(pulse * Math.PI * 2));
			ctx.drawCenteredTextWithShadow(tr, Text.translatable("hud.fnaf_night_shift.danger").formatted(Formatting.BOLD), w / 2, 40,
					(MathHelper.clamp(alpha, 0, 255) << 24) | 0xFF3030);
		} else if (ClientNightState.danger == 1) {
			ctx.drawCenteredTextWithShadow(tr, Text.translatable("hud.fnaf_night_shift.near"), w / 2, 40, 0xC0D08080);
		}
	}

	private static void renderDanger(DrawContext ctx, int w, int h, TextRenderer tr) {
		if (ClientNightState.danger == 0) return;
		float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / (ClientNightState.danger == 2 ? 90.0 : 300.0)));
		int maxAlpha = ClientNightState.danger == 2 ? 150 : 60;
		int a = (int) (maxAlpha * (0.5f + 0.5f * pulse));
		int edge = ClientNightState.danger == 2 ? 60 : 40;
		int red = (a << 24) | 0x8B0000;
		ctx.fillGradient(0, 0, w, edge, red, 0x008B0000);
		ctx.fillGradient(0, h - edge, w, h, 0x008B0000, red);
		// bordes laterales (degradado vertical aproximado con franjas)
		for (int i = 0; i < edge; i += 4) {
			int aa = (int) (a * (1f - i / (float) edge));
			int c = (aa << 24) | 0x8B0000;
			ctx.fill(i, 0, i + 4, h, c);
			ctx.fill(w - i - 4, 0, w - i, h, c);
		}
	}

	private static void renderFlashlight(DrawContext ctx, TextRenderer tr, ClientPlayerEntity player, int w, int h) {
		ItemStack flashlight = null;
		if (player.getMainHandStack().isOf(ModItems.FLASHLIGHT)) flashlight = player.getMainHandStack();
		else if (player.getOffHandStack().isOf(ModItems.FLASHLIGHT)) flashlight = player.getOffHandStack();
		if (flashlight == null && ClientNightState.phase.isActive()) {
			for (int i = 0; i < player.getInventory().size(); i++) {
				ItemStack s = player.getInventory().getStack(i);
				if (s.isOf(ModItems.FLASHLIGHT)) {
					flashlight = s;
					break;
				}
			}
		}
		if (flashlight == null) return;
		float f = FlashlightItem.getBatteryFraction(flashlight);
		boolean on = FlashlightItem.isOn(flashlight);
		Text label = Text.translatable(on ? "hud.fnaf_night_shift.flashlight_on" : "hud.fnaf_night_shift.flashlight_off");
		String pct = Math.round(f * 100) + "%";
		int bw = Math.max(64, tr.getWidth(label));
		int x = w - bw - 12, y = h - 30;
		ctx.fill(x - 4, y - 13, x + bw + 6, y + 9, 0x90101014);
		ctx.drawTextWithShadow(tr, label, x, y - 11, on ? 0xFFFFF2A0 : 0xFF909090);
		int barW = bw - tr.getWidth(pct) - 4;
		ctx.fill(x, y, x + barW, y + 6, 0xFF2A2A2A);
		int color = f > 0.5f ? 0xFF7CFC7C : f > 0.2f ? 0xFFFFD27F : 0xFFFF5050;
		ctx.fill(x, y, x + (int) (barW * f), y + 6, color);
		ctx.drawTextWithShadow(tr, pct, x + bw - tr.getWidth(pct), y - 1, 0xFFCCCCCC);
	}
}
