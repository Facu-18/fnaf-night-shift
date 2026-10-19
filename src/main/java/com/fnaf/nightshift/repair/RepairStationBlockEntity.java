package com.fnaf.nightshift.repair;

import com.fnaf.nightshift.ai.NoiseManager;
import com.fnaf.nightshift.block.FlickeringLightBlock;
import com.fnaf.nightshift.campaign.CampaignState;
import com.fnaf.nightshift.campaign.NightManager;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.network.ModPackets;
import com.fnaf.nightshift.registry.ModBlockEntities;
import com.fnaf.nightshift.registry.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Estado persistente de una instalación: estado, progreso, semilla del puzle
 * y nivel de dificultad con el que se averió.
 */
public class RepairStationBlockEntity extends BlockEntity {
	private boolean broken;
	private double progress;
	private long seed;
	private int level = 1;
	private long lastHoldTick;
	/** Nombre visible en el monitor (sólo cámaras). */
	private String camName = "";
	private int holdCounter;

	public RepairStationBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.REPAIR_STATION, pos, state);
	}

	public RepairType getRepairType() {
		return getCachedState().getBlock() instanceof RepairStationBlock b ? b.getRepairType() : RepairType.GENERATOR;
	}

	public boolean isBroken() {
		return broken;
	}

	public double getProgress() {
		return progress;
	}

	public long getSeed() {
		return seed;
	}

	public int getLevel() {
		return level;
	}

	public String getCamName() {
		return camName;
	}

	public void setCamName(String name) {
		camName = name == null ? "" : name;
		markDirty();
	}

	/** Avería la instalación para la noche actual con una nueva semilla. */
	public void breakStation(long newSeed, int puzzleLevel) {
		broken = true;
		progress = 0;
		seed = newSeed;
		level = Math.max(1, puzzleLevel);
		syncState();
		if (getRepairType() == RepairType.LIGHTING && world instanceof ServerWorld sw) {
			FlickeringLightBlock.setAreaFaulty(sw, pos, 12, true);
		}
	}

	/** Deja la instalación operativa sin contar como objetivo (inicio / reinicio). */
	public void resetRepaired() {
		broken = false;
		progress = 0;
		syncState();
		if (getRepairType() == RepairType.LIGHTING && world instanceof ServerWorld sw) {
			FlickeringLightBlock.setAreaFaulty(sw, pos, 12, false);
		}
	}

	private void syncState() {
		markDirty();
		if (world != null && !world.isClient) {
			BlockState state = getCachedState();
			if (state.contains(RepairStationBlock.BROKEN) && state.get(RepairStationBlock.BROKEN) != broken) {
				world.setBlockState(pos, state.with(RepairStationBlock.BROKEN, broken), 3);
			}
			world.updateListeners(pos, state, state, 3);
		}
	}

	// ------------------------------------------------------------------
	// Interacción
	// ------------------------------------------------------------------

	public void interact(ServerPlayerEntity player) {
		if (!(world instanceof ServerWorld sw)) return;
		RepairType type = getRepairType();

		if (!broken) {
			player.sendMessage(Text.translatable("message.fnaf_night_shift.station_ok", Text.translatable(type.translationKey())).formatted(Formatting.GREEN), true);
			return;
		}
		CampaignState state = CampaignState.get(sw.getServer());
		if (state.isCaught(player.getUuid())) {
			player.sendMessage(Text.translatable("message.fnaf_night_shift.caught_cannot_repair").formatted(Formatting.RED), true);
			return;
		}

		Item tool = type.tool();
		if (tool != null && !player.getMainHandStack().isOf(tool)) {
			player.sendMessage(Text.translatable("message.fnaf_night_shift.need_tool", Text.translatable(tool.getTranslationKey())).formatted(Formatting.YELLOW), true);
			return;
		}

		int needed = partsNeeded();
		Item part = type.part();
		if (part != null && countItem(player, part) < needed) {
			player.sendMessage(Text.translatable("message.fnaf_night_shift.need_parts", needed, Text.translatable(part.getTranslationKey())).formatted(Formatting.YELLOW), true);
			return;
		}

		if (type.minigame() == MinigameType.HOLD) {
			holdStep(sw, player);
		} else {
			ModPackets.sendOpenMinigame(player, pos, type, seed, level);
		}
	}

	private void holdStep(ServerWorld sw, ServerPlayerEntity player) {
		RepairType type = getRepairType();
		long now = sw.getTime();
		// Clic derecho mantenido se repite cada 4 ticks: 5 pasos por segundo.
		if (now - lastHoldTick < 3) return;
		lastHoldTick = now;
		double seconds = type.holdSeconds() * ModConfig.get().holdRepairTimeMultiplier * (0.85 + level * 0.05);
		progress = Math.min(1.0, progress + 1.0 / Math.max(1.0, seconds * 5.0));
		holdCounter++;
		markDirty();

		double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
		sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y - 0.2, z, 3, 0.3, 0.2, 0.3, 0.05);
		if (holdCounter % 3 == 0) {
			sw.playSound(null, pos, ModSounds.REPAIR_RATCHET, SoundCategory.BLOCKS, 0.8f, 0.9f + sw.random.nextFloat() * 0.2f);
		}
		if (holdCounter % 5 == 0) {
			NoiseManager.emit(sw, pos, type.noiseRadius(), NoiseManager.NoiseKind.REPAIR);
		}
		int pct = MathHelper.floor(progress * 100);
		player.sendMessage(Text.translatable("message.fnaf_night_shift.repair_progress", Text.translatable(type.translationKey()), progressBar(progress), pct).formatted(Formatting.AQUA), true);

		if (progress >= 1.0) {
			complete(player);
		}
	}

	private static String progressBar(double p) {
		int filled = (int) Math.round(p * 20);
		return "█".repeat(filled) + "░".repeat(20 - filled);
	}

	public int partsNeeded() {
		RepairType type = getRepairType();
		if (type.part() == null) return 0;
		if (type.minigame() == MinigameType.REPLACE_PART) return MinigameLogic.burntCount(seed, level);
		return type.partCount();
	}

	/** Respuesta de un minijuego recibida del cliente (ya en el hilo del servidor). */
	public void receiveAnswer(ServerPlayerEntity player, boolean gaveUp, int[] answer) {
		if (!(world instanceof ServerWorld sw) || !broken) return;
		if (player.squaredDistanceTo(pos.toCenterPos()) > 64) return;
		if (gaveUp) return;
		RepairType type = getRepairType();
		if (type.minigame() == MinigameType.HOLD) return;

		if (MinigameLogic.validate(type.minigame(), seed, level, answer)) {
			complete(player);
		} else {
			sw.playSound(null, pos, ModSounds.REPAIR_FAIL, SoundCategory.BLOCKS, 1.0f, 1.0f);
			NoiseManager.emit(sw, pos, Math.max(24, type.noiseRadius() * 2), NoiseManager.NoiseKind.ALARM);
			player.sendMessage(Text.translatable("message.fnaf_night_shift.repair_failed").formatted(Formatting.RED), true);
			// nueva semilla para que no se pueda reintentar la misma respuesta a ciegas
			seed = sw.random.nextLong();
			markDirty();
		}
	}

	private void complete(ServerPlayerEntity player) {
		if (!(world instanceof ServerWorld sw) || !broken) return;
		RepairType type = getRepairType();
		int needed = partsNeeded();
		Item part = type.part();
		if (part != null && needed > 0) {
			if (countItem(player, part) < needed) {
				player.sendMessage(Text.translatable("message.fnaf_night_shift.need_parts", needed, Text.translatable(part.getTranslationKey())).formatted(Formatting.YELLOW), true);
				return;
			}
			if (!player.isCreative()) removeItem(player, part, needed);
		}
		Item tool = type.tool();
		if (tool != null && !player.isCreative()) {
			ItemStack held = player.getMainHandStack();
			if (held.isOf(tool) && held.isDamageable()) {
				held.damage(1, player, p -> p.sendToolBreakStatus(Hand.MAIN_HAND));
			}
		}

		broken = false;
		progress = 1.0;
		syncState();

		double x = pos.getX() + 0.5, y = pos.getY() + 1.1, z = pos.getZ() + 0.5;
		sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 12, 0.4, 0.3, 0.4, 0.02);
		sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 20, 0.4, 0.4, 0.4, 0.2);
		sw.playSound(null, pos, ModSounds.REPAIR_SUCCESS, SoundCategory.BLOCKS, 1.0f, 1.0f);
		sw.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.4f, 1.6f);
		NoiseManager.emit(sw, pos, type.noiseRadius(), NoiseManager.NoiseKind.REPAIR);

		if (type == RepairType.LIGHTING) {
			FlickeringLightBlock.setAreaFaulty(sw, pos, 12, false);
		}
		player.sendMessage(Text.translatable("message.fnaf_night_shift.repair_done", Text.translatable(type.translationKey())).formatted(Formatting.GREEN), true);
		NightManager.onRepairCompleted(sw, pos, player);
	}

	private static int countItem(ServerPlayerEntity player, Item item) {
		if (player.isCreative()) return Integer.MAX_VALUE;
		int n = 0;
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack s = player.getInventory().getStack(i);
			if (s.isOf(item)) n += s.getCount();
		}
		return n;
	}

	private static void removeItem(ServerPlayerEntity player, Item item, int count) {
		for (int i = 0; i < player.getInventory().size() && count > 0; i++) {
			ItemStack s = player.getInventory().getStack(i);
			if (s.isOf(item)) {
				int take = Math.min(count, s.getCount());
				s.decrement(take);
				count -= take;
			}
		}
		player.getInventory().markDirty();
	}

	// ------------------------------------------------------------------
	// Persistencia y sincronización
	// ------------------------------------------------------------------

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		nbt.putBoolean("Broken", broken);
		nbt.putDouble("Progress", progress);
		nbt.putLong("Seed", seed);
		nbt.putInt("Level", level);
		if (!camName.isEmpty()) nbt.putString("CamName", camName);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		broken = nbt.getBoolean("Broken");
		progress = nbt.getDouble("Progress");
		seed = nbt.getLong("Seed");
		level = Math.max(1, nbt.getInt("Level"));
		camName = nbt.getString("CamName");
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt() {
		// La semilla no se envía al cliente hasta abrir el minijuego.
		NbtCompound nbt = new NbtCompound();
		nbt.putBoolean("Broken", broken);
		nbt.putDouble("Progress", progress);
		return nbt;
	}
}
