package com.fnaf.nightshift.item;

import com.fnaf.nightshift.registry.ModBlocks;
import com.fnaf.nightshift.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Ilumina dinámicamente lo que apunta cada linterna encendida colocando un
 * bloque de luz invisible en el aire justo antes de la superficie apuntada
 * (y otro cerca del jugador). Funciona en multijugador porque la luz es real.
 */
public final class FlashlightLightManager {
	public static final double RANGE = 14.0;

	private record Lights(ServerWorld world, BlockPos far, BlockPos near) {
	}

	private static final Map<UUID, Lights> ACTIVE = new HashMap<>();

	private FlashlightLightManager() {
	}

	/** Devuelve la linterna encendida que el jugador sostiene, o null. */
	@Nullable
	public static ItemStack isHoldingActive(PlayerEntity player) {
		ItemStack main = player.getMainHandStack();
		if (main.isOf(ModItems.FLASHLIGHT) && FlashlightItem.isOn(main)) return main;
		ItemStack off = player.getOffHandStack();
		if (off.isOf(ModItems.FLASHLIGHT) && FlashlightItem.isOn(off)) return off;
		return null;
	}

	public static void tick(MinecraftServer server) {
		if (server.getTicks() % 2 != 0) return;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.isSpectator() || isHoldingActive(player) == null) {
				remove(player);
				continue;
			}
			ServerWorld world = player.getServerWorld();
			Vec3d eye = player.getEyePos();
			Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(RANGE));
			BlockHitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
			Vec3d target = hit.getType() == HitResult.Type.MISS ? end : hit.getPos().subtract(player.getRotationVec(1.0f).multiply(0.6));
			BlockPos far = findAir(world, BlockPos.ofFloored(target));
			BlockPos near = findAir(world, BlockPos.ofFloored(eye.add(player.getRotationVec(1.0f).multiply(2.0))));
			if (near != null && near.equals(far)) near = null;

			Lights old = ACTIVE.get(player.getUuid());
			Lights now = new Lights(world, far, near);
			if (old != null && old.world == world && eq(old.far, far) && eq(old.near, near)) continue;
			ACTIVE.put(player.getUuid(), now);
			if (old != null) {
				clear(old.world, old.far, now);
				clear(old.world, old.near, now);
			}
			place(world, far);
			place(world, near);
		}
	}

	private static boolean eq(@Nullable BlockPos a, @Nullable BlockPos b) {
		return a == null ? b == null : a.equals(b);
	}

	@Nullable
	private static BlockPos findAir(ServerWorld world, BlockPos pos) {
		if (!world.isInBuildLimit(pos)) return null;
		BlockState s = world.getBlockState(pos);
		if (s.isAir() || s.isOf(ModBlocks.FLASHLIGHT_LIGHT)) return pos;
		BlockPos up = pos.up();
		BlockState u = world.getBlockState(up);
		if (u.isAir() || u.isOf(ModBlocks.FLASHLIGHT_LIGHT)) return up;
		return null;
	}

	private static void place(ServerWorld world, @Nullable BlockPos pos) {
		if (pos == null) return;
		BlockState s = world.getBlockState(pos);
		if (s.isAir() && !s.isOf(ModBlocks.FLASHLIGHT_LIGHT)) {
			world.setBlockState(pos, ModBlocks.FLASHLIGHT_LIGHT.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
	}

	private static void clear(ServerWorld world, @Nullable BlockPos pos, Lights keep) {
		if (pos == null || (keep.world == world && (pos.equals(keep.far) || pos.equals(keep.near)))) return;
		if (isActiveLight(world, pos, true)) return; // otra linterna también lo usa
		if (world.getBlockState(pos).isOf(ModBlocks.FLASHLIGHT_LIGHT)) {
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
	}

	public static boolean isActiveLight(ServerWorld world, BlockPos pos) {
		return isActiveLight(world, pos, false);
	}

	private static boolean isActiveLight(ServerWorld world, BlockPos pos, boolean ignoreNone) {
		for (Lights l : ACTIVE.values()) {
			if (l.world == world && (pos.equals(l.far) || pos.equals(l.near))) return true;
		}
		return false;
	}

	public static void remove(PlayerEntity player) {
		Lights old = ACTIVE.remove(player.getUuid());
		if (old != null) {
			Lights none = new Lights(old.world, null, null);
			clear(old.world, old.far, none);
			clear(old.world, old.near, none);
		}
	}

	public static void clearAll(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Lights>> it = ACTIVE.entrySet().iterator();
		while (it.hasNext()) {
			Lights l = it.next().getValue();
			it.remove();
			for (BlockPos p : new BlockPos[]{l.far, l.near}) {
				if (p != null && l.world.getBlockState(p).isOf(ModBlocks.FLASHLIGHT_LIGHT)) {
					l.world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	/**
	 * ¿Algún jugador está alumbrando a esta entidad con una linterna encendida?
	 */
	@Nullable
	public static PlayerEntity findShiningPlayer(Entity target, double maxDistance) {
		for (PlayerEntity player : target.getWorld().getPlayers()) {
			if (player.isSpectator() || isHoldingActive(player) == null) continue;
			double distSq = player.squaredDistanceTo(target);
			if (distSq > maxDistance * maxDistance) continue;
			Vec3d look = player.getRotationVec(1.0f).normalize();
			Vec3d toTarget = target.getPos().add(0, target.getHeight() * 0.6, 0).subtract(player.getEyePos()).normalize();
			if (look.dotProduct(toTarget) > 0.93 && player.canSee(target)) return player;
		}
		return null;
	}
}
