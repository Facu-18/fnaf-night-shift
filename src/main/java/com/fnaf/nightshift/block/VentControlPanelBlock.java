package com.fnaf.nightshift.block;

import com.fnaf.nightshift.campaign.VentilationSystem;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Panel de control de la ventilación de la oficina. Clic derecho reinicia
 * el sistema (ver {@link VentilationSystem}).
 */
public class VentControlPanelBlock extends WallPanelBlock {
	public VentControlPanelBlock(Settings settings) {
		super(settings, 2, 3, 3);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
		if (!world.isClient && player instanceof ServerPlayerEntity sp) VentilationSystem.reset(sp, pos);
		return ActionResult.success(world.isClient);
	}
}
