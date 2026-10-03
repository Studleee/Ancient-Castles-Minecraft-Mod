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
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BannerBlock;
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
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * One whole castle: a hill raised out of the ground with a water moat around its foot, and on top a square curtain
 * wall with round corner towers, a gatehouse facing the bridge, and a two-story keep with a central tower, the throne
 * room and its sealed vault, and a dungeon underneath. The courtyard grows with the castle's size: gardens, a
 * fountain, barracks, a storehouse, a blacksmith, market stalls, and stables. Guards keep watch on the walls.
 * <p>
 * Built in local coordinates: x/z from the castle's center with the gate toward +z (south), y from the hilltop
 * floor. They're turned by {@link #rotation} when placed. Each chunk the castle touches builds only its own part.
 */
public class CastlePiece extends StructurePiece {
	public static final ResourceKey<LootTable> STORAGE_LOOT = loot("castle_storage");
	public static final ResourceKey<LootTable> ARMORY_LOOT = loot("castle_armory");
	public static final ResourceKey<LootTable> DUNGEON_LOOT = loot("castle_dungeon");
	public static final ResourceKey<LootTable> TREASURE_LOOT = loot("castle_treasure");

	/** How far up and down from the hilltop to cut away terrain or fill it in. */
	private static final int CLEAR_ABOVE = 32;
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

		// The keep, shared by the keep, its dungeon, and the courtyard layout.
		private final int keepX = switch (size) {
			case SMALL -> 5;
			case MEDIUM -> 7;
			case LARGE -> 9;
		};
		private final int keepBack = -half + 3;
		private final int keepFront = keepBack + switch (size) {
			case SMALL -> 9;
			case MEDIUM -> 12;
			case LARGE -> 15;
		};
		/** The throne hall's ceiling (the upper floor), and the keep's roof. */
		private final int hallTop = size == Size.LARGE ? 8 : 7;
		private final int roof = hallTop + 5;
		private final int keepMiddle = Math.floorDiv(keepBack + keepFront, 2);
		/** How many steps down to the dungeon. */
		private final int dungeonDepth = size == Size.SMALL ? 6 : 8;

		Builder(WorldGenLevel level, BoundingBox chunkBB) {
			this.level = level;
			this.chunkBB = chunkBB;
		}

