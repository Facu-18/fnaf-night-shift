package com.fnaf.nightshift.repair;

import com.fnaf.nightshift.campaign.CampaignState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Bloque de instalación reparable (generador, fusibles, terminal...).
 * Toda la lógica de estado vive en {@link RepairStationBlockEntity}.
 */
public class RepairStationBlock extends BlockWithEntity {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty BROKEN = BooleanProperty.of("broken");

	private final RepairType type;

	public RepairStationBlock(RepairType type, Settings settings) {
		super(settings);
		this.type = type;
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(BROKEN, false));
	}

	public RepairType getRepairType() {
		return type;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, BROKEN);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.fullCube();
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new RepairStationBlockEntity(pos, state);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
		if (world.isClient) return ActionResult.SUCCESS;
		if (world.getBlockEntity(pos) instanceof RepairStationBlockEntity be && player instanceof ServerPlayerEntity sp) {
			be.interact(sp);
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		if (world instanceof ServerWorld sw) CampaignState.get(sw.getServer()).registerStation(sw, pos);
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!newState.isOf(this) && world instanceof ServerWorld sw) {
			CampaignState.get(sw.getServer()).unregisterStation(sw, pos);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (!state.get(BROKEN)) return;
		Direction facing = state.get(FACING);
		double x = pos.getX() + 0.5 + facing.getOffsetX() * 0.55 + (random.nextDouble() - 0.5) * 0.6;
		double y = pos.getY() + 0.3 + random.nextDouble() * 0.6;
		double z = pos.getZ() + 0.5 + facing.getOffsetZ() * 0.55 + (random.nextDouble() - 0.5) * 0.6;
		if (random.nextInt(3) == 0) {
			world.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, (random.nextDouble() - 0.5) * 0.4, 0.15, (random.nextDouble() - 0.5) * 0.4);
		}
		if (random.nextInt(4) == 0) {
			world.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 1.0, z, 0, 0.03, 0);
		}
		if (random.nextInt(25) == 0) {
			world.playSound(x, y, z, com.fnaf.nightshift.registry.ModSounds.REPAIR_SPARK, SoundCategory.BLOCKS, 0.35f, 0.8f + random.nextFloat() * 0.4f, false);
		}
	}
}
