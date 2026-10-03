package com.ancientcastles.registry;

import java.util.Set;

import com.ancientcastles.AncientCastles;
import com.ancientcastles.block.SealedVaultBlockEntity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<SealedVaultBlockEntity> SEALED_VAULT = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		AncientCastles.id("sealed_vault"),
		new BlockEntityType<>(SealedVaultBlockEntity::new, Set.of(ModBlocks.SEALED_VAULT))
	);

	private ModBlockEntities() {
	}

	public static void initialize() {
	}
}
