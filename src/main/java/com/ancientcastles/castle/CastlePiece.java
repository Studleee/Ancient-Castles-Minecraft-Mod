package com.ancientcastles.castle;

import com.ancientcastles.AncientCastles;
import com.ancientcastles.block.SealedVaultBlock;
import com.ancientcastles.registry.ModBlocks;
import com.ancientcastles.registry.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * One whole castle: a hill raised out of the ground with a water moat around its foot, and on top a square curtain
 * wall with round corner towers, a gatehouse facing the bridge, and a keep with the throne room and its sealed vault.
 * Medium and large castles also get barracks, a storehouse, and a well; large ones a garden.
 * <p>
 * Built in local coordinates: x/z from the castle's center with the gate toward +z (south), y from the hilltop
 * floor. They're turned by {@link #rotation} when placed. Each chunk the castle touches builds only its own part.
 */
public class CastlePiece extends StructurePiece {
	public static final ResourceKey<LootTable> STORAGE_LOOT = loot("castle_storage");
	public static final ResourceKey<LootTable> ARMORY_LOOT = loot("castle_armory");
	public static final ResourceKey<LootTable> TREASURE_LOOT = loot("castle_treasure");

	/** How far up and down from the hilltop to cut away terrain or fill it in. */
	private static final int CLEAR_ABOVE = 24;
	private static final int DIG_BELOW = 14;

	public enum Size {
		SMALL(10), MEDIUM(15), LARGE(21);

		/** From the center to the outside of the curtain wall. */
		public final int half;

		Size(int half) {
			this.half = half;
		}

		public static Size random(RandomSource random) {
			int roll = random.nextInt(100);
			return roll < 30 ? SMALL : roll < 75 ? MEDIUM : LARGE;
		}
	}

	/** The rings around the castle, measured from its center (see {@link #distance}). */
	public record Layout(int half, int hill) {
		/** The flat hilltop, a few blocks wider than the walls and towers. */
		public int flatRadius() {
			return half + 6;
		}

		/** Where the hill's slope reaches the ground. */
		public int hillRadius() {
			return flatRadius() + hill * 2;
		}

		public int moatRadius() {
			return hillRadius() + 5;
		}

		/** The ground just outside the moat, built up where it's too low to hold the water. */
		public int bankRadius() {
			return moatRadius() + 2;
		}
	}

	private final int centerX;
	private final int centerZ;
	private final int baseY;
	private final Size size;
	private final int hill;
	private final Rotation rotation;
	private final CastleTheme theme;
	private final long seed;

	private final Layout layout;
	private final int floorY;
	private final int waterY;

	public CastlePiece(BlockPos center, Size size, int hill, Rotation rotation, CastleTheme theme, long seed) {
		super(ModStructures.CASTLE_PIECE, 0, makeBox(center, new Layout(size.half, hill)));
		this.centerX = center.getX();
		this.centerZ = center.getZ();
		this.baseY = center.getY();
		this.size = size;
		this.hill = hill;
		this.rotation = rotation;
		this.theme = theme;
		this.seed = seed;
		this.layout = new Layout(size.half, hill);
		this.floorY = baseY + hill;
		this.waterY = baseY - 1;
	}

	public CastlePiece(CompoundTag tag) {
		super(ModStructures.CASTLE_PIECE, tag);
		this.centerX = tag.getIntOr("CX", 0);
		this.centerZ = tag.getIntOr("CZ", 0);
		this.baseY = tag.getIntOr("BaseY", 64);
		this.size = Size.values()[Math.clamp(tag.getIntOr("Size", 1), 0, Size.values().length - 1)];
		this.hill = tag.getIntOr("Hill", 6);
		this.rotation = Rotation.values()[Math.clamp(tag.getIntOr("Rot", 0), 0, Rotation.values().length - 1)];
		this.theme = CastleTheme.byName(tag.getStringOr("Theme", "plains"));
		this.seed = tag.getLongOr("Seed", 0L);
		this.layout = new Layout(size.half, hill);
		this.floorY = baseY + hill;
		this.waterY = baseY - 1;
	}

