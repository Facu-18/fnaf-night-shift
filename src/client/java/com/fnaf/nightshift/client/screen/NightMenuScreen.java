package com.fnaf.nightshift.client.screen;

import com.fnaf.nightshift.campaign.Difficulty;
import com.fnaf.nightshift.campaign.MenuActions;
import com.fnaf.nightshift.campaign.NightDefinition;
import com.fnaf.nightshift.campaign.NightPhase;
import com.fnaf.nightshift.network.ModPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Menú de la campaña: nueva partida, continuar, selección de noches
 * desbloqueadas, progreso, reinicio y preparación del mapa. Todas las
 * acciones las ejecuta el servidor con la misma lógica que los comandos.
 * Se abre con la tecla N o haciendo clic derecho en el Núcleo de la oficina.
 */
public class NightMenuScreen extends Screen {
	public record Data(int currentNight, int highestUnlocked, int completedMask, int difficulty, NightPhase phase, int activeNight,
					   boolean canControl, boolean admin, boolean hasShelter, int stations, int cameras, int lockers,
					   boolean needsUpgrade, int totalCatches) {
		public static Data read(PacketByteBuf buf) {
			return new Data(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), NightPhase.byOrdinal(buf.readVarInt()),
					buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
		}

		boolean completed(int night) {
			return (completedMask & (1 << night)) != 0;
		}
	}

	private Data data;
	private boolean confirmNew;

	public NightMenuScreen(Data data) {
		super(Text.translatable("screen.fnaf_night_shift.menu"));
		this.data = data;
	}

	public void update(Data d) {
		this.data = d;
		clearAndInit();
	}

