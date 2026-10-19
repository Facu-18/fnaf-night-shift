package com.fnaf.nightshift.client.screen;

import com.fnaf.nightshift.client.ClientNightState;
import com.fnaf.nightshift.client.hud.NightHud;
import com.fnaf.nightshift.network.ModPackets;
import com.fnaf.nightshift.registry.ModSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Monitor de cámaras. Coloca la cámara del juego en la posición de cada
 * cámara de seguridad, de modo que se ve el mundo real con los
 * animatrónicos en su posición real (los sincroniza el servidor como
 * siempre). El cuerpo del jugador se queda en la oficina, expuesto.
 * Averías: cámara rota = sin señal; avería de seguridad = interferencias.
 */
public class CameraScreen extends Screen {
	public record Cam(BlockPos pos, Direction facing, String name, boolean broken) {
	}

	@Nullable
	private static CameraScreen current;

	private final BlockPos monitor;
	private List<Cam> cams;
	private boolean disrupted;
	private int index;
	private int age;
	private int switchStatic = 8;
	private float manualPan;
	private boolean dragging;
	private float burst;
	@Nullable
	private Entity camEntity;
	private boolean prevHudHidden;
	private double prevGamma;
	private final Random random = new Random();

	public CameraScreen(BlockPos monitor, List<Cam> cams, boolean disrupted) {
		super(Text.translatable("screen.fnaf_night_shift.cameras"));
		this.monitor = monitor;
		this.cams = new ArrayList<>(cams);
		this.disrupted = disrupted;
	}

	@Nullable
	public static CameraScreen current() {
		return current;
	}

	/** Actualización periódica del servidor (averías de cámaras). */
	public void update(List<Cam> list, boolean disrupted) {
		if (!list.isEmpty()) {
			BlockPos sel = cams.isEmpty() ? null : cams.get(index).pos();
			cams = new ArrayList<>(list);
			index = 0;
			for (int i = 0; i < cams.size(); i++) if (cams.get(i).pos().equals(sel)) index = i;
		}
		this.disrupted = disrupted;
	}

