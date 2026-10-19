package com.fnaf.nightshift.campaign;

import com.fnaf.nightshift.block.FlickeringLightBlock;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.entity.AnimatronicEntity;
import com.fnaf.nightshift.entity.AnimatronicKind;
import com.fnaf.nightshift.entity.GoldenFreddyEntity;
import com.fnaf.nightshift.registry.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Eventos especiales y secretos. Arquitectura extensible: cada evento
 * implementa {@link SpecialEvent} y se registra en {@link #EVENTS}. Se
 * evalúan una vez por segundo durante la noche.
 */
public final class SpecialEvents {

	/** Contrato de un evento especial. */
	public interface SpecialEvent {
		String id();

		/** ¿Puede ocurrir este segundo? (condiciones de configuración, noche, azar...) */
		boolean canTrigger(ServerWorld world, CampaignState state, NightDefinition def);

		void trigger(ServerWorld world, CampaignState state, NightDefinition def);
	}

	public static final List<SpecialEvent> EVENTS = new ArrayList<>();

	static {
		register(new GoldenFreddyEvent());
		register(new BlackoutEvent());
		register(new DistantNoiseEvent());
	}

	private SpecialEvents() {
	}

	public static void register(SpecialEvent event) {
		EVENTS.add(event);
	}

	public static void tick(ServerWorld world, CampaignState state, NightDefinition def) {
		if (state.getGraceTicks() > 0) return;
		for (SpecialEvent e : EVENTS) {
			if (e.canTrigger(world, state, def)) e.trigger(world, state, def);
		}
	}

	private static List<ServerPlayerEntity> freeParticipants(ServerWorld world, CampaignState state) {
		List<ServerPlayerEntity> list = new ArrayList<>();
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (!p.isSpectator() && !p.isCreative() && !state.isCaught(p.getUuid()) && !state.isInShelter(p)) list.add(p);
		}
		return list;
	}

	/** Busca y lanza un evento por id (comando de pruebas). */
	public static boolean forceTrigger(ServerWorld world, String id) {
		CampaignState state = CampaignState.get(world.getServer());
		NightDefinition def = NightManager.definition(world.getServer());
		for (SpecialEvent e : EVENTS) {
			if (e.id().equals(id)) {
				e.trigger(world, state, def);
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------

	/**
	 * Golden Freddy aparece frente a un jugador que esté a oscuras.
	 * Condiciones: activado en config, noche >= goldenFreddyMinNight (o noche 7),
	 * probabilidad por minuto configurable y máximo una manifestación a la vez.
	 */
	static final class GoldenFreddyEvent implements SpecialEvent {
		@Override
		public String id() {
			return "golden_freddy";
		}

		@Override
		public boolean canTrigger(ServerWorld world, CampaignState state, NightDefinition def) {
			if (!def.goldenFreddy()) return false;
			double perSecond = ModConfig.get().goldenFreddyChancePerMinute / 60.0;
			if (world.random.nextDouble() >= perSecond) return false;
			for (Entity e : world.iterateEntities()) {
				if (e instanceof GoldenFreddyEntity) return false;
			}
			return true;
		}

		@Override
		public void trigger(ServerWorld world, CampaignState state, NightDefinition def) {
			List<ServerPlayerEntity> candidates = freeParticipants(world, state);
			if (candidates.isEmpty()) {
				for (ServerPlayerEntity p : world.getPlayers()) if (!p.isSpectator()) candidates.add(p);
			}
			if (candidates.isEmpty()) return;
			ServerPlayerEntity target = candidates.get(world.random.nextInt(candidates.size()));
			Vec3d look = target.getRotationVec(1.0f).multiply(1, 0, 1).normalize();
			for (int dist = 7; dist >= 3; dist--) {
				BlockPos pos = BlockPos.ofFloored(target.getPos().add(look.multiply(dist)));
				for (int dy = 1; dy >= -2; dy--) {
					BlockPos p = pos.up(dy);
					if (world.getBlockState(p).getCollisionShape(world, p).isEmpty()
							&& world.getBlockState(p.up()).getCollisionShape(world, p.up()).isEmpty()
							&& world.getBlockState(p.up(2)).getCollisionShape(world, p.up(2)).isEmpty()
							&& !world.getBlockState(p.down()).getCollisionShape(world, p.down()).isEmpty()) {
						AnimatronicEntity e = NightManager.spawn(world, AnimatronicKind.GOLDEN_FREDDY, p, state.getSession());
						if (e instanceof GoldenFreddyEntity g) {
							g.setFocus(target.getUuid(), 140);
							target.playSound(ModSounds.GOLDEN_WHISPER, SoundCategory.HOSTILE, 1.0f, 1.0f);
							target.sendMessage(Text.translatable("message.fnaf_night_shift.golden_hint").formatted(Formatting.GOLD, Formatting.ITALIC), true);
						}
						return;
					}
				}
			}
		}
	}

	/** Apagón: las luces cercanas a un jugador se apagan varios segundos. */
	static final class BlackoutEvent implements SpecialEvent {
		@Override
		public String id() {
			return "blackout";
		}

		@Override
		public boolean canTrigger(ServerWorld world, CampaignState state, NightDefinition def) {
			return def.powerOutages() && world.random.nextInt(def.night() == 7 ? 60 : 120) == 0;
		}

		@Override
		public void trigger(ServerWorld world, CampaignState state, NightDefinition def) {
			List<ServerPlayerEntity> candidates = freeParticipants(world, state);
			if (candidates.isEmpty()) return;
			ServerPlayerEntity target = candidates.get(world.random.nextInt(candidates.size()));
			FlickeringLightBlock.blackout(world, target.getBlockPos(), 24);
			for (ServerPlayerEntity p : world.getPlayers()) {
				if (p.squaredDistanceTo(target) < 40 * 40) {
					p.playSound(ModSounds.POWER_OUT, SoundCategory.BLOCKS, 1.0f, 1.0f);
					p.sendMessage(Text.translatable("message.fnaf_night_shift.blackout").formatted(Formatting.DARK_GRAY), true);
				}
			}
		}
	}

	/** Golpes metálicos lejanos para generar tensión (no afectan a la IA). */
	static final class DistantNoiseEvent implements SpecialEvent {
		@Override
		public String id() {
			return "distant_noise";
		}

		@Override
		public boolean canTrigger(ServerWorld world, CampaignState state, NightDefinition def) {
			return world.random.nextInt(45) == 0;
		}

		@Override
		public void trigger(ServerWorld world, CampaignState state, NightDefinition def) {
			for (ServerPlayerEntity p : freeParticipants(world, state)) {
				Vec3d off = new Vec3d(world.random.nextGaussian(), 0, world.random.nextGaussian()).normalize().multiply(12);
				world.playSound(null, p.getX() + off.x, p.getY() + 2, p.getZ() + off.z, ModSounds.AMBIENT_DISTANT, SoundCategory.AMBIENT,
						0.8f, 0.7f + world.random.nextFloat() * 0.5f);
			}
		}
	}
}
