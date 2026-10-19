package com.fnaf.nightshift.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** Decoración con forma fija (mesas de fiesta, cajas de cables, etc.). */
public class ShapedDecorBlock extends Block {
	private final VoxelShape shape;

	public ShapedDecorBlock(Settings settings, VoxelShape shape) {
		super(settings);
		this.shape = shape;
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shape;
	}
}
