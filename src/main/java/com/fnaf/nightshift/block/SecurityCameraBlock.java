package com.fnaf.nightshift.block;

import com.fnaf.nightshift.repair.RepairStationBlock;
import com.fnaf.nightshift.repair.RepairType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * Cámara de seguridad colgada del techo. Es una instalación reparable más
 * (tipo {@link RepairType#CAMERA}): averiada, su señal en el monitor es estática.
 * FACING indica hacia dónde mira el objetivo.
 */
public class SecurityCameraBlock extends RepairStationBlock {
	private static final VoxelShape SHAPE = Block.createCuboidShape(4, 6, 4, 12, 16, 12);

	public SecurityCameraBlock(Settings settings) {
		super(RepairType.CAMERA, settings);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.empty();
	}
}
