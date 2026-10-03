package com.ancientcastles.block;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.ancientcastles.boss.CastleBosses;
import com.ancientcastles.castle.CastlePiece;
import com.ancientcastles.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Watches the throne room. The first player to come within {@link #TRIGGER_RANGE} wakes the castle's lord; once the
 * lord dies ({@link CastleBosses} calls {@link #open}) the vault becomes the treasure chest. If the lord goes missing
 * without dying, the vault re-arms after a while so the next visitor wakes a new one.
 */
public class SealedVaultBlockEntity extends BlockEntity {
	private static final double TRIGGER_RANGE = 10.0;
	private static final double TRIGGER_HEIGHT = 5.0;
	private static final int CHECK_EVERY = 10;
	private static final int REARM_AFTER = 600;
	private static final double MESSAGE_RANGE = 32.0;

	private @Nullable UUID boss;
	private int missingTicks;

	public SealedVaultBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SEALED_VAULT, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, SealedVaultBlockEntity vault) {
		if (!(level instanceof ServerLevel serverLevel) || (level.getGameTime() + pos.hashCode()) % CHECK_EVERY != 0) {
			return;
		}
		if (vault.boss == null) {
			if (playerInThroneRoom(serverLevel, pos)) {
				vault.boss = CastleBosses.spawn(serverLevel, pos, state.getValue(SealedVaultBlock.FACING), state.getValue(SealedVaultBlock.THEME));
				vault.missingTicks = 0;
				vault.setChanged();
			}
			return;
		}
		Entity lord = serverLevel.getEntity(vault.boss);
		if (lord != null && lord.isAlive()) {
			vault.missingTicks = 0;
			return;
		}
		vault.missingTicks += CHECK_EVERY;
		if (vault.missingTicks >= REARM_AFTER) {
			vault.boss = null;
			vault.missingTicks = 0;
			vault.setChanged();
		}
	}

	private static boolean playerInThroneRoom(ServerLevel level, BlockPos pos) {
		Vec3 center = Vec3.atCenterOf(pos);
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			double dx = player.getX() - center.x;
			double dz = player.getZ() - center.z;
			if (dx * dx + dz * dz <= TRIGGER_RANGE * TRIGGER_RANGE && Math.abs(player.getY() - center.y) <= TRIGGER_HEIGHT) {
				return true;
			}
		}
		return false;
	}

	/** The lord is dead: becomes the treasure chest, with a fanfare. */
	public void open(ServerLevel level) {
		BlockPos pos = worldPosition;
		Direction facing = getBlockState().getValue(SealedVaultBlock.FACING);
		level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), Block.UPDATE_ALL);
		RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), pos, CastlePiece.TREASURE_LOOT);
		level.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 60, 0.4, 0.6, 0.4, 0.3);
		Vec3 center = Vec3.atCenterOf(pos);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(center) <= MESSAGE_RANGE * MESSAGE_RANGE) {
				player.sendOverlayMessage(Component.translatable("message.ancientcastles.unsealed"));
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		boss = input.read("boss", UUIDUtil.CODEC).orElse(null);
		missingTicks = 0;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("boss", UUIDUtil.CODEC, boss);
	}
}