	private static BoundingBox makeBox(BlockPos center, Layout layout) {
		int radius = layout.bankRadius() + 1;
		int floor = center.getY() + layout.hill();
		return new BoundingBox(center.getX() - radius, center.getY() - DIG_BELOW, center.getZ() - radius,
			center.getX() + radius, floor + CLEAR_ABOVE, center.getZ() + radius);
	}

	private static ResourceKey<LootTable> loot(String name) {
		return ResourceKey.create(Registries.LOOT_TABLE, AncientCastles.id("chests/" + name));
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putInt("CX", centerX);
		tag.putInt("CZ", centerZ);
		tag.putInt("BaseY", baseY);
		tag.putInt("Size", size.ordinal());
		tag.putInt("Hill", hill);
		tag.putInt("Rot", rotation.ordinal());
		tag.putString("Theme", theme.getSerializedName());
		tag.putLong("Seed", seed);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
		BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
		shapeTerrain(level, chunkBB);
		new Builder(level, chunkBB).build();
	}

	// ---- The hill and moat ----

	/** A rounded square: 0 at the center, and equal to the half-width along the axes and nearly so at the corners. */
	private static double distance(int dx, int dz) {
		double x = Math.abs(dx);
		double z = Math.abs(dz);
		return Math.pow(Math.pow(x, 8) + Math.pow(z, 8), 1.0 / 8);
	}

	/** The ground's top block on the hill: flat on top, then easing down to the base at the hill's foot. */
	private int hillY(double distance) {
		int flat = layout.flatRadius();
		if (distance <= flat) {
			return floorY;
		}
		double t = Math.min(1.0, (distance - flat) / (layout.hillRadius() - flat));
		return floorY - (int) Math.round(hill * t * t * (3 - 2 * t));
	}

