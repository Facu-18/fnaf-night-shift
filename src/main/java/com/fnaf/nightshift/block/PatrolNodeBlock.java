package com.fnaf.nightshift.block;

import com.fnaf.nightshift.campaign.CampaignState;
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
 * Nodo de patrulla: punto de paso de la IA. Clic derecho en creativo
 * cambia la zona del nodo.
 */
public class PatrolNodeBlock extends MarkerBlock {
	public static final EnumProperty<Zone> ZONE = EnumProperty.of("zone", Zone.class);

	public PatrolNodeBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(ZONE, Zone.GENERAL));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(ZONE);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!player.getAbilities().creativeMode || hand != Hand.MAIN_HAND) return ActionResult.PASS;
		if (!world.isClient) {
			Zone next = state.get(ZONE).next();
			world.setBlockState(pos, state.with(ZONE, next), Block.NOTIFY_ALL);
			player.sendMessage(Text.translatable("message.fnaf_night_shift.node_zone", Text.translatable("zone.fnaf_night_shift." + next.asString())), true);
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		if (world instanceof ServerWorld sw) CampaignState.get(sw.getServer()).registerNode(sw, pos, state.get(ZONE));
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!newState.isOf(this) && world instanceof ServerWorld sw) CampaignState.get(sw.getServer()).unregisterNode(sw, pos);
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
