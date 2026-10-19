package com.fnaf.nightshift.client.screen;

import com.fnaf.nightshift.repair.MinigameLogic;
import com.fnaf.nightshift.repair.RepairType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Memorizar el código que aparece unos segundos en la terminal e introducirlo. */
public class CodeMinigameScreen extends MinigameScreen {
	private final int[] code;
	private final int showTicks;
	private final List<Integer> input = new ArrayList<>();

	public CodeMinigameScreen(BlockPos pos, RepairType type, long seed, int level) {
		super(pos, type, seed, level);
		this.code = MinigameLogic.code(seed);
		this.showTicks = MinigameLogic.codeDisplayTicks(level);
		this.panelH = 200;
	}

	@Override
	protected void init() {
		super.init();
		int bx = panelX + panelW / 2 - 52;
		int by = panelY + 76;
		for (int i = 1; i <= 9; i++) {
			int d = i;
			addDrawableChild(ButtonWidget.builder(Text.literal(String.valueOf(d)), b -> press(d))
					.dimensions(bx + ((i - 1) % 3) * 36, by + ((i - 1) / 3) * 22, 32, 20).build());
		}
		addDrawableChild(ButtonWidget.builder(Text.literal("⌫"), b -> {
			if (!input.isEmpty()) input.remove(input.size() - 1);
		}).dimensions(bx, by + 66, 32, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("0"), b -> press(0)).dimensions(bx + 36, by + 66, 32, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("OK"), b -> {
			if (input.size() == MinigameLogic.CODE_LENGTH) submit(input.stream().mapToInt(Integer::intValue).toArray());
		}).dimensions(bx + 72, by + 66, 32, 20).build());
	}

	private void press(int d) {
		if (age < showTicks) return;
		if (input.size() < MinigameLogic.CODE_LENGTH) input.add(d);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (chr >= '0' && chr <= '9') {
			press(chr - '0');
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 259 && !input.isEmpty()) { // retroceso
			input.remove(input.size() - 1);
			return true;
		}
		if ((keyCode == 257 || keyCode == 335) && input.size() == MinigameLogic.CODE_LENGTH) { // Enter
			submit(input.stream().mapToInt(Integer::intValue).toArray());
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		boolean show = age < showTicks;
		drawPanel(ctx, Text.translatable(show ? "minigame.fnaf_night_shift.code_memorize" : "minigame.fnaf_night_shift.code_enter"));
		int sx = panelX + panelW / 2 - 50;
		int sy = panelY + 36;
		ctx.fill(sx, sy, sx + 100, sy + 30, 0xFF0A1A0A);
		ctx.fill(sx + 2, sy + 2, sx + 98, sy + 28, 0xFF0F2A12);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < MinigameLogic.CODE_LENGTH; i++) {
			if (show) sb.append(code[i]);
			else sb.append(i < input.size() ? String.valueOf(input.get(i)) : "_");
			if (i < MinigameLogic.CODE_LENGTH - 1) sb.append(' ');
		}
		ctx.getMatrices().push();
		ctx.getMatrices().translate(width / 2f, sy + 9, 0);
		ctx.getMatrices().scale(2f, 2f, 1f);
		ctx.drawCenteredTextWithShadow(textRenderer, sb.toString(), 0, 0, show ? 0xFF6CFF6C : 0xFF4CD04C);
		ctx.getMatrices().pop();
		if (show) {
			int remaining = showTicks - age;
			ctx.fill(sx, sy + 32, sx + (int) (100 * remaining / (float) showTicks), sy + 34, 0xFF6CFF6C);
		}
		super.render(ctx, mouseX, mouseY, delta);
	}
}
