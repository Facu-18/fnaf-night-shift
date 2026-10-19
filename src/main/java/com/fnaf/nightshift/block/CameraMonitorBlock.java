package com.fnaf.nightshift.block;

import com.fnaf.nightshift.campaign.CameraManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Monitor de vigilancia: clic derecho abre el sistema de cámaras.
 * Mientras se mira, el cuerpo del jugador queda en la oficina, expuesto.
 */
public class CameraMonitorBlock extends HorizontalFacingBlock {
	private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, 14, 15);

	public CameraMonitorBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
		if (!world.isClient && player instanceof ServerPlayerEntity sp) CameraManager.open(sp, pos);
		return ActionResult.success(world.isClient);
	}
}