	public static void send(int action, int arg) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(action);
		buf.writeVarInt(arg);
		ClientPlayNetworking.send(ModPackets.MENU_ACTION, buf);
	}

	@Override
	protected void init() {
		int cx = width / 2;
		int y = height / 2 - 40;
		boolean active = data.phase().isActive();
		boolean control = data.canControl() && data.hasShelter();

		// fila de noches
		int tile = 40, gap = 4;
		int total = NightDefinition.MAX_NIGHT * tile + (NightDefinition.MAX_NIGHT - 1) * gap;
		int x0 = cx - total / 2;
		for (int n = 1; n <= NightDefinition.MAX_NIGHT; n++) {
			int night = n;
			boolean unlocked = night <= data.highestUnlocked();
			Text label = Text.literal((data.completed(night) ? "✔ " : unlocked ? "" : "🔒 ") + night);
			ButtonWidget b = ButtonWidget.builder(label, btn -> send(MenuActions.START_NIGHT, night))
					.dimensions(x0 + (n - 1) * (tile + gap), y, tile, 20)
					.tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("screen.fnaf_night_shift.night_tooltip", night)))
					.build();
			b.active = control && (unlocked || data.admin());
			addDrawableChild(b);
		}
		y += 30;

		ButtonWidget cont = ButtonWidget.builder(Text.translatable("screen.fnaf_night_shift.continue_night", data.currentNight()),
				b -> send(MenuActions.CONTINUE, 0)).dimensions(cx - 152, y, 148, 20).build();
		cont.active = control;
		addDrawableChild(cont);
		ButtonWidget fresh = ButtonWidget.builder(Text.translatable(confirmNew ? "screen.fnaf_night_shift.new_game_confirm" : "screen.fnaf_night_shift.new_game")
				.formatted(confirmNew ? Formatting.RED : Formatting.RESET), b -> {
			if (!confirmNew) {
				confirmNew = true;
				clearAndInit();
			} else {
				send(MenuActions.NEW_GAME, 0);
			}
		}).dimensions(cx + 4, y, 148, 20).build();
		fresh.active = control;
		addDrawableChild(fresh);
		y += 24;

		ButtonWidget restart = ButtonWidget.builder(Text.translatable("screen.fnaf_night_shift.restart"), b -> send(MenuActions.RESTART, 0))
				.dimensions(cx - 152, y, 148, 20).build();
		restart.active = control && (active || data.phase() == NightPhase.DEFEAT || data.phase() == NightPhase.VICTORY);
		addDrawableChild(restart);
		ButtonWidget abandon = ButtonWidget.builder(Text.translatable("screen.fnaf_night_shift.abandon"), b -> send(MenuActions.ABANDON, 0))
				.dimensions(cx + 4, y, 148, 20).build();
		abandon.active = control && active;
		addDrawableChild(abandon);
		y += 24;

		Difficulty diff = Difficulty.values()[Math.max(0, Math.min(data.difficulty(), Difficulty.values().length - 1))];
		ButtonWidget dif = ButtonWidget.builder(Text.translatable("screen.fnaf_night_shift.difficulty", Text.translatable(diff.translationKey())),
				b -> send(MenuActions.DIFFICULTY, data.difficulty() + 1)).dimensions(cx - 152, y, 148, 20).build();
		dif.active = data.admin() && !active;
		addDrawableChild(dif);

		if (!data.hasShelter()) {
			ButtonWidget gen = ButtonWidget.builder(Text.translatable("screen.fnaf_night_shift.generate"), b -> send(MenuActions.GENERATE_MAP, 0))
					.dimensions(cx + 4, y, 148, 20)
					.tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("screen.fnaf_night_shift.generate_tooltip")))
					.build();
			gen.active = data.admin();
			addDrawableChild(gen);
		} else {
			ButtonWidget up = ButtonWidget.builder(Text.translatable("screen.fnaf_night_shift.upgrade"), b -> send(MenuActions.UPGRADE_MAP, 0))
					.dimensions(cx + 4, y, 148, 20).build();
			up.active = data.admin() && data.needsUpgrade() && !active;
			addDrawableChild(up);
		}
		y += 30;
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.fnaf_night_shift.close"), b -> close()).dimensions(cx - 60, y, 120, 20).build());
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0xE00A0A0C);
		java.util.Random r = new java.util.Random(System.currentTimeMillis() / 80);
		for (int i = 0; i < 250; i++) {
			int x = r.nextInt(Math.max(1, width)), y = r.nextInt(Math.max(1, height));
			int g = 30 + r.nextInt(50);
			ctx.fill(x, y, x + 2, y + 1, 0x60000000 | (g << 16) | (g << 8) | g);
		}
		int cx = width / 2;
		int top = height / 2 - 122;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(cx, top, 0);
		ctx.getMatrices().scale(2.5f, 2.5f, 1f);
		ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("FNAF: NIGHT SHIFT"), 0, 0, 0xFFD02020);
		ctx.getMatrices().pop();

		int done = 0;
		for (int n = 1; n <= NightDefinition.MAX_NIGHT; n++) if (data.completed(n)) done++;
		ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.fnaf_night_shift.progress", done, NightDefinition.MAX_NIGHT,
				data.highestUnlocked(), data.totalCatches()), cx, top + 30, 0xFFC0C0C0);
		// barra de progreso
		int bw = 200;
		ctx.fill(cx - bw / 2, top + 42, cx + bw / 2, top + 46, 0xFF2A2A2A);
		ctx.fill(cx - bw / 2, top + 42, cx - bw / 2 + bw * done / NightDefinition.MAX_NIGHT, top + 46, 0xFFC9A227);

		Text status;
		if (data.phase().isActive()) status = Text.translatable("screen.fnaf_night_shift.status_active", data.activeNight());
		else if (!data.hasShelter()) status = Text.translatable("screen.fnaf_night_shift.status_no_map");
		else status = Text.translatable("screen.fnaf_night_shift.status_ready", data.stations(), data.cameras(), data.lockers());
		ctx.drawCenteredTextWithShadow(textRenderer, status, cx, top + 52, data.hasShelter() ? 0xFF7FD7FF : 0xFFFFD27F);
		if (!data.canControl()) {
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.fnaf_night_shift.no_permission"), cx, height / 2 - 54, 0xFFFF7070);
		} else {
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.fnaf_night_shift.choose_night"), cx, height / 2 - 54, 0xFFAAAAAA);
		}
		super.render(ctx, mouseX, mouseY, delta);
	}
}
