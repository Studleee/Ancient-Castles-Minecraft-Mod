package com.ancientcastles.registry;

import com.ancientcastles.AncientCastles;
import com.ancientcastles.castle.CastlePiece;
import com.ancientcastles.castle.CastleStructure;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * The castle structure. Where it can appear and how often is data: see worldgen/structure/castle.json,
 * worldgen/structure_set/castles.json, and the has_structure/castle biome tag.
 */
public final class ModStructures {
	public static final StructureType<CastleStructure> CASTLE = Registry.register(
		BuiltInRegistries.STRUCTURE_TYPE,
		AncientCastles.id("castle"),
		() -> CastleStructure.CODEC
	);

	public static final StructurePieceType CASTLE_PIECE = Registry.register(
		BuiltInRegistries.STRUCTURE_PIECE,
		AncientCastles.id("castle"),
		(context, tag) -> new CastlePiece(tag)
	);

	private ModStructures() {
	}

	public static void initialize() {
	}
}
