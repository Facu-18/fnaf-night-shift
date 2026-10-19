package com.fnaf.nightshift.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

/**
 * Objeto fino montado en pared: carteles de señalización y luces de
 * emergencia. FACING apunta hacia donde mira el frente.
 */
public class WallPanelBlock extends HorizontalFacingBlock {
	private final VoxelShape north, south, east, west;

	/** depth: grosor en píxeles; inset: margen lateral/vertical en píxeles. */
	public WallPanelBlock(Settings settings, double depth, double insetX, double insetY) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
		north = Block.createCuboidShape(insetX, insetY, 16 - depth, 16 - insetX, 16 - insetY, 16);
		south = Block.createCuboidShape(insetX, insetY, 0, 16 - insetX, 16 - insetY, depth);
		east = Block.createCuboidShape(0, insetY, insetX, depth, 16 - insetY, 16 - insetX);
		west = Block.createCuboidShape(16 - depth, insetY, insetX, 16, 16 - insetY, 16 - insetX);
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		Direction side = ctx.getSide();
		Direction facing = side.getAxis().isHorizontal() ? side : ctx.getHorizontalPlayerFacing().getOpposite();
		return getDefaultState().with(FACING, facing);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return switch (state.get(FACING)) {
			case SOUTH -> south;
			case EAST -> east;
			case WEST -> west;
			default -> north;
		};
	}
}
