package com.ancientcastles.registry;

import com.ancientcastles.block.SealedVaultBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
	/** Behind every throne. Wakes the castle's lord, and opens into the treasure chest once the lord is beaten. */
	public static final Block SEALED_VAULT = Register.block(
		"sealed_vault",
		SealedVaultBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.DEEPSLATE)
			.instrument(NoteBlockInstrument.BASEDRUM)
			.strength(-1.0F, 3_600_000.0F)
			.noLootTable()
			.pushReaction(PushReaction.IMMOVEABLE)
			.sound(SoundType.DEEPSLATE_BRICKS)
			.lightLevel(state -> 6)
	);

	private ModBlocks() {
	}

	public static void initialize() {
	}
}
