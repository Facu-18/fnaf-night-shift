package com.fnaf.nightshift.client.screen;

import com.fnaf.nightshift.network.ModPackets;
import com.fnaf.nightshift.repair.RepairType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Base de los minijuegos de reparación. No pausa el juego: mientras reparás,
 * los animatrónicos siguen moviéndose. Se cierra si te alejás demasiado.
 */
public abstract class MinigameScreen extends Screen {
	protected final BlockPos pos;
	protected final RepairType type;
	protected final long seed;
	protected final int level;
	protected int panelX, panelY, panelW = 260, panelH = 180;
	protected int age;
	private boolean sent;

	protected MinigameScreen(BlockPos pos, RepairType type, long seed, int level) {
		super(Text.translatable(type.translationKey()));
		this.pos = pos;
		this.type = type;
		this.seed = seed;
		this.level = level;
	}

	@Override
	protected void init() {
		panelX = (width - panelW) / 2;
		panelY = (height - panelH) / 2;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void tick() {
		age++;
		if (client != null && client.player != null && client.player.squaredDistanceTo(Vec3d.ofCenter(pos)) > 49) {
			close();
		}
	}

	protected void submit(int[] answer) {
		send(false, answer);
		sent = true;
		if (client != null) client.setScreen(null);
	}

	@Override
	public void close() {
		if (!sent) send(true, new int[0]);
		sent = true;
		super.close();
	}

	private void send(boolean gaveUp, int[] answer) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		buf.writeBoolean(gaveUp);
		buf.writeVarInt(answer.length);
		for (int a : answer) buf.writeVarInt(a);
		ClientPlayNetworking.send(ModPackets.MINIGAME_ANSWER, buf);
	}

	protected void drawPanel(DrawContext ctx, Text subtitle) {
		ctx.fill(0, 0, width, height, 0x70000000);
		ctx.fill(panelX - 4, panelY - 4, panelX + panelW + 4, panelY + panelH + 4, 0xFF3A3A3A);
		ctx.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF6B6B6B);
		ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1B1D1F);
		// tornillos en las esquinas
		for (int[] c : new int[][]{{panelX + 4, panelY + 4}, {panelX + panelW - 7, panelY + 4}, {panelX + 4, panelY + panelH - 7}, {panelX + panelW - 7, panelY + panelH - 7}}) {
			ctx.fill(c[0], c[1], c[0] + 3, c[1] + 3, 0xFF9A9A9A);
		}
		// franja de peligro
		for (int i = 0; i < panelW; i += 10) {
			ctx.fill(panelX + i, panelY + panelH - 12, panelX + Math.min(panelW, i + 5), panelY + panelH - 8, 0xFFE8C33A);
		}
		ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelY + 8, 0xFFFFD27F);
		ctx.drawCenteredTextWithShadow(textRenderer, subtitle, width / 2, panelY + 20, 0xFFB0B0B0);
		ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("minigame.fnaf_night_shift.esc"), width / 2, panelY + panelH + 8, 0xFF808080);
	}

	protected static void drawLine(DrawContext ctx, int x1, int y1, int x2, int y2, int thickness, int color) {
		int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
		for (int i = 0; i <= steps; i++) {
			float t = steps == 0 ? 0 : i / (float) steps;
			int x = Math.round(x1 + (x2 - x1) * t);
			int y = Math.round(y1 + (y2 - y1) * t);
			ctx.fill(x - thickness / 2, y - thickness / 2, x + (thickness + 1) / 2, y + (thickness + 1) / 2, color);
		}
	}

	protected static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
