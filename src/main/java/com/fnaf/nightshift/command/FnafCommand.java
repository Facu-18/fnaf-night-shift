package com.fnaf.nightshift.command;

import com.fnaf.nightshift.campaign.CampaignState;
import com.fnaf.nightshift.campaign.Difficulty;
import com.fnaf.nightshift.campaign.NightDefinition;
import com.fnaf.nightshift.campaign.NightManager;
import com.fnaf.nightshift.campaign.SpecialEvents;
import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.entity.AnimatronicEntity;
import com.fnaf.nightshift.entity.AnimatronicKind;
import com.fnaf.nightshift.repair.RepairStationBlockEntity;
import com.fnaf.nightshift.world.FacilityGenerator;
import com.fnaf.nightshift.world.FacilityUpgrade;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.Locale;

/**
 * /fnaf — comandos de administración y pruebas (nivel de permiso 2).
 */
public final class FnafCommand {
	private static final SuggestionProvider<ServerCommandSource> DIFFICULTIES = (ctx, b) ->
			CommandSource.suggestMatching(Arrays.stream(Difficulty.values()).map(d -> d.name().toLowerCase(Locale.ROOT)), b);
	private static final SuggestionProvider<ServerCommandSource> ANIMATRONICS = (ctx, b) ->
			CommandSource.suggestMatching(Arrays.stream(AnimatronicKind.values()).filter(k -> k != AnimatronicKind.ANY).map(AnimatronicKind::id), b);
	private static final SuggestionProvider<ServerCommandSource> EVENTS = (ctx, b) ->
			CommandSource.suggestMatching(SpecialEvents.EVENTS.stream().map(SpecialEvents.SpecialEvent::id), b);

