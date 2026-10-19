package com.fnaf.nightshift.block;

import com.fnaf.nightshift.ai.NoiseManager;
import com.fnaf.nightshift.campaign.CampaignState;
import com.fnaf.nightshift.registry.ModItems;
import com.fnaf.nightshift.registry.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Caja de suministros: una vez por noche entrega repuestos al azar.
 * Las cajas se rellenan al iniciar cada noche.
 */
public class SupplyCrateBlock extends Block {
	public static final BooleanProperty LOOTED = BooleanProperty.of("looted");

	public SupplyCrateBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(LOOTED, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(LOOTED);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (state.get(LOOTED)) {
			player.sendMessage(Text.translatable("message.fnaf_night_shift.crate_empty").formatted(Formatting.GRAY), true);
			return ActionResult.CONSUME;
		}
		world.setBlockState(pos, state.with(LOOTED, true), Block.NOTIFY_ALL);
		Random r = world.random;
		give(player, new ItemStack(ModItems.FUSE, 1 + r.nextInt(3)));
		give(player, new ItemStack(ModItems.CABLE, 1 + r.nextInt(2)));
		if (r.nextFloat() < 0.7f) give(player, new ItemStack(ModItems.ELECTRONIC_PARTS, 1 + r.nextInt(2)));
		if (r.nextFloat() < 0.35f) give(player, new ItemStack(ModItems.BATTERY));
		world.playSound(null, pos, ModSounds.CRATE_OPEN, SoundCategory.BLOCKS, 1.0f, 1.0f);
		if (world instanceof ServerWorld sw) NoiseManager.emit(sw, pos, 8, NoiseManager.NoiseKind.DOOR);
		player.sendMessage(Text.translatable("message.fnaf_night_shift.crate_looted").formatted(Formatting.GOLD), true);
		return ActionResult.CONSUME;
	}

	private static void give(PlayerEntity player, ItemStack stack) {
		if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
	}

	@Override
	public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		if (world instanceof ServerWorld sw) CampaignState.get(sw.getServer()).registerCrate(sw, pos);
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!newState.isOf(this) && world instanceof ServerWorld sw) CampaignState.get(sw.getServer()).unregisterCrate(sw, pos);
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
