package com.fnaf.nightshift.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Pantalla de victoria (6 AM) o derrota (GAME OVER). */
public class NightResultScreen extends Screen {
	private final boolean victory;
	private final int night, done, required, catches, timeTicks, reason;
	private int age;

	public NightResultScreen(boolean victory, int night, int done, int required, int catches, int timeTicks, int reason) {
		super(Text.translatable(victory ? "screen.fnaf_night_shift.victory" : "screen.fnaf_night_shift.defeat"));
		this.victory = victory;
		this.night = night;
		this.done = done;
		this.required = required;
		this.catches = catches;
		this.timeTicks = timeTicks;
		this.reason = reason;
	}

	@Override
	protected void init() {
		int y = height / 2 + 70;
		// Siguiente noche / reintentar, volver al menú o cerrar
		Text again = Text.translatable(victory && night < 7 ? "screen.fnaf_night_shift.next_night" : "screen.fnaf_night_shift.retry");
		addDrawableChild(ButtonWidget.builder(again, b -> {
			NightMenuScreen.send(victory && night < 7 ? com.fnaf.nightshift.campaign.MenuActions.CONTINUE
					: com.fnaf.nightshift.campaign.MenuActions.RESTART, 0);
			close();
		}).dimensions(width / 2 - 154, y, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.fnaf_night_shift.back_menu"), b -> {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(com.fnaf.nightshift.network.ModPackets.MENU_REQUEST,
					net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create());
		}).dimensions(width / 2 - 50, y, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.fnaf_night_shift.continue"), b -> close())
				.dimensions(width / 2 + 54, y, 100, 20).build());
	}

	@Override
	public void tick() {
		age++;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, victory ? 0xE0101010 : 0xF0100000);
		// estática de TV
		java.util.Random r = new java.util.Random(age * 31L);
		for (int i = 0; i < 300; i++) {
			int x = r.nextInt(Math.max(1, width));
			int y = r.nextInt(Math.max(1, height));
			int g = 40 + r.nextInt(60);
			ctx.fill(x, y, x + 2, y + 1, 0x30000000 | (g << 16) | (g << 8) | g);
		}
		ctx.getMatrices().push();
		ctx.getMatrices().translate(width / 2f, height / 2f - 70, 0);
		ctx.getMatrices().scale(3f, 3f, 1f);
		Text big = victory ? Text.literal("6:00 AM") : Text.translatable("title.fnaf_night_shift.game_over");
		ctx.drawCenteredTextWithShadow(textRenderer, big, 0, 0, victory ? 0xFFFFD27F : 0xFFD02020);
		ctx.getMatrices().pop();

		int y = height / 2 - 30;
		ctx.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.BOLD), width / 2, y, 0xFFFFFFFF);
		y += 18;
		ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.fnaf_night_shift.night", night), width / 2, y, 0xFFC0C0C0);
		y += 12;
		ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.fnaf_night_shift.repairs", done, required), width / 2, y, 0xFFC0C0C0);
		y += 12;
		int s = timeTicks / 20;
		ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.fnaf_night_shift.time", String.format("%d:%02d", s / 60, s % 60)), width / 2, y, 0xFFC0C0C0);
		y += 12;
		ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.fnaf_night_shift.catches", catches), width / 2, y, 0xFFC0C0C0);
		y += 14;
		Text hint = victory ? Text.translatable(night >= 7 ? "screen.fnaf_night_shift.campaign_done" : "screen.fnaf_night_shift.next_hint")
				: Text.translatable(reason == 1 ? "screen.fnaf_night_shift.reason_time" : "screen.fnaf_night_shift.reason_caught");
		ctx.drawCenteredTextWithShadow(textRenderer, hint, width / 2, y, victory ? 0xFF7CFC7C : 0xFFFF7070);
		super.render(ctx, mouseX, mouseY, delta);
	}
}