	private FnafCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("fnaf")
				.then(CommandManager.literal("status").executes(FnafCommand::status))
				.then(CommandManager.literal("night").requires(s -> s.hasPermissionLevel(2))
						.then(CommandManager.literal("start")
								.executes(ctx -> start(ctx, CampaignState.get(ctx.getSource().getServer()).getCurrentNight()))
								.then(CommandManager.argument("night", IntegerArgumentType.integer(1, NightDefinition.MAX_NIGHT))
										.executes(ctx -> start(ctx, IntegerArgumentType.getInteger(ctx, "night")))))
						.then(CommandManager.literal("stop").executes(ctx -> {
							NightManager.stopNight(ctx.getSource().getServer());
							ctx.getSource().sendFeedback(() -> Text.translatable("command.fnaf_night_shift.stopped"), true);
							return 1;
						}))
						.then(CommandManager.literal("restart").executes(ctx -> {
							NightManager.restartNight(ctx.getSource().getServer(), ctx.getSource().getPlayer());
							ctx.getSource().sendFeedback(() -> Text.translatable("command.fnaf_night_shift.restarted"), true);
							return 1;
						}))
						.then(CommandManager.literal("set")
								.then(CommandManager.argument("night", IntegerArgumentType.integer(1, NightDefinition.MAX_NIGHT))
										.executes(ctx -> {
											int n = IntegerArgumentType.getInteger(ctx, "night");
											CampaignState.get(ctx.getSource().getServer()).setCurrentNight(n);
											NightManager.invalidateCache();
											ctx.getSource().sendFeedback(() -> Text.translatable("command.fnaf_night_shift.night_set", n), true);
											return 1;
										})))
						.then(CommandManager.literal("time")
								.then(CommandManager.argument("seconds", IntegerArgumentType.integer(1, 7200))
										.executes(ctx -> {
											CampaignState s = CampaignState.get(ctx.getSource().getServer());
											s.setTicksRemaining(IntegerArgumentType.getInteger(ctx, "seconds") * 20);
											s.markDirty();
											ctx.getSource().sendFeedback(() -> Text.translatable("command.fnaf_night_shift.time_set"), true);
											return 1;
										}))))
				.then(CommandManager.literal("difficulty").requires(s -> s.hasPermissionLevel(2))
						.then(CommandManager.argument("level", StringArgumentType.word()).suggests(DIFFICULTIES)
								.executes(ctx -> {
									Difficulty d = Difficulty.parse(StringArgumentType.getString(ctx, "level"), null);
									if (d == null) {
										ctx.getSource().sendError(Text.translatable("command.fnaf_night_shift.bad_difficulty"));
										return 0;
									}
									CampaignState.get(ctx.getSource().getServer()).setDifficulty(d);
									NightManager.invalidateCache();
									ctx.getSource().sendFeedback(() -> Text.translatable("command.fnaf_night_shift.difficulty_set", Text.translatable(d.translationKey())), true);
									return 1;
								})))
				.then(CommandManager.literal("summon").requires(s -> s.hasPermissionLevel(2))
						.then(CommandManager.argument("animatronic", StringArgumentType.word()).suggests(ANIMATRONICS)
								.executes(ctx -> summon(ctx, BlockPos.ofFloored(ctx.getSource().getPosition())))
								.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
										.executes(ctx -> summon(ctx, BlockPosArgumentType.getLoadedBlockPos(ctx, "pos"))))))
				.then(CommandManager.literal("despawn").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
					int n = NightManager.removeAnimatronics(ctx.getSource().getWorld(), a -> true);
					ctx.getSource().sendFeedback(() -> Text.translatable("command.fnaf_night_shift.despawned", n), true);
					return n;
				}))
				.then(CommandManager.literal("reset").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
					NightManager.stopNight(ctx.getSource().getServer());
					CampaignState.get(ctx.getSource().getServer()).resetProgress();
					NightManager.invalidateCache();
					ctx.getSource().sendFeedback(() -> Text.translatable("command.fnaf_night_shift.reset"), true);
					return 1;
				}))
				.then(CommandManager.literal("kit").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
					NightManager.giveStarterKit(ctx.getSource().getPlayerOrThrow());
					return 1;
				}))
				.then(CommandManager.literal("event").requires(s -> s.hasPermissionLevel(2))
						.then(CommandManager.argument("id", StringArgumentType.word()).suggests(EVENTS).executes(ctx -> {
							boolean ok = SpecialEvents.forceTrigger(ctx.getSource().getWorld(), StringArgumentType.getString(ctx, "id"));
							if (!ok) ctx.getSource().sendError(Text.translatable("command.fnaf_night_shift.bad_event"));
							return ok ? 1 : 0;
						})))
				.then(CommandManager.literal("repairs").requires(s -> s.hasPermissionLevel(2))
						.then(CommandManager.literal("list").executes(FnafCommand::listRepairs))
						.then(CommandManager.literal("complete").executes(FnafCommand::completeRepairs)))
				.then(CommandManager.literal("config").requires(s -> s.hasPermissionLevel(2))
						.then(CommandManager.literal("reload").executes(ctx -> {
							ModConfig.load();
							NightManager.invalidateCache();
							ctx.getSource().sendFeedback(() -> Text.translatable("command.fnaf_night_shift.config_reloaded"), true);
							return 1;
						})))
				.then(CommandManager.literal("map").requires(s -> s.hasPermissionLevel(2))
						.then(CommandManager.literal("check").executes(ctx -> map(ctx, false)))
						.then(CommandManager.literal("generate").executes(ctx -> map(ctx, true)))
						.then(CommandManager.literal("upgrade").executes(ctx -> {
							int n = FacilityUpgrade.upgradeExisting(ctx.getSource().getServer());
							ctx.getSource().sendFeedback(() -> Text.translatable(n >= 0 ? "message.fnaf_night_shift.upgrade_done"
									: "message.fnaf_night_shift.upgrade_unknown", Math.max(0, n)), true);
							return Math.max(0, n);
						})))
				.then(CommandManager.literal("vent").requires(s -> s.hasPermissionLevel(2))
						.then(CommandManager.argument("seconds", IntegerArgumentType.integer(0, 3600)).executes(ctx -> {
							CampaignState st = CampaignState.get(ctx.getSource().getServer());
							st.setVentTicks(IntegerArgumentType.getInteger(ctx, "seconds") * 20);
							return 1;
						})))
				.then(CommandManager.literal("breakdown").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
					CampaignState st = CampaignState.get(ctx.getSource().getServer());
					ServerWorld w = st.getFacilityWorld(ctx.getSource().getServer());
					if (w == null || !st.getPhase().isActive()) return 0;
					st.setNextExtraTick(0);
					st.setExtraCount(0);
					com.fnaf.nightshift.campaign.BreakdownManager.tick(w, st, NightManager.definition(ctx.getSource().getServer()));
					return 1;
				})));
	}

	private static int status(CommandContext<ServerCommandSource> ctx) {
		ctx.getSource().sendFeedback(() -> NightManager.statusText(CampaignState.get(ctx.getSource().getServer())), false);
		return 1;
	}

	private static int start(CommandContext<ServerCommandSource> ctx, int night) {
		boolean ok = NightManager.startNight(ctx.getSource().getServer(), night, ctx.getSource().getPlayer());
		if (!ok && ctx.getSource().getPlayer() == null) {
			ctx.getSource().sendError(Text.translatable("message.fnaf_night_shift.no_shelter"));
		}
		return ok ? 1 : 0;
	}

	private static int summon(CommandContext<ServerCommandSource> ctx, BlockPos pos) {
		AnimatronicKind kind = AnimatronicKind.byId(StringArgumentType.getString(ctx, "animatronic"));
		if (kind == null || kind == AnimatronicKind.ANY) {
			ctx.getSource().sendError(Text.translatable("command.fnaf_night_shift.bad_animatronic"));
			return 0;
		}
		ServerWorld world = ctx.getSource().getWorld();
		// Sesión -1: entidad de prueba, el NightManager no la elimina ni la cuenta.
		AnimatronicEntity e = NightManager.spawn(world, kind, pos, -1);
		if (e == null) return 0;
		ctx.getSource().sendFeedback(() -> Text.translatable("command.fnaf_night_shift.summoned",
				Text.translatable("animatronic.fnaf_night_shift." + kind.id()), pos.toShortString()), true);
		return 1;
	}

	private static int listRepairs(CommandContext<ServerCommandSource> ctx) {
		CampaignState s = CampaignState.get(ctx.getSource().getServer());
		ServerWorld world = s.getFacilityWorld(ctx.getSource().getServer());
		ctx.getSource().sendFeedback(() -> Text.translatable("command.fnaf_night_shift.repairs_header", s.getStations().size()).formatted(Formatting.GOLD), false);
		for (BlockPos p : s.getStations()) {
			if (world != null && world.getBlockEntity(p) instanceof RepairStationBlockEntity be) {
				boolean req = s.getRequired().contains(p);
				boolean done = s.getCompleted().contains(p);
				Text line = Text.literal(" - ").append(Text.translatable(be.getRepairType().translationKey()))
						.append(Text.literal(" @ " + p.toShortString() + " "))
						.append(Text.translatable(be.isBroken() ? "command.fnaf_night_shift.broken" : "command.fnaf_night_shift.ok")
								.formatted(be.isBroken() ? Formatting.RED : Formatting.GREEN))
						.append(Text.literal(req ? (done ? " [✔]" : " [!]") : ""));
				ctx.getSource().sendFeedback(() -> line, false);
			}
		}
		return s.getStations().size();
	}

	private static int completeRepairs(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		CampaignState s = CampaignState.get(ctx.getSource().getServer());
		ServerWorld world = s.getFacilityWorld(ctx.getSource().getServer());
		if (world == null) return 0;
		int n = 0;
		for (BlockPos p : s.getRequired().toArray(new BlockPos[0])) {
			if (!s.getCompleted().contains(p) && world.getBlockEntity(p) instanceof RepairStationBlockEntity be) {
				be.resetRepaired();
				NightManager.onRepairCompleted(world, p, player);
				n++;
			}
		}
		int count = n;
		ctx.getSource().sendFeedback(() -> Text.translatable("command.fnaf_night_shift.repairs_completed", count), true);
		return n;
	}

	private static int map(CommandContext<ServerCommandSource> ctx, boolean generate) {
		ServerCommandSource src = ctx.getSource();
		ServerWorld world = src.getWorld();
		BlockPos origin = BlockPos.ofFloored(src.getPosition()).down();
		FacilityGenerator.Report report = FacilityGenerator.check(world, origin);
		if (!report.ok()) {
			src.sendError(Text.translatable("command.fnaf_night_shift.map_blocked", report.blocking(), report.firstBlocked() == null ? "-" : report.firstBlocked().toShortString()));
			return 0;
		}
		if (!generate) {
			src.sendFeedback(() -> Text.translatable("command.fnaf_night_shift.map_ok", FacilityGenerator.describeBounds(origin)), false);
			return 1;
		}
		src.sendFeedback(() -> Text.translatable("command.fnaf_night_shift.map_generating"), true);
		FacilityGenerator.Result result = FacilityGenerator.generate(world, origin);
		src.sendFeedback(() -> Text.translatable("command.fnaf_night_shift.map_done", result.blocks(), result.stations(), result.nodes(),
				result.shelter().toShortString()), true);
		return 1;
	}
}
