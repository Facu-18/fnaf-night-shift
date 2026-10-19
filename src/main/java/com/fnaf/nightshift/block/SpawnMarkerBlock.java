package com.fnaf.nightshift.block;

import com.fnaf.nightshift.campaign.CampaignState;
import com.fnaf.nightshift.entity.AnimatronicKind;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Punto de aparición de animatrónicos. Clic derecho en creativo cambia el
 * animatrónico asignado (cualquiera, Bonnie, Chica, Foxy o Freddy).
 */
public class SpawnMarkerBlock extends MarkerBlock {
	public static final EnumProperty<AnimatronicKind> ANIMATRONIC = EnumProperty.of("animatronic", AnimatronicKind.class,
			k -> k != AnimatronicKind.GOLDEN_FREDDY);

	public SpawnMarkerBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(ANIMATRONIC, AnimatronicKind.ANY));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(ANIMATRONIC);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!player.getAbilities().creativeMode || hand != Hand.MAIN_HAND) return ActionResult.PASS;
		if (!world.isClient) {
			BlockState next = state.cycle(ANIMATRONIC);
			world.setBlockState(pos, next, Block.NOTIFY_ALL);
			player.sendMessage(Text.translatable("message.fnaf_night_shift.marker_kind",
					Text.translatable("animatronic.fnaf_night_shift." + next.get(ANIMATRONIC).id())), true);
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		if (world instanceof ServerWorld sw) CampaignState.get(sw.getServer()).registerSpawn(sw, pos, state.get(ANIMATRONIC));
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!newState.isOf(this) && world instanceof ServerWorld sw) CampaignState.get(sw.getServer()).unregisterSpawn(sw, pos);
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
