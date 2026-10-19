package com.fnaf.nightshift.block;

import com.fnaf.nightshift.ai.NoiseManager;
import com.fnaf.nightshift.campaign.CampaignState;
import com.fnaf.nightshift.registry.ModItems;
import com.fnaf.nightshift.registry.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Casillero de herramientas. Al comenzar cada noche el servidor decide qué
 * casilleros contienen herramienta (ver ResourceManager). Cada casillero
 * entrega su herramienta una sola vez por noche: no se pueden duplicar objetos.
 * TIER indica lo arriesgado de su ubicación (0 seguro, 2 peligroso).
 */
public class ToolLockerBlock extends HorizontalFacingBlock {
	public enum Tool implements StringIdentifiable {
		WRENCH("wrench"), SCREWDRIVER("screwdriver"), PLIERS("pliers");

		private final String id;

		Tool(String id) {
			this.id = id;
		}

		@Override
		public String asString() {
			return id;
		}

		public Item item() {
			return switch (this) {
				case WRENCH -> ModItems.WRENCH;
				case SCREWDRIVER -> ModItems.SCREWDRIVER;
				case PLIERS -> ModItems.PLIERS;
			};
		}
	}

	public static final EnumProperty<Tool> TOOL = EnumProperty.of("tool", Tool.class);
	public static final BooleanProperty STOCKED = BooleanProperty.of("stocked");
	public static final IntProperty TIER = IntProperty.of("tier", 0, 2);

	public ToolLockerBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(TOOL, Tool.WRENCH)
				.with(STOCKED, false).with(TIER, 0));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, TOOL, STOCKED, TIER);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
		if (world.isClient) return ActionResult.SUCCESS;
		if (player.getAbilities().creativeMode && player.isSneaking()) {
			// creativo: Mayús + clic cambia el nivel de riesgo del casillero
			BlockState next = state.cycle(TIER);
			world.setBlockState(pos, next, Block.NOTIFY_ALL);
			player.sendMessage(Text.translatable("message.fnaf_night_shift.locker_tier", next.get(TIER)), true);
			return ActionResult.CONSUME;
		}
		if (!state.get(STOCKED)) {
			player.sendMessage(Text.translatable("message.fnaf_night_shift.locker_empty").formatted(Formatting.GRAY), true);
			world.playSound(null, pos, ModSounds.LOCKER_OPEN, SoundCategory.BLOCKS, 0.5f, 1.3f);
			return ActionResult.CONSUME;
		}
		// primero se vacía el casillero (servidor, un solo hilo): nadie más puede recogerla
		world.setBlockState(pos, state.with(STOCKED, false), Block.NOTIFY_ALL);
		ItemStack stack = new ItemStack(state.get(TOOL).item());
		if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
		world.playSound(null, pos, ModSounds.LOCKER_OPEN, SoundCategory.BLOCKS, 1.0f, 1.0f);
		if (world instanceof ServerWorld sw) NoiseManager.emit(sw, pos, 8, NoiseManager.NoiseKind.DOOR);
		player.sendMessage(Text.translatable("message.fnaf_night_shift.locker_taken", Text.translatable(state.get(TOOL).item().getTranslationKey()))
				.formatted(Formatting.GOLD), true);
		return ActionResult.CONSUME;
	}

	@Override
	public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		if (world instanceof ServerWorld sw) CampaignState.get(sw.getServer()).registerLocker(sw, pos);
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!newState.isOf(this) && world instanceof ServerWorld sw) CampaignState.get(sw.getServer()).unregisterLocker(sw, pos);
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
