package com.fnaf.nightshift.campaign;

import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.network.ModPackets;
import com.fnaf.nightshift.world.FacilityGenerator;
import com.fnaf.nightshift.world.FacilityUpgrade;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * Acciones del menú de noches. Reutilizan la misma lógica que los comandos
 * (NightManager / CampaignState): no hay un segundo sistema de progresión.
 */
public final class MenuActions {
	public static final int NEW_GAME = 0;
	public static final int CONTINUE = 1;
	public static final int START_NIGHT = 2;
	public static final int RESTART = 3;
	public static final int ABANDON = 4;
	public static final int DIFFICULTY = 5;
	public static final int GENERATE_MAP = 6;
	public static final int UPGRADE_MAP = 7;

	private MenuActions() {
	}

	/** ¿Puede este jugador iniciar o reiniciar noches? */
	public static boolean canControl(ServerPlayerEntity p) {
		return isAdmin(p) || ModConfig.get().playersCanControlNights;
	}

	/** Anfitrión de la partida o jugador con permisos (necesario para modificar el mapa o la dificultad). */
	public static boolean isAdmin(ServerPlayerEntity p) {
		MinecraftServer server = p.getServer();
		return p.hasPermissionLevel(2) || (server != null && server.isHost(p.getGameProfile()));
	}

	public static void handle(ServerPlayerEntity player, int action, int arg) {
		MinecraftServer server = player.getServer();
		if (server == null) return;
		CampaignState s = CampaignState.get(server);
		boolean admin = isAdmin(player);
		if (!canControl(player) && action != DIFFICULTY) {
			deny(player);
			return;
		}
		switch (action) {
			case NEW_GAME -> {
				NightManager.stopNight(server);
				s.resetProgress();
				NightManager.invalidateCache();
				NightManager.startNight(server, 1, player);
			}
			case CONTINUE -> NightManager.startNight(server, s.getCurrentNight(), player);
			case START_NIGHT -> {
				int night = Math.max(1, Math.min(NightDefinition.MAX_NIGHT, arg));
				if (night > s.getHighestUnlocked() && !admin) {
					player.sendMessage(Text.translatable("message.fnaf_night_shift.night_locked", night).formatted(Formatting.RED), false);
					break;
				}
				s.setCurrentNight(night);
				NightManager.startNight(server, night, player);
			}
			case RESTART -> NightManager.restartNight(server, player);
			case ABANDON -> NightManager.stopNight(server);
			case DIFFICULTY -> {
				if (!admin) {
					deny(player);
					break;
				}
				Difficulty[] all = Difficulty.values();
				s.setDifficulty(all[Math.floorMod(arg, all.length)]);
				NightManager.invalidateCache();
			}
			case GENERATE_MAP -> {
				if (!admin) {
					deny(player);
					break;
				}
				ServerWorld world = player.getServerWorld();
				BlockPos origin = player.getBlockPos().down();
				FacilityGenerator.Report report = FacilityGenerator.check(world, origin);
				if (!report.ok()) {
					player.sendMessage(Text.translatable("command.fnaf_night_shift.map_blocked", report.blocking(),
							report.firstBlocked() == null ? "-" : report.firstBlocked().toShortString()).formatted(Formatting.RED), false);
					break;
				}
				FacilityGenerator.Result r = FacilityGenerator.generate(world, origin);
				player.sendMessage(Text.translatable("command.fnaf_night_shift.map_done", r.blocks(), r.stations(), r.nodes(),
						r.shelter().toShortString()).formatted(Formatting.GREEN), false);
				NightManager.teleportToShelter(player, world, r.shelter());
			}
			case UPGRADE_MAP -> {
				if (!admin) {
					deny(player);
					break;
				}
				int changes = FacilityUpgrade.upgradeExisting(server);
				player.sendMessage(Text.translatable(changes >= 0 ? "message.fnaf_night_shift.upgrade_done" : "message.fnaf_night_shift.upgrade_unknown",
						Math.max(0, changes)).formatted(changes >= 0 ? Formatting.GREEN : Formatting.YELLOW), false);
			}
			default -> {
			}
		}
		ModPackets.sendMenu(player);
	}

	private static void deny(ServerPlayerEntity p) {
		p.sendMessage(Text.translatable("message.fnaf_night_shift.menu_denied").formatted(Formatting.RED), true);
	}
}
