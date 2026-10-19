package com.fnaf.nightshift.client.screen;

import com.fnaf.nightshift.repair.MinigameLogic;
import com.fnaf.nightshift.repair.RepairType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;

/** Conectar cada cable de la izquierda con el terminal del mismo color. */
public class WireMinigameScreen extends MinigameScreen {
	private static final int[] COLORS = {0xFFD83A3A, 0xFF3A6BD8, 0xFFE8C33A, 0xFF3AB84A};
	private final int[] perm;
	private final int[] links = new int[MinigameLogic.WIRE_COUNT];
	private int selected = -1;

	public WireMinigameScreen(BlockPos pos, RepairType type, long seed, int level) {
		super(pos, type, seed, level);
		this.perm = MinigameLogic.wires(seed);
		Arrays.fill(links, -1);
	}

	@Override
	protected void init() {
		super.init();
		addDrawableChild(ButtonWidget.builder(Text.translatable("minigame.fnaf_night_shift.reset"), b -> {
			Arrays.fill(links, -1);
			selected = -1;
		}).dimensions(panelX + panelW / 2 - 40, panelY + panelH - 36, 80, 18).build());
	}

	private int nodeY(int i) {
		return panelY + 44 + i * 26;
	}

	private int leftX() {
		return panelX + 30;
	}

	private int rightX() {
		return panelX + panelW - 42;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		drawPanel(ctx, Text.translatable("minigame.fnaf_night_shift.wires"));
		for (int i = 0; i < MinigameLogic.WIRE_COUNT; i++) {
			int y = nodeY(i);
			// cable suelto pelado
			ctx.fill(panelX + 8, y + 3, leftX(), y + 7, COLORS[i]);
			ctx.fill(leftX(), y, leftX() + 12, y + 10, selected == i ? 0xFFFFFFFF : 0xFF8A8A8A);
			ctx.fill(leftX() + 2, y + 2, leftX() + 10, y + 8, COLORS[i]);
			// terminal derecho
			ctx.fill(rightX(), y, rightX() + 12, y + 10, 0xFF8A8A8A);
			ctx.fill(rightX() + 2, y + 2, rightX() + 10, y + 8, COLORS[perm[i]]);
			ctx.fill(rightX() + 12, y + 3, panelX + panelW - 8, y + 7, COLORS[perm[i]]);
		}
		for (int i = 0; i < MinigameLogic.WIRE_COUNT; i++) {
			if (links[i] >= 0) {
				drawLine(ctx, leftX() + 12, nodeY(i) + 5, rightX(), nodeY(links[i]) + 5, 4, COLORS[i]);
			}
		}
		if (selected >= 0) {
			drawLine(ctx, leftX() + 12, nodeY(selected) + 5, mouseX, mouseY, 3, COLORS[selected] & 0xA0FFFFFF);
		}
		super.render(ctx, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		for (int i = 0; i < MinigameLogic.WIRE_COUNT; i++) {
			if (inside(mx, my, leftX() - 4, nodeY(i) - 4, 20, 18)) {
				selected = i;
				return true;
			}
			if (selected >= 0 && inside(mx, my, rightX() - 4, nodeY(i) - 4, 20, 18)) {
				for (int k = 0; k < links.length; k++) if (links[k] == i) links[k] = -1;
				links[selected] = i;
				selected = -1;
				if (Arrays.stream(links).allMatch(l -> l >= 0)) submit(links.clone());
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
	}
}