	@Override
	protected void init() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (camEntity == null && mc.world != null) {
			camEntity = EntityType.MARKER.create(mc.world);
			prevHudHidden = mc.options.hudHidden;
			prevGamma = mc.options.getGamma().getValue();
			// el mano/HUD vanilla se ocultan; la interfaz del mod se sigue dibujando
			mc.options.hudHidden = true;
			// leve "visión nocturna" de las cámaras: brillo al máximo mientras se mira
			mc.options.getGamma().setValue(1.0);
			mc.getSoundManager().play(PositionedSoundInstance.master(ModSounds.CAMERA_SWITCH, 1.0f));
		}
		current = this;
		place(true);
	}

	private Cam cam() {
		return cams.get(MathHelper.clamp(index, 0, cams.size() - 1));
	}

	private float autoPan() {
		// barrido lento de lado a lado, como las cámaras de la pizzería
		return (float) Math.sin(age * 0.025) * 40f;
	}

	private void place(boolean snap) {
		if (camEntity == null || cams.isEmpty()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		Cam c = cam();
		float baseYaw = c.facing().asRotation();
		float yaw = baseYaw + (dragging ? manualPan : autoPan() + manualPan);
		double x = c.pos().getX() + 0.5 + c.facing().getOffsetX() * 0.35;
		double y = c.pos().getY() + 0.25;
		double z = c.pos().getZ() + 0.5 + c.facing().getOffsetZ() * 0.35;
		float prevYaw = camEntity.getYaw();
		camEntity.refreshPositionAndAngles(x, y, z, yaw, 28f);
		if (!snap) camEntity.prevYaw = prevYaw;
		if (mc.getCameraEntity() != camEntity) mc.setCameraEntity(camEntity);
	}

	private void select(int i) {
		if (cams.isEmpty()) return;
		index = Math.floorMod(i, cams.size());
		manualPan = 0;
		switchStatic = 8;
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(ModSounds.CAMERA_SWITCH, 1.0f));
		place(true);
	}

	@Override
	public void tick() {
		age++;
		if (switchStatic > 0) switchStatic--;
		if (burst > 0) burst -= 0.1f;
		if (disrupted && random.nextInt(25) == 0) {
			burst = 1f;
			MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(ModSounds.CAMERA_STATIC, 1.0f, 0.4f));
		}
		if (ClientNightState.danger == 2 && random.nextInt(40) == 0) burst = Math.max(burst, 0.6f);
		place(false);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext context) {
		// sin fondo: se ve el mundo desde la cámara
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int w = width, h = height;
		Cam c = cams.isEmpty() ? null : cam();
		boolean noSignal = c == null || c.broken();
		float staticLevel = noSignal ? 1f : Math.max(switchStatic / 8f, burst);

		// grano permanente + líneas de barrido
		int grains = (int) (180 + staticLevel * 2400);
		for (int i = 0; i < grains; i++) {
			int x = random.nextInt(Math.max(1, w));
			int y = random.nextInt(Math.max(1, h));
			int g = 90 + random.nextInt(160);
			int a = noSignal ? 255 : (int) (40 + staticLevel * 180);
			ctx.fill(x, y, x + 2, y + 1, (a << 24) | (g << 16) | (g << 8) | g);
		}
		if (noSignal) ctx.fill(0, 0, w, h, 0x90101010);
		for (int y = (age * 2) % 4; y < h; y += 4) ctx.fill(0, y, w, y + 1, 0x18000000);
		ctx.fill(0, 0, w, h, 0x1430FF30);

		// marco y datos
		ctx.fill(8, 8, 9, h - 8, 0xA0FFFFFF);
		ctx.fill(w - 9, 8, w - 8, h - 8, 0xA0FFFFFF);
		ctx.fill(8, 8, w - 8, 9, 0xA0FFFFFF);
		ctx.fill(8, h - 9, w - 8, h - 8, 0xA0FFFFFF);
		if (c != null) ctx.drawTextWithShadow(textRenderer, Text.literal(c.name()), 16, h - 22, 0xFFFFFFFF);
		if ((age / 15) % 2 == 0) {
			ctx.fill(w - 52, 16, w - 46, 22, 0xFFE02020);
			ctx.drawTextWithShadow(textRenderer, "REC", w - 42, 15, 0xFFE0E0E0);
		}
		String clock = NightHud.clock(ClientNightState.ticksRemaining, ClientNightState.totalTicks);
		ctx.drawTextWithShadow(textRenderer, clock, w - 16 - textRenderer.getWidth(clock), h - 22, 0xFFFFD27F);
		if (noSignal) {
			ctx.getMatrices().push();
			ctx.getMatrices().translate(w / 2f, h / 2f - 8, 0);
			ctx.getMatrices().scale(2f, 2f, 1f);
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.fnaf_night_shift.no_signal"), 0, 0, 0xFFFFFFFF);
			ctx.getMatrices().pop();
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.fnaf_night_shift.no_signal_hint"), w / 2, h / 2 + 18, 0xFFC0C0C0);
		} else if (disrupted) {
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.fnaf_night_shift.interference"), w / 2, 16, 0xFFFF8080);
		}
		if (!ClientNightState.lurking.isEmpty()) {
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("hud.fnaf_night_shift.lurk_warning"), w / 2, 30, 0xFFFF4040);
		}
		renderMap(ctx, mouseX, mouseY);
		ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.fnaf_night_shift.camera_help"), w / 2, h - 36, 0xA0FFFFFF);
	}

	// --- minimapa ---
	private int mapX, mapY, mapW = 150, mapH = 110;
	private int minX, minZ, maxX, maxZ;

	private void computeBounds() {
		minX = monitor.getX();
		maxX = minX;
		minZ = monitor.getZ();
		maxZ = minZ;
		for (Cam c : cams) {
			minX = Math.min(minX, c.pos().getX());
			maxX = Math.max(maxX, c.pos().getX());
			minZ = Math.min(minZ, c.pos().getZ());
			maxZ = Math.max(maxZ, c.pos().getZ());
		}
		mapX = width - mapW - 16;
		mapY = height - mapH - 30;
	}

	private int mx(int x) {
		return mapX + 6 + (int) ((x - minX) / (float) Math.max(1, maxX - minX) * (mapW - 12));
	}

	private int mz(int z) {
		return mapY + 6 + (int) ((z - minZ) / (float) Math.max(1, maxZ - minZ) * (mapH - 12));
	}

	private void renderMap(DrawContext ctx, int mouseX, int mouseY) {
		computeBounds();
		ctx.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0x90000000);
		ctx.fill(mapX, mapY, mapX + mapW, mapY + 1, 0xFF808080);
		int ox = mx(monitor.getX()), oz = mz(monitor.getZ());
		ctx.fill(ox - 3, oz - 3, ox + 3, oz + 3, 0xFF7FD7FF);
		ctx.drawTextWithShadow(textRenderer, Text.translatable("screen.fnaf_night_shift.you"), ox + 5, oz - 4, 0xFF7FD7FF);
		for (int i = 0; i < cams.size(); i++) {
			Cam c = cams.get(i);
			int x = mx(c.pos().getX()), z = mz(c.pos().getZ());
			boolean sel = i == index;
			int col = c.broken() ? 0xFFD03030 : sel ? 0xFF40FF40 : 0xFFC0C0C0;
			ctx.fill(x - 4, z - 3, x + 4, z + 3, sel ? 0xFF205020 : 0xFF202020);
			ctx.fill(x - 3, z - 2, x + 3, z + 2, col);
			if (sel || (Math.abs(mouseX - x) < 5 && Math.abs(mouseY - z) < 4)) {
				ctx.drawTextWithShadow(textRenderer, c.name(), Math.min(x + 6, width - 10 - textRenderer.getWidth(c.name())), z - 4, 0xFFFFFFFF);
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		computeBounds();
		for (int i = 0; i < cams.size(); i++) {
			Cam c = cams.get(i);
			if (Math.abs(mouseX - mx(c.pos().getX())) <= 5 && Math.abs(mouseY - mz(c.pos().getZ())) <= 4) {
				select(i);
				return true;
			}
		}
		dragging = true;
		manualPan += autoPan();
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (dragging) {
			dragging = false;
			manualPan -= autoPan();
		}
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		manualPan = MathHelper.clamp(manualPan + (float) deltaX * 0.5f, -70f, 70f);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 262 || keyCode == 68) { // derecha / D
			select(index + 1);
			return true;
		}
		if (keyCode == 263 || keyCode == 65) { // izquierda / A
			select(index - 1);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void removed() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) mc.setCameraEntity(mc.player);
		mc.options.hudHidden = prevHudHidden;
		mc.options.getGamma().setValue(prevGamma);
		camEntity = null;
		current = null;
		if (mc.getNetworkHandler() != null && ClientPlayNetworking.canSend(ModPackets.CAMERA_STOP)) {
			ClientPlayNetworking.send(ModPackets.CAMERA_STOP, PacketByteBufs.create());
		}
	}
}
