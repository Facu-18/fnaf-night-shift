package com.fnaf.nightshift.client.screen;

import com.fnaf.nightshift.registry.ModSounds;
import com.fnaf.nightshift.repair.MinigameLogic;
import com.fnaf.nightshift.repair.RepairType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Observar la secuencia de interruptores y repetirla. */
public class SequenceMinigameScreen extends MinigameScreen {
	private static final int STEP = 14;
	private final int[] sequence;
	private final List<Integer> input = new ArrayList<>();
	private int pressedFlash = -1;
	private int pressedTicks;

	public SequenceMinigameScreen(BlockPos pos, RepairType type, long seed, int level) {
		super(pos, type, seed, level);
		this.sequence = MinigameLogic.sequence(seed, level);
	}

	private boolean showing() {
		return age < 20 + sequence.length * STEP;
	}

	private int litDuringShow() {
		int t = age - 20;
		if (t < 0) return -1;
		int idx = t / STEP;
		if (idx >= sequence.length || t % STEP > STEP - 4) return -1;
		return sequence[idx];
	}

	private int btnX(int i) {
		return panelX + 40 + (i % 3) * 64;
	}

	private int btnY(int i) {
		return panelY + 46 + (i / 3) * 56;
	}

	@Override
	public void tick() {
		super.tick();
		if (showing()) {
			int t = age - 20;
			if (t >= 0 && t % STEP == 0 && client != null) {
				client.getSoundManager().play(PositionedSoundInstance.master(ModSounds.FLASHLIGHT_CLICK, 0.8f + sequence[t / STEP] * 0.1f));
			}
		}
		if (pressedTicks > 0) pressedTicks--;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		Text sub = showing() ? Text.translatable("minigame.fnaf_night_shift.sequence_watch")
				: Text.translatable("minigame.fnaf_night_shift.sequence_repeat", input.size(), sequence.length);
		drawPanel(ctx, sub);
		int lit = showing() ? litDuringShow() : (pressedTicks > 0 ? pressedFlash : -1);
		for (int i = 0; i < MinigameLogic.SEQUENCE_BUTTONS; i++) {
			int x = btnX(i), y = btnY(i);
			boolean on = lit == i;
			ctx.fill(x, y, x + 50, y + 44, 0xFF4A4A4A);
			ctx.fill(x + 2, y + 2, x + 48, y + 42, 0xFF2A2A2A);
			// palanca
			ctx.fill(x + 21, on ? y + 6 : y + 22, x + 29, on ? y + 22 : y + 38, on ? 0xFFE0E0E0 : 0xFF9A9A9A);
			// led
			ctx.fill(x + 4, y + 4, x + 10, y + 10, on ? 0xFF4CFF4C : 0xFF203020);
			ctx.drawTextWithShadow(textRenderer, String.valueOf(i + 1), x + 38, y + 4, 0xFF808080);
		}
		super.render(ctx, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (showing()) return true;
		for (int i = 0; i < MinigameLogic.SEQUENCE_BUTTONS; i++) {
			if (inside(mx, my, btnX(i), btnY(i), 50, 44)) {
				input.add(i);
				pressedFlash = i;
				pressedTicks = 6;
				if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(ModSounds.FLASHLIGHT_CLICK, 0.8f + i * 0.1f));
				if (input.size() >= sequence.length) {
					submit(input.stream().mapToInt(Integer::intValue).toArray());
				}
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
	}
}
