package com.fnaf.nightshift.client.screen;

import com.fnaf.nightshift.repair.MinigameLogic;
import com.fnaf.nightshift.repair.RepairType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Sustituir las piezas quemadas (ennegrecidas). Cambiar una pieza sana
 * cuenta como error. Consume un repuesto por pieza al confirmar.
 */
public class ReplacePartScreen extends MinigameScreen {
	private final boolean[] burnt;
	private final boolean[] replaced = new boolean[MinigameLogic.FUSE_SLOTS];

	public ReplacePartScreen(BlockPos pos, RepairType type, long seed, int level) {
		super(pos, type, seed, level);
		this.burnt = MinigameLogic.burntFuses(seed, level);
	}

	@Override
	protected void init() {
		super.init();
		addDrawableChild(ButtonWidget.builder(Text.translatable("minigame.fnaf_night_shift.confirm"), b -> {
			List<Integer> list = new ArrayList<>();
			for (int i = 0; i < replaced.length; i++) if (replaced[i]) list.add(i);
			submit(list.stream().mapToInt(Integer::intValue).toArray());
		}).dimensions(panelX + panelW / 2 - 40, panelY + panelH - 36, 80, 18).build());
	}

	private int slotX(int i) {
		return panelX + 22 + i * 38;
	}

	private int slotY() {
		return panelY + 48;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int count = 0;
		for (boolean b : burnt) if (b) count++;
		drawPanel(ctx, Text.translatable("minigame.fnaf_night_shift.replace", count));
		for (int i = 0; i < MinigameLogic.FUSE_SLOTS; i++) {
			int x = slotX(i), y = slotY();
			ctx.fill(x, y, x + 26, y + 70, 0xFF3A3A3A);
			ctx.fill(x + 3, y + 3, x + 23, y + 67, 0xFF151515);
			// contactos
			ctx.fill(x + 9, y + 6, x + 17, y + 12, 0xFFB08D3A);
			ctx.fill(x + 9, y + 58, x + 17, y + 64, 0xFFB08D3A);
			int body;
			if (replaced[i]) body = 0xFFDADADA;
			else if (burnt[i]) body = 0xFF4A3A30;
			else body = 0xFFB8B8B0;
			ctx.fill(x + 7, y + 12, x + 19, y + 58, body);
			// filamento
			if (burnt[i] && !replaced[i]) {
				ctx.fill(x + 12, y + 16, x + 14, y + 28, 0xFF1A1A1A);
				ctx.fill(x + 10, y + 32, x + 16, y + 38, 0xFF000000);
				ctx.fill(x + 12, y + 42, x + 14, y + 54, 0xFF1A1A1A);
			} else {
				ctx.fill(x + 12, y + 16, x + 14, y + 54, replaced[i] ? 0xFFE8C33A : 0xFF9A7A2A);
			}
			if (inside(mouseX, mouseY, x, y, 26, 70)) ctx.fill(x, y, x + 26, y + 70, 0x30FFFFFF);
		}
		super.render(ctx, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		for (int i = 0; i < MinigameLogic.FUSE_SLOTS; i++) {
			if (inside(mx, my, slotX(i), slotY(), 26, 70)) {
				replaced[i] = !replaced[i];
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
	}
}
