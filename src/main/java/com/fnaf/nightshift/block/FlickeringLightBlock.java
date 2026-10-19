package com.fnaf.nightshift.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Lámpara de techo industrial. Si está defectuosa (FAULTY) parpadea de forma
 * irregular hasta que se repara el panel de iluminación cercano.
 */
public class FlickeringLightBlock extends Block {
	public static final BooleanProperty LIT = BooleanProperty.of("lit");
	public static final BooleanProperty FAULTY = BooleanProperty.of("faulty");
	/** Lámpara fundida (oscuridad progresiva de las noches avanzadas). No se enciende. */
	public static final BooleanProperty DEAD = BooleanProperty.of("dead");
	private static final VoxelShape SHAPE = Block.createCuboidShape(3, 13, 3, 13, 16, 13);

	public FlickeringLightBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(LIT, true).with(FAULTY, true).with(DEAD, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(LIT, FAULTY, DEAD);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		if (!world.isClient && state.get(FAULTY) && !oldState.isOf(this)) {
			world.scheduleBlockTick(pos, this, 20 + world.random.nextInt(60));
		}
	}

	@Override
	public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (state.get(DEAD)) {
			if (state.get(LIT)) world.setBlockState(pos, state.with(LIT, false), Block.NOTIFY_LISTENERS);
			return;
		}
		if (!state.get(FAULTY)) {
			if (!state.get(LIT)) world.setBlockState(pos, state.with(LIT, true), Block.NOTIFY_LISTENERS);
			return;
		}
		boolean lit = state.get(LIT);
		// Encendida más tiempo del que está apagada; apagones cortos y nerviosos.
		int delay = lit ? 30 + random.nextInt(120) : 3 + random.nextInt(12);
		world.setBlockState(pos, state.with(LIT, !lit), Block.NOTIFY_LISTENERS);
		if (random.nextInt(4) == 0) {
			world.playSound(null, pos, SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT, SoundCategory.BLOCKS, 0.15f, 1.8f);
		}
		world.scheduleBlockTick(pos, this, delay);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!player.getAbilities().creativeMode) return ActionResult.PASS;
		if (!world.isClient) {
			BlockState next = state.cycle(FAULTY).with(LIT, true);
			world.setBlockState(pos, next, Block.NOTIFY_ALL);
			if (next.get(FAULTY)) world.scheduleBlockTick(pos, this, 10);
		}
		return ActionResult.success(world.isClient);
	}

	/** Cambia el estado defectuoso de todas las lámparas en un radio. */
	public static void setAreaFaulty(ServerWorld world, BlockPos center, int radius, boolean faulty) {
		for (BlockPos p : BlockPos.iterate(center.add(-radius, -4, -radius), center.add(radius, 6, radius))) {
			BlockState s = world.getBlockState(p);
			if (s.getBlock() instanceof FlickeringLightBlock light && s.get(FAULTY) != faulty) {
				world.setBlockState(p, s.with(FAULTY, faulty).with(LIT, !s.get(DEAD)), Block.NOTIFY_LISTENERS);
				if (faulty) world.scheduleBlockTick(p.toImmutable(), light, 5 + world.random.nextInt(40));
			}
		}
	}

	/**
	 * Interferencia eléctrica al paso de un animatrónico: las lámparas cercanas
	 * se cortan un instante (vuelven solas; las averiadas siguen parpadeando).
	 */
	public static void disturb(ServerWorld world, BlockPos center, int radius) {
		for (BlockPos p : BlockPos.iterate(center.add(-radius, -1, -radius), center.add(radius, 7, radius))) {
			BlockState s = world.getBlockState(p);
			if (s.getBlock() instanceof FlickeringLightBlock light && s.get(LIT) && !s.get(DEAD) && world.random.nextInt(3) != 0) {
				world.setBlockState(p, s.with(LIT, false), Block.NOTIFY_LISTENERS);
				world.scheduleBlockTick(p.toImmutable(), light, 3 + world.random.nextInt(10));
			}
		}
	}

	/** Apagón: apaga temporalmente lámparas cercanas (vuelven con el parpadeo). */
	public static void blackout(ServerWorld world, BlockPos center, int radius) {
		for (BlockPos p : BlockPos.iterate(center.add(-radius, -4, -radius), center.add(radius, 6, radius))) {
			BlockState s = world.getBlockState(p);
			if (s.getBlock() instanceof FlickeringLightBlock light && s.get(LIT) && !s.get(DEAD)) {
				world.setBlockState(p, s.with(LIT, false).with(FAULTY, true), Block.NOTIFY_LISTENERS);
				world.scheduleBlockTick(p.toImmutable(), light, 200 + world.random.nextInt(200));
			}
		}
	}
}
