package com.fnaf.nightshift.campaign;

import com.fnaf.nightshift.ai.AiState;
import com.fnaf.nightshift.ai.NoiseManager;
import com.fnaf.nightshift.block.SecurityDoorBlock;
import com.fnaf.nightshift.entity.AnimatronicEntity;
import com.fnaf.nightshift.registry.ModSounds;
import com.fnaf.nightshift.repair.RepairType;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La oficina (el antiguo refugio) ya no es totalmente segura. Este gestor
 * localiza sus puertas de seguridad, lleva la cuenta de los golpes que
 * reciben y las fuerza cuando ceden. Las puertas cerradas siguen siendo la
 * defensa principal: los animatrónicos sólo entran por puertas abiertas o
 * forzadas, llegando por las rutas del mapa.
 */
public final class OfficeManager {
	/**
	 * @param lower    mitad inferior de la puerta
	 * @param outward  dirección desde la oficina hacia el pasillo
	 * @param approach punto en el pasillo donde esperan los animatrónicos
	 */
	public record OfficeDoor(BlockPos lower, Direction outward, BlockPos approach, String side) {
	}

	private static List<OfficeDoor> cached = List.of();
	@Nullable
	private static BlockPos cachedFor;
	private static long cachedAt = -1000;
	private static final Map<BlockPos, Integer> damage = new HashMap<>();
	/** Puertas atascadas sin avería asociada: se liberan solas (tick del servidor en que se liberan). */
	private static final Map<BlockPos, Long> autoUnjam = new HashMap<>();

	private OfficeManager() {
	}

	public static void reset() {
		damage.clear();
		autoUnjam.clear();
		cachedAt = -1000;
	}

	public static List<OfficeDoor> doors(ServerWorld world, CampaignState state) {
		BlockPos shelter = state.getShelter(world);
		if (shelter == null) return List.of();
		long now = world.getTime();
		if (shelter.equals(cachedFor) && now - cachedAt < 200) return cached;
		List<OfficeDoor> list = new ArrayList<>();
		int r = state.shelterRadius() + 3;
		for (BlockPos p : BlockPos.iterate(shelter.add(-r, -1, -r), shelter.add(r, 3, r))) {
			BlockState s = world.getBlockState(p);
			if (s.getBlock() instanceof SecurityDoorBlock && s.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				int dx = p.getX() - shelter.getX();
				int dz = p.getZ() - shelter.getZ();
				Direction out = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
				list.add(new OfficeDoor(p.toImmutable(), out, p.offset(out, 2).toImmutable(), out.asString()));
			}
		}
		cached = List.copyOf(list);
		cachedFor = shelter;
		cachedAt = now;
		return cached;
	}

	@Nullable
	public static OfficeDoor nearestDoor(ServerWorld world, CampaignState state, BlockPos pos, double maxDist) {
		OfficeDoor best = null;
		double bestD = maxDist * maxDist;
		for (OfficeDoor d : doors(world, state)) {
			double dist = d.lower().getSquaredDistance(pos);
			if (dist < bestD) {
				bestD = dist;
				best = d;
			}
		}
		return best;
	}

	public static boolean isOpen(ServerWorld world, OfficeDoor door) {
		BlockState s = world.getBlockState(door.lower());
		return !(s.getBlock() instanceof DoorBlock) || s.get(DoorBlock.OPEN);
	}

	/** Golpe de un animatrónico contra una puerta cerrada. Devuelve true si la puerta cedió. */
	public static boolean bang(ServerWorld world, CampaignState state, NightDefinition def, OfficeDoor door, AnimatronicEntity mob, int amount) {
		BlockPos p = door.lower();
		world.playSound(null, p, ModSounds.DOOR_BANG, SoundCategory.BLOCKS, 1.4f, 0.8f + world.random.nextFloat() * 0.3f);
		world.spawnParticles(ParticleTypes.CRIT, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 6, 0.3, 0.5, 0.3, 0.1);
		NoiseManager.emit(world, p, 10, NoiseManager.NoiseKind.DOOR);
		int dmg = damage.merge(p, amount, Integer::sum);
		if (dmg >= def.doorStrength()) {
			force(world, state, def, door, mob);
			return true;
		}
		return false;
	}

	/** La puerta cede: se abre y queda atascada hasta reparar un control de puerta automática. */
	public static void force(ServerWorld world, CampaignState state, NightDefinition def, OfficeDoor door, @Nullable AnimatronicEntity by) {
		BlockState s = world.getBlockState(door.lower());
		if (s.getBlock() instanceof DoorBlock d) d.setOpen(null, world, s, door.lower(), true);
		damage.remove(door.lower());
		state.getJammedDoors().add(door.lower());
		state.markDirty();
		world.playSound(null, door.lower(), ModSounds.DOOR_SLAM, SoundCategory.BLOCKS, 2.0f, 0.6f);
		world.playSound(null, door.lower(), ModSounds.MALFUNCTION, SoundCategory.BLOCKS, 1.0f, 1.0f);
		// Si queda tiempo, el arreglo es una avería obligatoria; si no, la puerta se libera sola.
		boolean linked = BreakdownManager.triggerOfType(world, state, def, RepairType.AUTO_DOOR, "door:" + door.lower().asLong());
		if (!linked) autoUnjam.put(door.lower(), world.getTime() + 45 * 20);
		for (ServerPlayerEntity p : NightManager.playersIn(world.getServer(), state)) {
			p.sendMessage(Text.translatable("message.fnaf_night_shift.door_forced",
					Text.translatable("direction.fnaf_night_shift." + door.side())).formatted(Formatting.DARK_RED), false);
		}
	}

	public static void unjam(CampaignState state, BlockPos lower) {
		state.getJammedDoors().remove(lower);
		autoUnjam.remove(lower);
		state.markDirty();
	}

	public static void tick(ServerWorld world, CampaignState state) {
		if (autoUnjam.isEmpty()) return;
		long now = world.getTime();
		autoUnjam.entrySet().removeIf(e -> {
			if (now >= e.getValue()) {
				state.getJammedDoors().remove(e.getKey());
				state.markDirty();
				return true;
			}
			return false;
		});
	}

	/** Jugadores participantes, libres, dentro de la oficina. */
	public static List<PlayerEntity> playersInOffice(ServerWorld world, CampaignState state) {
		List<PlayerEntity> list = new ArrayList<>();
		for (PlayerEntity p : world.getPlayers()) {
			if (p.isSpectator() || p.isCreative() || !p.isAlive() || state.isCaught(p.getUuid())) continue;
			if (state.isInShelter(p)) list.add(p);
		}
		return list;
	}

	/** Lados de las puertas donde hay un animatrónico acechando (para la interfaz). */
	public static Set<String> lurkingSides(ServerWorld world, CampaignState state) {
		Set<String> sides = new HashSet<>();
		BlockPos shelter = state.getShelter(world);
		if (shelter == null) return sides;
		for (AnimatronicEntity a : world.getEntitiesByClass(AnimatronicEntity.class, new Box(shelter).expand(state.shelterRadius() + 8), e -> true)) {
			if (a.getAi().getState() == AiState.LURK && a.getAi().getLurkDoor() != null) sides.add(a.getAi().getLurkDoor().side());
		}
		return sides;
	}
}
