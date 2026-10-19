package com.fnaf.nightshift.block;

import com.fnaf.nightshift.ai.NoiseManager;
import com.fnaf.nightshift.registry.ModSounds;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Puerta de seguridad metálica. Los jugadores la abren a mano; los
 * animatrónicos no pueden abrirla (usa el tipo de hierro, que la navegación
 * de las criaturas considera infranqueable cuando está cerrada).
 */
public class SecurityDoorBlock extends DoorBlock {
	public SecurityDoorBlock(Settings settings) {
		super(settings, BlockSetType.IRON);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		boolean open = !state.get(OPEN);
		if (!world.isClient && world instanceof ServerWorld swj) {
			BlockPos lower = state.get(HALF) == net.minecraft.block.enums.DoubleBlockHalf.UPPER ? pos.down() : pos;
			if (com.fnaf.nightshift.campaign.CampaignState.get(swj.getServer()).isDoorJammed(lower)) {
				player.sendMessage(net.minecraft.text.Text.translatable("message.fnaf_night_shift.door_jammed")
						.formatted(net.minecraft.util.Formatting.RED), true);
				world.playSound(null, pos, ModSounds.REPAIR_FAIL, SoundCategory.BLOCKS, 0.5f, 1.4f);
				return ActionResult.CONSUME;
			}
		}
		if (!world.isClient) {
			setOpen(player, world, state, pos, open);
			world.playSound(null, pos, ModSounds.DOOR_SLAM, SoundCategory.BLOCKS, 0.9f, open ? 1.15f : 0.9f);
			if (world instanceof ServerWorld sw) NoiseManager.emit(sw, pos, 12, NoiseManager.NoiseKind.DOOR);
		}
		return ActionResult.success(world.isClient);
	}
}
