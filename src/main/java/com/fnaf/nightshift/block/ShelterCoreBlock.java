package com.fnaf.nightshift.block;

import com.fnaf.nightshift.campaign.CampaignState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Núcleo del refugio. Define el área segura, recarga linternas y permite
 * comenzar la siguiente noche o entregar el turno cuando todo está reparado.
 */
public class ShelterCoreBlock extends Block {
	public ShelterCoreBlock(Settings settings) {
		super(settings);
	}

	@Override
	public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		if (world instanceof ServerWorld sw && !oldState.isOf(this)) {
			CampaignState.get(sw.getServer()).setShelter(sw, pos);
		}
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!newState.isOf(this) && world instanceof ServerWorld sw) {
			CampaignState.get(sw.getServer()).clearShelterIf(sw, pos);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
		if (world.isClient) return ActionResult.SUCCESS;
		if (!(world instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity sp)) return ActionResult.CONSUME;
		CampaignState campaign = CampaignState.get(sw.getServer());
		if (!campaign.isShelter(sw, pos)) {
			campaign.setShelter(sw, pos);
			sp.sendMessage(Text.translatable("message.fnaf_night_shift.shelter_set").formatted(Formatting.GREEN), false);
			return ActionResult.CONSUME;
		}
		// Abre el menú de noches (iniciar, continuar, elegir noche, reiniciar...).
		com.fnaf.nightshift.network.ModPackets.sendMenu(sp);
		return ActionResult.CONSUME;
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(2) == 0) {
			world.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
					pos.getY() + 1.05, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
		}
	}
}
