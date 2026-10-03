package com.ancientcastles.castle;

import java.util.Optional;

import com.ancientcastles.registry.ModStructures;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * Picks where a castle goes: fairly flat, dry ground. The castle's size, hill height, facing, and theme (from the
 * biome) are chosen here and handed to {@link CastlePiece}, which builds it.
 */
public class CastleStructure extends Structure {
	public static final MapCodec<CastleStructure> CODEC = simpleCodec(CastleStructure::new);

	/** How far the ground may rise or fall across the castle's hilltop, and across its moat. */
	private static final int MAX_TOP_SPREAD = 8;
	private static final int MAX_MOAT_SPREAD = 12;

	public CastleStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		RandomSource random = context.random();
		int x = context.chunkPos().getMiddleBlockX();
		int z = context.chunkPos().getMiddleBlockZ();

		CastlePiece.Size size = CastlePiece.Size.random(random);
		int hill = 5 + random.nextInt(4);
		Rotation rotation = Rotation.getRandom(random);
		long seed = random.nextLong();
		CastlePiece.Layout layout = new CastlePiece.Layout(size.half, hill);

		int[] top = sampleRing(context, x, z, layout.flatRadius(), true);
		if (top == null) {
			return Optional.empty();
		}
		int[] moat = sampleRing(context, x, z, layout.moatRadius(), false);
		if (spread(top) > MAX_TOP_SPREAD || spread(moat) > MAX_MOAT_SPREAD) {
			return Optional.empty();
		}
		int sum = 0;
		for (int height : top) {
			sum += height;
		}
		int baseY = Math.round((float) sum / top.length);
		if (baseY <= context.chunkGenerator().getSeaLevel()) {
			return Optional.empty();
		}

		CastleTheme theme = CastleTheme.forBiome(context.biomeResolver().getNoiseBiome(
			QuartPos.fromBlock(x), QuartPos.fromBlock(baseY), QuartPos.fromBlock(z)));
		BlockPos center = new BlockPos(x, baseY, z);
		return Optional.of(new GenerationStub(center, builder -> builder.addPiece(new CastlePiece(center, size, hill, rotation, theme, seed))));
	}

	/**
	 * Ground heights at the center and eight points on a ring around it, or null if dry is required and any of them
	 * is under water.
	 */
	private static int[] sampleRing(GenerationContext context, int x, int z, int radius, boolean dry) {
		ChunkGenerator generator = context.chunkGenerator();
		int[] heights = new int[9];
		int diagonal = Math.round(radius * 0.7071F);
		int[][] offsets = { { 0, 0 }, { radius, 0 }, { -radius, 0 }, { 0, radius }, { 0, -radius },
			{ diagonal, diagonal }, { -diagonal, diagonal }, { diagonal, -diagonal }, { -diagonal, -diagonal } };
		for (int i = 0; i < offsets.length; i++) {
			int px = x + offsets[i][0];
			int pz = z + offsets[i][1];
			int ground = generator.getFirstOccupiedHeight(px, pz, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
			if (dry) {
				int surface = generator.getFirstOccupiedHeight(px, pz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
				if (surface != ground) {
					return null;
				}
			}
			heights[i] = ground;
		}
		return heights;
	}

	private static int spread(int[] heights) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int height : heights) {
			min = Math.min(min, height);
			max = Math.max(max, height);
		}
		return max - min;
	}

	@Override
	public StructureType<?> type() {
		return ModStructures.CASTLE;
	}
}