		void build() {
			courtyard();
			curtainWalls();
			wallLights();
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					cornerTower(sx, sz);
				}
			}
			gatehouse();
			dungeon();
			keep();
			courtyardFeatures();
			approach();
			if (theme.vines) {
				vines();
			}
			guards();
		}

		// ---- Placing blocks and mobs ----

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

		private void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
			fill(x0, y0, z0, x1, y1, z1, block.defaultBlockState());
		}

		private void chest(int x, int y, int z, Direction facing, ResourceKey<LootTable> lootTable) {
			put(x, y, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
			BlockPos pos = world(x, y, z);
			if (chunkBB.isInside(pos)) {
				RandomizableContainer.setBlockEntityLootTable(level, RandomSource.create(seed ^ pos.asLong()), pos, lootTable);
			}
		}

		private void spawner(int x, int y, int z, EntityType<?> type) {
			put(x, y, z, Blocks.SPAWNER);
			BlockPos pos = world(x, y, z);
			if (chunkBB.isInside(pos) && level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
				spawner.setEntityId(type, RandomSource.create(seed ^ pos.asLong()));
			}
		}

		/** A mob that stays put (never despawns). Undead get a helmet so they don't burn by day. */
		private void mob(EntityType<? extends Mob> type, int x, int y, int z) {
			BlockPos pos = world(x, y, z);
			if (!chunkBB.isInside(pos)) {
				return;
			}
			Mob mob = type.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
			if (mob == null) {
				return;
			}
			mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, hash(x, y, z) % 360, 0.0F);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
			mob.stopRiding();
			mob.setBaby(false);
			if (mob.is(EntityTypeTags.UNDEAD) && mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
			}
			mob.setPersistenceRequired();
			level.addFreshEntityWithPassengers(mob);
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

		/** Upside-down stairs, its tall side toward facing: an overhang or the corner of an arch. */
		private BlockState corbel(Direction facing) {
			return stairs(theme.stairs, facing).setValue(StairBlock.HALF, Half.TOP);
		}

		private BlockState bottomSlab(Block block) {
			return block.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
		}

		private BlockState topSlab(Block block) {
			return block.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
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

		/** A torch on the wall behind it, which is on the side opposite facing. */
		private BlockState torch(Direction facing) {
			return theme.wallTorch().defaultBlockState().setValue(WallTorchBlock.FACING, facing);
		}

		private void door(int x, int z, Direction facing) {
			BlockState door = theme.door.defaultBlockState().setValue(DoorBlock.FACING, facing);
			put(x, 1, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			put(x, 2, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		}

		/** A fence post with a lantern on top, standing on the ground at height y. */
		private void lampPost(int x, int y, int z) {
			fill(x, y + 1, z, x, y + 2, z, theme.fence);
			put(x, y + 3, z, theme.lantern);
		}

		/** A chain from the ceiling, a cross of fences, and a lantern hanging from each arm. */
		private void chandelier(int x, int chainY, int z) {
			put(x, chainY, z, Blocks.IRON_CHAIN);
			put(x, chainY - 1, z, theme.fence);
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				int ax = x + direction.getStepX();
				int az = z + direction.getStepZ();
				put(ax, chainY - 1, az, theme.fence);
				put(ax, chainY - 2, az, hangingLantern());
			}
		}

		// ---- Walls and towers ----

		private void courtyard() {
			int inner = half - 2;
			for (int x = -inner; x <= inner; x++) {
				for (int z = -inner; z <= inner; z++) {
					put(x, 0, z, chance(x, 0, z, 30) ? theme.pathAlt : theme.path);
				}
			}
		}

		/** Where along a wall side a buttress stands: every sixth block, clear of the towers and the gate. */
		private boolean buttress(int along, boolean gateSide) {
			return Math.floorMod(along, 6) == 3 && Math.abs(along) <= half - 5 && !(gateSide && Math.abs(along) <= 8);
		}

		/**
		 * Two blocks thick, walkable on top behind a row of merlons that overhang on stone brackets, with buttresses
		 * down the outside.
		 */
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
			for (int a = -half; a <= half; a++) {
				put(a, 7, half + 1, corbel(Direction.NORTH));
				put(a, 7, -half - 1, corbel(Direction.SOUTH));
				put(half + 1, 7, a, corbel(Direction.WEST));
				put(-half - 1, 7, a, corbel(Direction.EAST));
				if (buttress(a, true)) {
					stoneColumn(a, half + 1, -3, 5);
					put(a, 6, half + 1, stairs(theme.stairs, Direction.NORTH));
				}
				if (buttress(a, false)) {
					stoneColumn(a, -half - 1, -3, 5);
					put(a, 6, -half - 1, stairs(theme.stairs, Direction.SOUTH));
					stoneColumn(half + 1, a, -3, 5);
					put(half + 1, 6, a, stairs(theme.stairs, Direction.WEST));
					stoneColumn(-half - 1, a, -3, 5);
					put(-half - 1, 6, a, stairs(theme.stairs, Direction.EAST));
				}
			}
		}

		/** Lanterns on the merlons and torches along the inside of the walls, so the castle glows at night. */
		private void wallLights() {
			int inner = half - 2;
			for (int a = -half + 4; a <= half - 4; a++) {
				if (Math.floorMod(a, 6) == 0) {
					put(a, 9, half, theme.lantern);
					put(a, 9, -half, theme.lantern);
					put(half, 9, a, theme.lantern);
					put(-half, 9, a, theme.lantern);
				}
				if (Math.floorMod(a, 6) == 3) {
					put(a, 3, inner, torch(Direction.NORTH));
					put(a, 3, -inner, torch(Direction.SOUTH));
					put(inner, 3, a, torch(Direction.WEST));
					put(-inner, 3, a, torch(Direction.EAST));
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
			// A doorway from the courtyard, openings onto both wall walks, and arrow slits looking out.
			fill(cx - 2 * sx, 1, cz - 2 * sz, cx - 2 * sx, 2, cz - 2 * sz, air);
			fill(cx - 3 * sx, 8, cz, cx - 3 * sx, 9, cz, air);
			fill(cx, 8, cz - 3 * sz, cx, 9, cz - 3 * sz, air);
			fill(cx + 3 * sx, 3, cz, cx + 3 * sx, 4, cz, air);
			fill(cx + 3 * sx, 9, cz, cx + 3 * sx, 10, cz, air);
			put(cx, 1, cz, theme.lantern);
			put(cx, 8, cz, theme.lantern);
			put(cx, 13, cz, theme.lantern);
			if (size == Size.SMALL || chance(cx, 1, cz, 60)) {
				chest(cx + 2 * sx, 1, cz, sx > 0 ? Direction.WEST : Direction.EAST, STORAGE_LOOT);
			}
		}

		/** Two square towers flanking an arched gate, with a portcullis hanging in it. */
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
								fill(x, -3, z, x, 11, z, theme.trim);
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
				fill(mid, 3, front, mid, 4, front, air);
				put(mid, 1, half, theme.lantern);
				put(mid, 12, half, theme.lantern);
				put(mid, 8, front + 1, banner(Direction.SOUTH));
				put(near + side, 4, front + 1, torch(Direction.SOUTH));
			}
			// The gate: three wide and five tall, with an arch at the top and the portcullis half down.
			for (int x = -1; x <= 1; x++) {
				for (int z = back; z <= front; z++) {
					stoneColumn(x, z, 6, 11);
					put(x, 12, z, Math.floorMod(x + z, 2) == 0 ? stone(x, 12, z) : air);
				}
			}
			fill(-1, 1, back, 1, 5, front, air);
			for (int z = back; z <= front; z++) {
				put(-1, 5, z, corbel(Direction.WEST));
				put(1, 5, z, corbel(Direction.EAST));
			}
			fill(-1, 4, half, 1, 4, half, Blocks.IRON_BARS);
			fill(-1, 0, back, 1, 0, front, theme.path);
		}

		// ---- The keep ----

		/**
		 * Two stories and a tower: the throne hall below, a great hall with a library, a dining table, and the lord's
		 * bed above, a lookout tower rising from the roof with the castle's flag, and the dungeon underneath.
		 */
		private void keep() {
			int kx = keepX;
			int back = keepBack;
			int front = keepFront;

			for (int x = -kx; x <= kx; x++) {
				for (int z = back; z <= front; z++) {
					boolean edgeX = Math.abs(x) == kx;
					boolean edgeZ = z == back || z == front;
					if (edgeX || edgeZ) {
						if (edgeX && edgeZ) {
							fill(x, -3, z, x, roof, z, theme.trim);
						} else {
							stoneColumn(x, z, -3, roof);
						}
						put(x, roof + 1, z, Math.floorMod(x + z, 2) == 0 ? stone(x, roof + 1, z) : air);
					} else {
						put(x, 0, z, theme.floor);
						fill(x, 1, z, x, roof - 1, z, air);
						put(x, hallTop, z, theme.planks);
						put(x, roof, z, stone(x, roof, z));
					}
				}
			}
			// A band of trim between the stories, and corbels under the merlons.
			for (int x = -kx; x <= kx; x++) {
				put(x, hallTop, front + 1, corbel(Direction.NORTH));
				put(x, roof, front + 1, corbel(Direction.NORTH));
				put(x, roof, back - 1, corbel(Direction.SOUTH));
			}
			for (int z = back; z <= front; z++) {
				put(kx + 1, roof, z, corbel(Direction.WEST));
				put(-kx - 1, roof, z, corbel(Direction.EAST));
			}

			throneHall(kx, back, front);
			upperFloor(kx, back, front);
			centralTower();
		}

		private void throneHall(int kx, int back, int front) {
			// An arched double door, and tall stained glass windows.
			fill(-1, 1, front, 1, 4, front, air);
			put(-1, 4, front, corbel(Direction.WEST));
			put(1, 4, front, corbel(Direction.EAST));
			put(-2, 3, front + 1, torch(Direction.SOUTH));
			put(2, 3, front + 1, torch(Direction.SOUTH));
			for (int z = back + 2; z < front - 1; z += 4) {
				fill(-kx, 2, z, -kx, 5, z, theme.pane);
				fill(kx, 2, z, kx, 5, z, theme.pane);
			}
			fill(-kx + 2, 2, front, -kx + 2, 5, front, theme.pane);
			fill(kx - 2, 2, front, kx - 2, 5, front, theme.pane);

			// Pillars along both sides, with tapestries hung from the west ones (the stairs up run along the east).
			for (int z = back + 4; z < front - 1; z += 4) {
				fill(-kx + 1, 1, z, -kx + 1, hallTop - 2, z, theme.trim);
				fill(kx - 1, 1, z, kx - 1, hallTop - 2, z, theme.trim);
				put(-kx + 1, hallTop - 1, z, corbel(Direction.WEST));
				put(kx - 1, hallTop - 1, z, corbel(Direction.EAST));
				put(-kx + 2, hallTop - 2, z, banner(Direction.EAST));
			}

			// The dais: gold at its corners, carpeted steps, the throne, and the vault set into the back of it.
			BlockState plain = theme.wall.defaultBlockState();
			fill(-2, 1, back + 1, 2, 1, back + 2, plain);
			fill(-2, 1, back + 1, -2, 1, back + 2, Blocks.GOLD_BLOCK);
			fill(2, 1, back + 1, 2, 1, back + 2, Blocks.GOLD_BLOCK);
			fill(-2, 2, back + 1, -2, 2, back + 2, theme.carpet);
			fill(2, 2, back + 1, 2, 2, back + 2, theme.carpet);
			fill(-2, 1, back + 3, 2, 1, back + 3, stairs(theme.stairs, Direction.NORTH));
			put(0, 2, back + 2, stairs(theme.stairs, Direction.NORTH));
			put(-1, 2, back + 2, bottomSlab(theme.slab));
			put(1, 2, back + 2, bottomSlab(theme.slab));
			put(0, 2, back + 1, ModBlocks.SEALED_VAULT.defaultBlockState()
				.setValue(SealedVaultBlock.FACING, Direction.SOUTH)
				.setValue(SealedVaultBlock.THEME, theme));
			fill(-1, 2, back + 1, -1, 3, back + 1, theme.trim);
			fill(1, 2, back + 1, 1, 3, back + 1, theme.trim);
			put(-1, 4, back + 1, theme.lantern);
			put(1, 4, back + 1, theme.lantern);
			put(0, 3, back + 1, theme.trim);
			put(0, 4, back + 1, Blocks.GOLD_BLOCK);
			put(-3, 4, back + 1, banner(Direction.SOUTH));
			put(3, 4, back + 1, banner(Direction.SOUTH));

			// A carpet runner from the door to the steps, chandeliers, and lanterns in the corners.
			int runner = size == Size.LARGE ? 1 : 0;
			fill(-runner, 1, back + 4, runner, 1, front - 1, theme.carpet);
			int lightsFrom = back + 5;
			int lightsTo = front - 2;
			if (size == Size.LARGE) {
				chandelier(0, hallTop - 1, lightsFrom + 1);
				chandelier(0, hallTop - 1, lightsTo - 1);
			} else {
				chandelier(0, hallTop - 1, (lightsFrom + lightsTo) / 2);
			}
			put(-kx + 1, 1, front - 1, theme.lantern);
			put(kx - 1, 1, front - 1, theme.lantern);
			put(-kx + 1, 1, back + 1, theme.lantern);
			put(kx - 1, 1, back + 1, theme.lantern);

			// Stairs up along the east side, through an opening in the ceiling.
			int stairX = kx - 2;
			for (int k = 0; k < hallTop; k++) {
				int z = front - 1 - k;
				put(stairX, 1 + k, z, stairs(theme.stairs, Direction.NORTH));
				if (k >= hallTop - 3 && k < hallTop - 1) {
					put(stairX, hallTop, z, air);
				}
			}

			// Stairs down to the dungeon along the west side, through a gap in the floor.
			for (int k = 0; k < dungeonDepth; k++) {
				int z = front - 1 - k;
				for (int x = -kx + 2; x <= -kx + 3; x++) {
					put(x, -k, z, stairs(theme.stairs, Direction.SOUTH));
					for (int y = -k + 1; y <= Math.min(0, -k + 2); y++) {
						put(x, y, z, air);
					}
				}
			}
		}

		private void upperFloor(int kx, int back, int front) {
			int floor = hallTop;
			int ceiling = roof;
			for (int z = back + 2; z < front - 1; z += 4) {
				fill(-kx, floor + 2, z, -kx, floor + 3, z, theme.pane);
				fill(kx, floor + 2, z, kx, floor + 3, z, theme.pane);
			}
			fill(-kx + 2, floor + 2, front, -kx + 2, floor + 3, front, theme.pane);
			fill(kx - 2, floor + 2, front, kx - 2, floor + 3, front, theme.pane);

			// A railing beside the stairwell.
			int stairX = kx - 2;
			int top = front - hallTop;
			fill(stairX - 1, floor + 1, top + 1, stairX - 1, floor + 1, top + 2, theme.fence);
			put(stairX, floor + 1, top + 3, theme.fence);

			// The library: bookshelves along the west and back walls, an enchanting table, and a lectern.
			for (int z = back + 1; z <= keepMiddle; z++) {
				if (Math.floorMod(z - back - 2, 4) != 0) {
					fill(-kx + 1, floor + 1, z, -kx + 1, floor + 3, z, Blocks.BOOKSHELF);
				}
			}
			fill(-kx + 1, floor + 1, back + 1, -2, floor + 3, back + 1, Blocks.BOOKSHELF);
			put(-kx + 3, floor + 1, back + 3, Blocks.ENCHANTING_TABLE);
			put(-kx + 3, floor + 1, back + 5, Blocks.LECTERN);

			// The lord's bed and chest along the east wall, clear of the top of the stairs.
			BlockState bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
			put(kx - 1, floor + 1, back + 1, bed.setValue(BedBlock.PART, BedPart.HEAD));
			put(kx - 1, floor + 1, back + 2, bed.setValue(BedBlock.PART, BedPart.FOOT));
			chest(kx - 1, floor + 1, back + 3, Direction.WEST, STORAGE_LOOT);

			// The dining table, with chairs down both sides.
			int tableFrom = keepMiddle + 1;
			int tableTo = front - 2;
			for (int z = tableFrom; z <= tableTo; z++) {
				put(0, floor + 1, z, topSlab(theme.woodSlab));
				put(-1, floor + 1, z, stairs(woodStairs(), Direction.WEST));
				put(1, floor + 1, z, stairs(woodStairs(), Direction.EAST));
			}
			put(0, floor + 2, tableFrom, theme.lantern);
			put(0, floor + 2, tableTo, theme.lantern);
			chandelier(0, ceiling - 1, (tableFrom + tableTo) / 2);
			put(-kx + 1, floor + 1, front - 1, theme.lantern);
			put(kx - 1, floor + 1, front - 1, theme.lantern);
		}

		/** Stairs in the theme's wood, for chairs. */
		private Block woodStairs() {
			return switch (theme) {
				case PLAINS -> Blocks.OAK_STAIRS;
				case TAIGA, SNOWY -> Blocks.SPRUCE_STAIRS;
				case DESERT -> Blocks.BIRCH_STAIRS;
				case BADLANDS, DARK_FOREST -> Blocks.DARK_OAK_STAIRS;
				case JUNGLE -> Blocks.JUNGLE_STAIRS;
				case SWAMP -> Blocks.MANGROVE_STAIRS;
				case SAVANNA -> Blocks.ACACIA_STAIRS;
				case CHERRY -> Blocks.CHERRY_STAIRS;
			};
		}

		/** A square lookout tower on the keep's roof, reached by a ladder from the great hall, flying the flag. */
		private void centralTower() {
			int t = size == Size.LARGE ? 3 : 2;
			int height = size == Size.LARGE ? 10 : 8;
			int cz = keepMiddle;
			int top = roof + height;
			for (int x = -t; x <= t; x++) {
				for (int z = cz - t; z <= cz + t; z++) {
					boolean edge = Math.abs(x) == t || Math.abs(z - cz) == t;
					if (edge) {
						if (Math.abs(x) == t && Math.abs(z - cz) == t) {
							fill(x, roof + 1, z, x, top, z, theme.trim);
						} else {
							stoneColumn(x, z, roof + 1, top);
						}
						put(x, top + 1, z, Math.floorMod(x + z, 2) == 0 ? stone(x, top + 1, z) : air);
					} else {
						fill(x, roof + 1, z, x, top - 1, z, air);
						put(x, top, z, theme.planks);
					}
				}
			}
			int ladderZ = cz - t + 1;
			fill(0, hallTop + 1, cz - t, 0, roof - 1, cz - t, theme.trim);
			fill(0, hallTop + 1, ladderZ, 0, top, ladderZ, ladder(Direction.SOUTH));
			fill(0, roof + 1, cz + t, 0, roof + 2, cz + t, air);
			fill(-t, roof + 4, cz, -t, roof + 5, cz, air);
			fill(t, roof + 4, cz, t, roof + 5, cz, air);
			put(0, roof + 1, cz, theme.lantern);
			put(0, top + 1, cz + 1, theme.flag().defaultBlockState().setValue(BannerBlock.ROTATION, 8));
			put(t, top + 1, cz - t, theme.lantern);
			put(-t, top + 1, cz + t, theme.lantern);
		}

		/** Under the keep: a vaulted hall with prison cells down one side, a spawner, and loot. */
		private void dungeon() {
			int kx = keepX;
			int back = keepBack;
			int front = keepFront;
			int bottom = -dungeonDepth;
			int top = bottom + 5;
			for (int x = -kx; x <= kx; x++) {
				for (int z = back; z <= front; z++) {
					boolean edge = Math.abs(x) == kx || z == back || z == front;
					put(x, bottom, z, stone(x, bottom, z));
					put(x, top, z, stone(x, top, z));
					for (int y = bottom + 1; y < top; y++) {
						put(x, y, z, edge ? stone(x, y, z) : air);
					}
				}
			}
			// The stairwell down from the keep, walled through the ground.
			for (int k = 0; k < dungeonDepth; k++) {
				int z = front - 1 - k;
				for (int y = -k - 1; y <= -k + 2 && y <= 0; y++) {
					if (y > top) {
						put(-kx + 1, y, z, stone(-kx + 1, y, z));
						put(-kx + 4, y, z, stone(-kx + 4, y, z));
					}
				}
				for (int x = -kx + 2; x <= -kx + 3; x++) {
					if (-k - 1 > top) {
						put(x, -k - 1, z, stone(x, -k - 1, z));
					}
					if (-k + 3 < 0 && -k + 3 > top) {
						put(x, -k + 3, z, stone(x, -k + 3, z));
					}
				}
			}

			// Cells along the east side behind iron bars.
			int cellFront = kx - 4;
			for (int z = back + 1; z < front; z++) {
				boolean divider = Math.floorMod(z - back - 1, 4) == 0;
				for (int y = bottom + 1; y < top; y++) {
					put(cellFront, y, z, divider ? stone(cellFront, y, z) : Blocks.IRON_BARS.defaultBlockState());
					if (divider) {
						fill(cellFront + 1, y, z, kx - 1, y, z, stone(cellFront + 1, y, z));
					}
				}
				if (!divider) {
					if (chance(kx - 1, bottom, z, 25)) {
						put(kx - 1, bottom + 1, z, Blocks.BONE_BLOCK);
					}
					if (chance(kx - 1, top, z, 40)) {
						put(kx - 1, top - 1, z, Blocks.COBWEB);
					}
					if (Math.floorMod(z - back - 1, 4) == 2 && chance(kx - 2, bottom, z, 35)) {
						chest(kx - 1, bottom + 1, z, Direction.WEST, DUNGEON_LOOT);
					}
				}
			}

			// The spawner at the back, two chests beside it, and a little light by the stairs.
			spawner(-1, bottom + 1, back + 2, theme.dungeonMob());
			chest(-3, bottom + 1, back + 1, Direction.SOUTH, DUNGEON_LOOT);
			chest(0, bottom + 1, back + 1, Direction.SOUTH, DUNGEON_LOOT);
			put(-kx + 1, bottom + 1, front - 1, theme.lantern);
			for (int x = -kx + 1; x < cellFront; x++) {
				if (chance(x, top, back + 1, 30)) {
					put(x, top - 1, back + 1, Blocks.COBWEB);
				}
			}
		}

		// ---- The courtyard ----

		private int courtyardMiddle() {
			return (keepFront + half) / 2;
		}

		private void courtyardFeatures() {
			int middle = courtyardMiddle();
			switch (size) {
				case SMALL -> {
					garden(6, -4, 8, 1, false);
					garden(-8, -4, -6, 1, false);
					lampPost(5, 0, 5);
					lampPost(-5, 0, 5);
				}
				case MEDIUM -> {
					barracks();
					storehouse();
					fountain(middle);
					blacksmith(-13, 6);
					stall(9, 5, DyeColor.RED);
					stall(9, 9, DyeColor.BLUE);
					garden(9, -10, 12, -6, true);
					garden(-12, -10, -9, -6, true);
				}
				case LARGE -> {
					barracks();
					storehouse();
					fountain(middle);
					blacksmith(9, 14);
					stables();
					wheat(middle);
					stall(4, 0, DyeColor.RED);
					stall(-6, 0, DyeColor.BLUE);
					stall(9, middle - 1, DyeColor.YELLOW);
					garden(11, -15, 14, -6, true);
					garden(-14, -15, -11, -6, true);
				}
			}
		}

		/** A plot of flowers (or cacti in dry lands), with a tree in the middle if there's room and it's green. */
		private void garden(int x0, int z0, int x1, int z1, boolean tree) {
			Block soil = theme.gardenSoil();
			int tx = (x0 + x1) / 2;
			int tz = (z0 + z1) / 2;
			Block[] flowers = theme == CastleTheme.CHERRY
				? new Block[] { Blocks.PINK_PETALS, Blocks.ALLIUM, Blocks.OXEYE_DAISY }
				: new Block[] { Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.ALLIUM };
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					put(x, 0, z, soil);
					if (tree && x == tx && z == tz) {
						continue;
					}
					int roll = hash(x, 1, z) % 100;
					if (theme.dry()) {
						if (roll < 12) {
							fill(x, 1, z, x, 1 + roll % 2, z, Blocks.CACTUS);
						} else if (roll < 30) {
							put(x, 1, z, Blocks.DEAD_BUSH);
						}
					} else if (roll < 45) {
						put(x, 1, z, flowers[roll % flowers.length]);
					}
				}
			}
			Block leaves = theme.leaves();
			if (tree && leaves != null) {
				tree(tx, tz, leaves);
			}
		}

		private void tree(int x, int z, Block leaves) {
			BlockState leaf = leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
			for (int y = 3; y <= 6; y++) {
				int r = y <= 4 ? 2 : 1;
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
						if ((corner && (r == 1 || chance(x + dx, y, z + dz, 50))) || (y == 6 && dx != 0 && dz != 0)) {
							continue;
						}
						put(x + dx, y, z + dz, leaf);
					}
				}
			}
			fill(x, 1, z, x, 5, z, theme.log);
		}

		/** A basin with a spouting pillar in the middle, and lanterns at its corners. */
		private void fountain(int cz) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					int z = cz + dz;
					boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
					put(dx, -1, z, theme.wall);
					if (rim) {
						put(dx, 0, z, theme.wall);
						put(dx, 1, z, theme.trim);
					} else {
						put(dx, 0, z, Blocks.WATER);
						put(dx, 1, z, air);
					}
				}
			}
			fill(0, 0, cz, 0, 2, cz, theme.trim);
			put(0, 3, cz, Blocks.WATER);
			BlockPos spout = world(0, 3, cz);
			if (chunkBB.isInside(spout)) {
				level.scheduleTick(spout, Fluids.WATER, 1);
			}
			for (int dx = -2; dx <= 2; dx += 4) {
				for (int dz = -2; dz <= 2; dz += 4) {
					put(dx, 2, cz + dz, theme.lantern);
				}
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
			put(east + 1, 3, -2, torch(Direction.EAST));
			put(east + 1, 3, 2, torch(Direction.EAST));
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
			put(west - 1, 3, -2, torch(Direction.WEST));
			put(west - 1, 3, 2, torch(Direction.WEST));
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
						put(x, 6, z, bottomSlab(theme.woodSlab));
					}
				}
			}
			int open = west == -half + 2 ? east : west;
			fill(open, 2, -2, open, 3, -2, theme.pane);
			fill(open, 2, 2, open, 3, 2, theme.pane);
		}

		/** An open-sided forge under a roof on log posts: anvil, smithing table, blast furnace, lava, and a chest. */
		private void blacksmith(int x0, int z0) {
			int x1 = x0 + 4;
			int z1 = z0 + 4;
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					put(x, 0, z, stone(x, 0, z));
					boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
					fill(x, 1, z, x, 3, z, corner ? theme.log.defaultBlockState() : air);
					put(x, 4, z, theme.planks);
				}
			}
			put(x0 + 1, 1, z0 + 1, Blocks.ANVIL);
			put(x0 + 2, 1, z0 + 1, Blocks.SMITHING_TABLE);
			put(x0 + 3, 1, z0 + 1, Blocks.BLAST_FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.SOUTH));
			put(x0 + 1, 1, z0 + 3, Blocks.GRINDSTONE);
			put(x0 + 2, 1, z0 + 3, Blocks.LAVA_CAULDRON);
			chest(x0 + 3, 1, z0 + 3, Direction.NORTH, ARMORY_LOOT);
			put(x0 + 2, 3, z0 + 2, hangingLantern());
		}

		/** A market stall: a striped awning on fence posts over a counter and a few goods. */
		private void stall(int x0, int z0, DyeColor color) {
			for (int dx = 0; dx <= 2; dx++) {
				for (int dz = 0; dz <= 2; dz++) {
					boolean corner = dx != 1 && dz != 1;
					if (corner) {
						fill(x0 + dx, 1, z0 + dz, x0 + dx, 2, z0 + dz, theme.fence);
					}
					put(x0 + dx, 3, z0 + dz, Blocks.WOOL.pick(dx == 1 ? DyeColor.WHITE : color));
				}
			}
			put(x0 + 1, 1, z0, topSlab(theme.woodSlab));
			Block[] goods = { Blocks.MELON, Blocks.PUMPKIN, Blocks.HAY_BLOCK };
			put(x0 + 1, 1, z0 + 1, goods[hash(x0, 1, z0) % goods.length]);
			put(x0 + 1, 1, z0 + 2, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
		}

		/** Stables along the south wall with hay, water, and three horses. */
		private void stables() {
			int x0 = -16;
			int x1 = -8;
			int z0 = 14;
			int z1 = 18;
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean edgeX = x == x0 || x == x1;
					boolean corner = edgeX && (z == z0 || z == z1);
					BlockState wall = corner ? theme.log.defaultBlockState()
						: (edgeX || z == z1) ? theme.planks.defaultBlockState() : air;
					fill(x, 1, z, x, 3, z, wall);
					put(x, 4, z, theme.planks);
					if (z == z0 && !edgeX) {
						put(x, 1, z, x == -12 ? Blocks.OAK_FENCE_GATE.defaultBlockState() : theme.fence.defaultBlockState());
					}
				}
			}
			for (int x = x0 + 1; x < x1; x += 2) {
				put(x, 1, z1 - 1, Blocks.HAY_BLOCK);
			}
			put(-12, 1, z1 - 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
			put(-12, 3, z0 + 2, hangingLantern());
			mob(EntityTypes.HORSE, -14, 1, 16);
			mob(EntityTypes.HORSE, -12, 1, 15);
			mob(EntityTypes.HORSE, -10, 1, 16);
		}

		/** Large castles grow wheat on a watered plot across from the stalls. */
		private void wheat(int cz) {
			BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE);
			for (int x = -13; x <= -9; x++) {
				for (int z = cz - 1; z <= cz + 1; z++) {
					if (x == -11 && z == cz) {
						put(x, 0, z, Blocks.WATER);
						put(x, 1, z, Blocks.LILY_PAD);
						continue;
					}
					put(x, 0, z, farmland);
					put(x, 1, z, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 2 + hash(x, 1, z) % 6));
				}
			}
			put(-14, 1, cz, Blocks.COMPOSTER);
		}

		// ---- Outside ----

		/** A path from the gate down the hill lined with lamp posts, and a bridge over the moat. */
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
			for (int z = half + 4; z <= hillEnd - 1; z += 4) {
				for (int side = -2; side <= 2; side += 4) {
					lampPost(side, hillY(distance(side, z)) - floorY, z);
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
				vineColumn(a, half + 1, Direction.NORTH, a, 0, buttress(a, true));
				vineColumn(a, -half - 1, Direction.SOUTH, a, 1, buttress(a, false));
				vineColumn(half + 1, a, Direction.WEST, a, 2, buttress(a, false));
				vineColumn(-half - 1, a, Direction.EAST, a, 3, buttress(a, false));
			}
		}

		/** A vine hanging at x, z under the wall's brackets, stuck to the wall on its attached side. */
		private void vineColumn(int x, int z, Direction attached, int along, int side, boolean blocked) {
			if (blocked || (side == 0 && Math.abs(along) <= 7) || !chance(along, side, 77, 18)) {
				return;
			}
			BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(attached), true);
			int length = 2 + hash(along, side, 78) % 4;
			for (int y = 6; y > 6 - length; y--) {
				put(x, y, z, vine);
			}
		}

		/** Guards on each wall walk and by the gate; large castles have two more in the courtyard. */
		private void guards() {
			EntityType<? extends Mob> guard = theme.guard();
			int walk = half - 1;
			mob(guard, 2, 8, -walk);
			mob(guard, -walk, 8, 2);
			mob(guard, walk, 8, -2);
			mob(guard, 3, 1, half - 4);
			mob(guard, -3, 1, half - 4);
			if (size == Size.LARGE) {
				mob(guard, 6, 1, 4);
				mob(guard, -6, 1, 4);
			}
		}
	}
}
