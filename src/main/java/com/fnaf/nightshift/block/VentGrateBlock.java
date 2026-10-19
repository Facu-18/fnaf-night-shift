package com.fnaf.nightshift.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Rejilla de ventilación abatible: entrada a los conductos (rutas
 * alternativas por las que sólo cabe un jugador).
 */
public class VentGrateBlock extends TrapdoorBlock {
	public VentGrateBlock(Settings settings) {
		super(settings, BlockSetType.IRON);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!world.isClient) {
			BlockState next = state.cycle(OPEN);
			world.setBlockState(pos, next, Block.NOTIFY_LISTENERS);
			if (next.get(WATERLOGGED)) {
				world.scheduleFluidTick(pos, net.minecraft.fluid.Fluids.WATER, net.minecraft.fluid.Fluids.WATER.getTickRate(world));
			}
			world.playSound(null, pos, next.get(OPEN) ? SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN : SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE,
					SoundCategory.BLOCKS, 0.8f, 1.1f);
		}
		return ActionResult.success(world.isClient);
	}
}
