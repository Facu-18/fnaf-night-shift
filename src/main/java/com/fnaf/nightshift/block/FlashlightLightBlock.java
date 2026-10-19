package com.fnaf.nightshift.block;

import com.fnaf.nightshift.item.FlashlightLightManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Bloque de luz invisible colocado por la linterna. Se autodestruye si la
 * linterna deja de apuntarlo, así nunca quedan luces huérfanas en el mundo
 * (ni siquiera tras cerrar el servidor de golpe).
 */
public class FlashlightLightBlock extends Block {
	public FlashlightLightBlock(Settings settings) {
		super(settings);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.INVISIBLE;
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.empty();
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.empty();
	}

	@Override
	public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		if (!world.isClient) world.scheduleBlockTick(pos, this, 10);
	}

	@Override
	public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (FlashlightLightManager.isActiveLight(world, pos)) {
			world.scheduleBlockTick(pos, this, 10);
		} else {
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
		}
	}
}