	private void shapeTerrain(WorldGenLevel level, BoundingBox chunkBB) {
		int minX = Math.max(chunkBB.minX(), boundingBox.minX());
		int maxX = Math.min(chunkBB.maxX(), boundingBox.maxX());
		int minZ = Math.max(chunkBB.minZ(), boundingBox.minZ());
		int maxZ = Math.min(chunkBB.maxZ(), boundingBox.maxZ());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState top = theme.groundTop.defaultBlockState();
		BlockState fill = theme.groundFill.defaultBlockState();
		BlockState water = Blocks.WATER.defaultBlockState();

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				double d = distance(x - centerX, z - centerZ);
				if (d > layout.bankRadius() + 0.5) {
					continue;
				}
				int surface = surfaceY(level, pos, x, z);
				if (d <= layout.hillRadius()) {
					int ground = hillY(d);
					clearAbove(level, pos, x, z, ground);
					// A few layers of soil under the top even where the hill was cut down into stone.
					for (int y = Math.min(surface + 1, ground - 3); y < ground; y++) {
						level.setBlock(pos.set(x, y, z), fill, Block.UPDATE_CLIENTS);
					}
					level.setBlock(pos.set(x, ground, z), top, Block.UPDATE_CLIENTS);
				} else if (d <= layout.moatRadius()) {
					clearAbove(level, pos, x, z, waterY);
					for (int y = surface + 1; y < waterY - 3; y++) {
						level.setBlock(pos.set(x, y, z), fill, Block.UPDATE_CLIENTS);
					}
					level.setBlock(pos.set(x, waterY - 3, z), theme.moatBed.defaultBlockState(), Block.UPDATE_CLIENTS);
					for (int y = waterY - 2; y <= waterY; y++) {
						level.setBlock(pos.set(x, y, z), water, Block.UPDATE_CLIENTS);
					}
				} else if (surface < baseY) {
					for (int y = surface + 1; y < baseY; y++) {
						level.setBlock(pos.set(x, y, z), fill, Block.UPDATE_CLIENTS);
					}
					level.setBlock(pos.set(x, baseY, z), top, Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/** The top solid block of the column (ignoring water, plants, and snow), searched within the castle's height. */
	private int surfaceY(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		for (int y = floorY + CLEAR_ABOVE; y > baseY - DIG_BELOW; y--) {
			BlockState state = level.getBlockState(pos.set(x, y, z));
			if (!state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty()) {
				return y;
			}
		}
		return baseY - DIG_BELOW;
	}

	private void clearAbove(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground) {
		for (int y = ground + 1; y <= floorY + CLEAR_ABOVE; y++) {
			if (!level.getBlockState(pos.set(x, y, z)).isAir()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	// ---- The castle ----

	private final class Builder {
		private final WorldGenLevel level;
		private final BoundingBox chunkBB;
		private final int half = size.half;
		private final BlockState air = Blocks.AIR.defaultBlockState();

		Builder(WorldGenLevel level, BoundingBox chunkBB) {
			this.level = level;
			this.chunkBB = chunkBB;
		}

		void build() {
			courtyard();
			curtainWalls();
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					cornerTower(sx, sz);
				}
			}
			gatehouse();
			keep();
			if (size != Size.SMALL) {
				barracks();
				storehouse();
				well();
			}
			if (size == Size.LARGE) {
				garden();
			}
			approach();
			if (theme.vines) {
				vines();
			}
		}

		// ---- Placing blocks ----

		private BlockPos world(int x, int y, int z) {
			return switch (rotation) {
				case NONE -> new BlockPos(centerX + x, floorY + y, centerZ + z);
				case CLOCKWISE_90 -> new BlockPos(centerX - z, floorY + y, centerZ + x);
				case CLOCKWISE_180 -> new BlockPos(centerX - x, floorY + y, centerZ - z);
				case COUNTERCLOCKWISE_90 -> new BlockPos(centerX + z, floorY + y, centerZ - x);
			};
		}

		/** Places a block given in local coordinates and facings, if it's in the chunk being built. */
		private void put(int x, int y, int z, BlockState state) {
			BlockPos pos = world(x, y, z);
			if (!chunkBB.isInside(pos)) {
				return;
			}
			BlockState turned = state.rotate(rotation);
			level.setBlock(pos, turned, Block.UPDATE_CLIENTS);
			Block block = turned.getBlock();
			if (block instanceof CrossCollisionBlock || block instanceof WallBlock || block instanceof StairBlock) {
				level.getChunk(pos).markPosForPostProcessing(pos);
			}
		}

		private void put(int x, int y, int z, Block block) {
			put(x, y, z, block.defaultBlockState());
		}

		private void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
					for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
						put(x, y, z, state);
					}
				}
			}
		}

		private void chest(int x, int y, int z, Direction facing, ResourceKey<LootTable> lootTable) {
			put(x, y, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
			BlockPos pos = world(x, y, z);
			if (chunkBB.isInside(pos)) {
				RandomizableContainer.setBlockEntityLootTable(level, RandomSource.create(seed ^ pos.asLong()), pos, lootTable);
			}
		}

		/** Same number for the same spot in the same castle, so every chunk agrees. */
		private int hash(int x, int y, int z) {
			long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
			h ^= h >>> 33;
			h *= 0xFF51AFD7ED558CCDL;
			h ^= h >>> 33;
			return (int) (h & 0x7FFFFFFF);
		}

		private boolean chance(int x, int y, int z, int percent) {
			return hash(x, y, z) % 100 < percent;
		}

		/** A weathered wall block: mostly plain, some mossy, some cracked. */
		private BlockState stone(int x, int y, int z) {
			int roll = hash(x, y, z) % 100;
			Block block = roll < 14 ? theme.mossy : roll < 26 ? theme.cracked : theme.wall;
			return block.defaultBlockState();
		}

		private void stoneColumn(int x, int z, int y0, int y1) {
			for (int y = y0; y <= y1; y++) {
				put(x, y, z, stone(x, y, z));
			}
		}

		private BlockState stairs(Block block, Direction facing) {
			return block.defaultBlockState().setValue(StairBlock.FACING, facing);
		}

		private BlockState hangingLantern() {
			return theme.lantern.defaultBlockState().setValue(LanternBlock.HANGING, true);
		}

		private BlockState ladder(Direction facing) {
			return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
		}

		private BlockState banner(Direction facing) {
			return theme.banner.defaultBlockState().setValue(WallBannerBlock.FACING, facing);
		}

		private void door(int x, int z, Direction facing) {
			BlockState door = theme.door.defaultBlockState().setValue(DoorBlock.FACING, facing);
			put(x, 1, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			put(x, 2, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		}

		// ---- Parts ----

		private void courtyard() {
			int inner = half - 2;
			for (int x = -inner; x <= inner; x++) {
				for (int z = -inner; z <= inner; z++) {
					put(x, 0, z, chance(x, 0, z, 30) ? theme.pathAlt : theme.path);
				}
			}
		}

		/** Two blocks thick, walkable on top behind a row of merlons. */
		private void curtainWalls() {
			for (int x = -half; x <= half; x++) {
				for (int z = -half; z <= half; z++) {
					int ring = Math.max(Math.abs(x), Math.abs(z));
					if (ring < half - 1) {
						continue;
					}
					stoneColumn(x, z, -3, 7);
					if (ring == half) {
						int along = Math.abs(z) == half ? x : z;
						put(x, 8, z, Math.floorMod(along, 2) == 0 ? stone(x, 8, z) : air);
					}
				}
			}
		}

		/** Round, three stories: ground floor, a floor level with the wall walk, and a lookout on top. */
		private void cornerTower(int sx, int sz) {
			int cx = sx * (half - 1);
			int cz = sz * (half - 1);
			BlockState planks = theme.planks.defaultBlockState();
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					int d2 = dx * dx + dz * dz;
					int x = cx + dx;
					int z = cz + dz;
					if (d2 > 12) {
						continue;
					}
					if (d2 > 6) {
						stoneColumn(x, z, -3, 12);
						put(x, 13, z, Math.floorMod(dx + dz, 2) == 0 ? stone(x, 13, z) : air);
					} else {
						put(x, 0, z, theme.floor);
						fill(x, 1, z, x, 11, z, air);
						put(x, 7, z, planks);
						put(x, 12, z, planks);
						if (d2 >= 4 && chance(x, 11, z, 25)) {
							put(x, 11, z, Blocks.COBWEB);
						}
					}
				}
			}
			// Ladder up the outer side, all the way to the lookout.
			Direction ladderFacing = sz > 0 ? Direction.NORTH : Direction.SOUTH;
			fill(cx, 1, cz + 2 * sz, cx, 12, cz + 2 * sz, ladder(ladderFacing));
			// A doorway from the courtyard, and openings onto both wall walks.
			fill(cx - 2 * sx, 1, cz - 2 * sz, cx - 2 * sx, 2, cz - 2 * sz, air);
			fill(cx - 3 * sx, 8, cz, cx - 3 * sx, 9, cz, air);
			fill(cx, 8, cz - 3 * sz, cx, 9, cz - 3 * sz, air);
			put(cx, 1, cz, theme.lantern);
			put(cx, 8, cz, theme.lantern);
			if (size == Size.SMALL || chance(cx, 1, cz, 60)) {
				chest(cx + 2 * sx, 1, cz, sx > 0 ? Direction.WEST : Direction.EAST, STORAGE_LOOT);
			}
		}

		/** Two square towers flanking the gate, with a portcullis hanging over it. */
		private void gatehouse() {
			BlockState planks = theme.planks.defaultBlockState();
			int front = half + 2;
			int back = half - 2;
			for (int side = -1; side <= 1; side += 2) {
				int near = side * 2;
				int far = side * 6;
				for (int x = Math.min(near, far); x <= Math.max(near, far); x++) {
					for (int z = back; z <= front; z++) {
						boolean edgeX = x == near || x == far;
						boolean edgeZ = z == back || z == front;
						if (edgeX || edgeZ) {
							if (edgeX && edgeZ) {
								fill(x, -3, z, x, 11, z, theme.trim.defaultBlockState());
							} else {
								stoneColumn(x, z, -3, 11);
							}
							put(x, 12, z, Math.floorMod(x + z, 2) == 0 ? stone(x, 12, z) : air);
						} else {
							put(x, 0, z, theme.floor);
							fill(x, 1, z, x, 10, z, air);
							put(x, 7, z, planks);
							put(x, 11, z, planks);
						}
					}
				}
				int mid = side * 4;
				fill(mid, 1, front - 1, mid, 11, front - 1, ladder(Direction.NORTH));
				fill(mid, 1, back, mid, 2, back, air);
				fill(far, 8, half - 1, far, 9, half - 1, air);
				put(mid, 1, half, theme.lantern);
				put(mid, 8, front + 1, banner(Direction.SOUTH));
			}
			// The gate: three wide, three tall, under a row of iron bars.
			fill(-1, 1, half - 1, 1, 4, half, air);
			fill(-1, 4, half, 1, 4, half, Blocks.IRON_BARS.defaultBlockState());
			fill(-1, 0, back, 1, 0, front, theme.path.defaultBlockState());
		}

		/** The keep: one tall hall with the throne at the back, and the sealed vault behind it. */
		private void keep() {
			int kx = switch (size) {
				case SMALL -> 5;
				case MEDIUM -> 7;
				case LARGE -> 9;
			};
			int depth = switch (size) {
				case SMALL -> 9;
				case MEDIUM -> 12;
				case LARGE -> 15;
			};
			int height = size == Size.LARGE ? 10 : 8;
			int back = -half + 3;
			int front = back + depth;

			for (int x = -kx; x <= kx; x++) {
				for (int z = back; z <= front; z++) {
					boolean edgeX = Math.abs(x) == kx;
					boolean edgeZ = z == back || z == front;
					if (edgeX || edgeZ) {
						if (edgeX && edgeZ) {
							fill(x, -3, z, x, height, z, theme.trim.defaultBlockState());
						} else {
							stoneColumn(x, z, -3, height);
						}
						put(x, height + 1, z, Math.floorMod(x + z, 2) == 0 ? stone(x, height + 1, z) : air);
					} else {
						put(x, 0, z, theme.floor);
						fill(x, 1, z, x, height - 1, z, air);
						put(x, height, z, stone(x, height, z));
					}
				}
			}

			// Door and windows.
			fill(-1, 1, front, 1, 3, front, air);
			put(0, 4, front, air);
			for (int z = back + 3; z < front - 1; z += 4) {
				fill(-kx, 3, z, -kx, 4, z, theme.pane.defaultBlockState());
				fill(kx, 3, z, kx, 4, z, theme.pane.defaultBlockState());
			}
			fill(-kx + 2, 3, front, -kx + 2, 4, front, theme.pane.defaultBlockState());
			fill(kx - 2, 3, front, kx - 2, 4, front, theme.pane.defaultBlockState());

			// The dais, with steps up, the throne, and the vault set into the back of it.
			BlockState plain = theme.wall.defaultBlockState();
			fill(-2, 1, back + 1, 2, 1, back + 2, plain);
			fill(-2, 1, back + 3, 2, 1, back + 3, stairs(theme.stairs, Direction.NORTH));
			put(0, 2, back + 2, stairs(theme.stairs, Direction.NORTH));
			put(-1, 2, back + 2, theme.slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
			put(1, 2, back + 2, theme.slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
			put(0, 2, back + 1, ModBlocks.SEALED_VAULT.defaultBlockState()
				.setValue(SealedVaultBlock.FACING, Direction.SOUTH)
				.setValue(SealedVaultBlock.THEME, theme));
			fill(-1, 2, back + 1, -1, 3, back + 1, theme.trim.defaultBlockState());
			fill(1, 2, back + 1, 1, 3, back + 1, theme.trim.defaultBlockState());
			put(-1, 4, back + 1, theme.lantern);
			put(1, 4, back + 1, theme.lantern);
			put(-3, 5, back + 1, banner(Direction.SOUTH));
			put(3, 5, back + 1, banner(Direction.SOUTH));

			// A carpet runner from the door to the steps, and light.
			int runner = size == Size.LARGE ? 1 : 0;
			fill(-runner, 1, back + 4, runner, 1, front - 1, theme.carpet.defaultBlockState());
			int middle = (back + front) / 2;
			put(0, height - 1, middle, hangingLantern());
			put(-kx / 2 - 1, height - 1, middle, hangingLantern());
			put(kx / 2 + 1, height - 1, middle, hangingLantern());
			put(-kx + 1, 1, front - 1, theme.lantern);
			put(kx - 1, 1, front - 1, theme.lantern);
			put(-kx + 1, 1, back + 1, theme.lantern);
			put(kx - 1, 1, back + 1, theme.lantern);
			for (int z = back + 4; z < front; z += 4) {
				fill(-kx + 1, 1, z, -kx + 1, height - 1, z, theme.log.defaultBlockState());
				fill(kx - 1, 1, z, kx - 1, height - 1, z, theme.log.defaultBlockState());
			}
		}

		/** A timber hall against the west wall with bunks and an armory chest. */
		private void barracks() {
			int west = -half + 2;
			int east = -half + 6;
			hall(west, east);
			door(east, 0, Direction.EAST);
			for (int z = -3; z <= 3; z += 2) {
				put(west + 1, 1, z, Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.WEST).setValue(BedBlock.PART, BedPart.HEAD));
				put(west + 2, 1, z, Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.WEST).setValue(BedBlock.PART, BedPart.FOOT));
			}
			chest(east - 1, 1, -3, Direction.WEST, ARMORY_LOOT);
			put(east - 1, 1, 3, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
			put(west + 2, 4, 0, hangingLantern());
		}

		/** A timber hall against the east wall with supplies. */
		private void storehouse() {
			int west = half - 6;
			int east = half - 2;
			hall(west, east);
			door(west, 0, Direction.WEST);
			chest(east - 1, 1, -3, Direction.WEST, STORAGE_LOOT);
			chest(east - 1, 1, 3, Direction.WEST, STORAGE_LOOT);
			put(east - 1, 1, -1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
			put(east - 1, 1, 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
			put(east - 1, 1, 0, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.WEST));
			put(west + 1, 1, -3, Blocks.CRAFTING_TABLE);
			put(west + 1, 1, 3, Blocks.HAY_BLOCK);
			put(west + 2, 4, 0, hangingLantern());
		}

		/** Plank walls on a log frame, nine long and five wide, roofed with planks. */
		private void hall(int west, int east) {
			BlockState planks = theme.planks.defaultBlockState();
			BlockState log = theme.log.defaultBlockState();
			for (int x = west; x <= east; x++) {
				for (int z = -4; z <= 4; z++) {
					boolean edgeX = x == west || x == east;
					boolean edgeZ = z == -4 || z == 4;
					put(x, 0, z, planks);
					fill(x, 1, z, x, 4, z, edgeX && edgeZ ? log : edgeX || edgeZ ? planks : air);
					put(x, 5, z, planks);
					if (edgeX || edgeZ) {
						put(x, 6, z, theme.woodSlab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
					}
				}
			}
			int open = west == -half + 2 ? east : west;
			fill(open, 2, -2, open, 3, -2, theme.pane.defaultBlockState());
			fill(open, 2, 2, open, 3, 2, theme.pane.defaultBlockState());
		}

		private int courtyardMiddle() {
			int keepFront = -half + 3 + (size == Size.LARGE ? 15 : 12);
			return (keepFront + half) / 2;
		}

		private void well() {
			int cx = 5;
			int cz = courtyardMiddle();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int x = cx + dx;
					int z = cz + dz;
					if (dx == 0 && dz == 0) {
						fill(x, -3, z, x, 0, z, Blocks.WATER.defaultBlockState());
					} else {
						put(x, 0, z, stone(x, 0, z));
						put(x, 1, z, stone(x, 1, z));
					}
					put(x, 4, z, theme.woodSlab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
				}
			}
			for (int dx = -1; dx <= 1; dx += 2) {
				for (int dz = -1; dz <= 1; dz += 2) {
					fill(cx + dx, 2, cz + dz, cx + dx, 3, cz + dz, theme.fence.defaultBlockState());
				}
			}
		}

		/** Large castles grow wheat on a watered plot across from the well. */
		private void garden() {
			int cz = courtyardMiddle();
			BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE);
			for (int x = -13; x <= -9; x++) {
				for (int z = cz - 1; z <= cz + 1; z++) {
					if (x == -11 && z == cz) {
						put(x, 0, z, Blocks.WATER);
						continue;
					}
					put(x, 0, z, farmland);
					put(x, 1, z, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 2 + hash(x, 1, z) % 6));
				}
			}
			put(-11, 1, cz, Blocks.LILY_PAD);
			put(-14, 1, cz, Blocks.COMPOSTER);
		}

		/** A path from the gate down the hill, and a bridge over the moat. */
		private void approach() {
			BlockState path = theme.path.defaultBlockState();
			int flat = layout.flatRadius();
			int hillEnd = layout.hillRadius();
			for (int x = -1; x <= 1; x++) {
				for (int z = half + 1; z <= flat; z++) {
					put(x, 0, z, path);
				}
				int previous = 0;
				for (int z = flat + 1; z <= hillEnd; z++) {
					int y = hillY(distance(x, z)) - floorY;
					put(x, y, z, path);
					if (y == previous - 1) {
						put(x, y + 1, z, stairs(theme.stairs, Direction.NORTH));
					}
					previous = y;
				}
			}

			int deck = baseY - floorY;
			int bed = waterY - 3 - floorY;
			int end = layout.bankRadius() + 1;
			BlockState planks = theme.planks.defaultBlockState();
			BlockState log = theme.log.defaultBlockState();
			BlockState fence = theme.fence.defaultBlockState();
			for (int z = hillEnd + 1; z <= end; z++) {
				fill(-1, deck, z, 1, deck, z, planks);
				fill(-1, deck + 1, z, 1, deck + 3, z, air);
				for (int side = -2; side <= 2; side += 4) {
					put(side, deck, z, log);
					put(side, deck + 1, z, fence);
					if ((z - hillEnd) % 3 == 1) {
						fill(side, bed, z, side, deck - 1, z, log);
					}
				}
			}
			for (int side = -2; side <= 2; side += 4) {
				put(side, deck + 2, hillEnd + 1, theme.lantern);
				put(side, deck + 2, end, theme.lantern);
			}
		}

		/** Vines trailing down the outside of the walls, here and there. */
		private void vines() {
			int reach = half - 4;
			for (int a = -reach; a <= reach; a++) {
				vineColumn(a, half + 1, Direction.NORTH, a, 0);
				vineColumn(a, -half - 1, Direction.SOUTH, a, 1);
				vineColumn(half + 1, a, Direction.WEST, a, 2);
				vineColumn(-half - 1, a, Direction.EAST, a, 3);
			}
		}

		/** A vine hanging at x, z, stuck to the wall on its attached side. */
		private void vineColumn(int x, int z, Direction attached, int along, int side) {
			if (side == 0 && Math.abs(along) <= 7) {
				return;
			}
			if (!chance(along, side, 77, 18)) {
				return;
			}
			BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(attached), true);
			int length = 2 + hash(along, side, 78) % 5;
			for (int y = 7; y > 7 - length; y--) {
				put(x, y, z, vine);
			}
		}
	}
}
