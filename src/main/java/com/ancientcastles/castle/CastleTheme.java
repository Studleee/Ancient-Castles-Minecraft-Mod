package com.ancientcastles.castle;

import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * How a castle looks, picked from the biome it's built in. Walls are mostly {@link #wall}, weathered with
 * {@link #mossy} and {@link #cracked} blocks.
 */
public enum CastleTheme implements StringRepresentable {
	PLAINS(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS,
		Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.SPRUCE_PLANKS, Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.OAK_SLAB, Blocks.OAK_FENCE, Blocks.OAK_DOOR,
		Blocks.GLASS_PANE, Blocks.CARPET.pick(DyeColor.RED), Blocks.WALL_BANNER.pick(DyeColor.RED), Blocks.GRAVEL, Blocks.COARSE_DIRT,
		Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.GRAVEL, Blocks.LANTERN, true),
	TAIGA(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE, Blocks.STONE_BRICKS,
		Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_SLAB, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_DOOR,
		Blocks.GLASS_PANE, Blocks.CARPET.pick(DyeColor.BROWN), Blocks.WALL_BANNER.pick(DyeColor.BROWN), Blocks.COARSE_DIRT, Blocks.PODZOL,
		Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.GRAVEL, Blocks.LANTERN, true),
	SNOWY(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.PACKED_ICE,
		Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_DOOR,
		Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_BLUE), Blocks.CARPET.pick(DyeColor.LIGHT_BLUE), Blocks.WALL_BANNER.pick(DyeColor.LIGHT_BLUE), Blocks.SNOW_BLOCK, Blocks.GRAVEL,
		Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.GRAVEL, Blocks.LANTERN, false),
	DESERT(Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE,
		Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE, Blocks.BIRCH_PLANKS, Blocks.STRIPPED_BIRCH_LOG, Blocks.BIRCH_SLAB, Blocks.BIRCH_FENCE, Blocks.BIRCH_DOOR,
		Blocks.STAINED_GLASS_PANE.pick(DyeColor.ORANGE), Blocks.CARPET.pick(DyeColor.ORANGE), Blocks.WALL_BANNER.pick(DyeColor.ORANGE), Blocks.SAND, Blocks.SANDSTONE,
		Blocks.SAND, Blocks.SANDSTONE, Blocks.SAND, Blocks.LANTERN, false),
	BADLANDS(Blocks.RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE,
		Blocks.RED_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_SLAB, Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE), Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_DOOR,
		Blocks.STAINED_GLASS_PANE.pick(DyeColor.RED), Blocks.CARPET.pick(DyeColor.RED), Blocks.WALL_BANNER.pick(DyeColor.BLACK), Blocks.RED_SAND, Blocks.TERRACOTTA,
		Blocks.RED_SAND, Blocks.TERRACOTTA, Blocks.RED_SAND, Blocks.LANTERN, false),
	JUNGLE(Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS,
		Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_LOG, Blocks.JUNGLE_SLAB, Blocks.JUNGLE_FENCE, Blocks.JUNGLE_DOOR,
		Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIME), Blocks.CARPET.pick(DyeColor.GREEN), Blocks.WALL_BANNER.pick(DyeColor.GREEN), Blocks.MOSS_BLOCK, Blocks.COARSE_DIRT,
		Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.MUD, Blocks.LANTERN, true),
	SWAMP(Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.MUD_BRICKS, Blocks.MANGROVE_LOG,
		Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB, Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_LOG, Blocks.MANGROVE_SLAB, Blocks.MANGROVE_FENCE, Blocks.MANGROVE_DOOR,
		Blocks.STAINED_GLASS_PANE.pick(DyeColor.BROWN), Blocks.CARPET.pick(DyeColor.GREEN), Blocks.WALL_BANNER.pick(DyeColor.GREEN), Blocks.MUD, Blocks.PACKED_MUD,
		Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.MUD, Blocks.LANTERN, true),
	DARK_FOREST(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE,
		Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_DOOR,
		Blocks.STAINED_GLASS_PANE.pick(DyeColor.GRAY), Blocks.CARPET.pick(DyeColor.PURPLE), Blocks.WALL_BANNER.pick(DyeColor.PURPLE), Blocks.COBBLED_DEEPSLATE, Blocks.GRAVEL,
		Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.GRAVEL, Blocks.SOUL_LANTERN, true),
	SAVANNA(Blocks.BRICKS, Blocks.TERRACOTTA, Blocks.BRICKS, Blocks.STRIPPED_ACACIA_LOG,
		Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB, Blocks.ACACIA_PLANKS, Blocks.ACACIA_PLANKS, Blocks.ACACIA_LOG, Blocks.ACACIA_SLAB, Blocks.ACACIA_FENCE, Blocks.ACACIA_DOOR,
		Blocks.STAINED_GLASS_PANE.pick(DyeColor.YELLOW), Blocks.CARPET.pick(DyeColor.YELLOW), Blocks.WALL_BANNER.pick(DyeColor.YELLOW), Blocks.COARSE_DIRT, Blocks.GRAVEL,
		Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.GRAVEL, Blocks.LANTERN, false),
	CHERRY(Blocks.POLISHED_DIORITE, Blocks.CALCITE, Blocks.DIORITE, Blocks.QUARTZ_PILLAR,
		Blocks.POLISHED_DIORITE_STAIRS, Blocks.POLISHED_DIORITE_SLAB, Blocks.CHERRY_PLANKS, Blocks.CHERRY_PLANKS, Blocks.CHERRY_LOG, Blocks.CHERRY_SLAB, Blocks.CHERRY_FENCE, Blocks.CHERRY_DOOR,
		Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK), Blocks.CARPET.pick(DyeColor.PINK), Blocks.WALL_BANNER.pick(DyeColor.PINK), Blocks.GRAVEL, Blocks.COARSE_DIRT,
		Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.GRAVEL, Blocks.LANTERN, false);

	private static final Set<ResourceKey<Biome>> SNOWY_BIOMES = Set.of(Biomes.SNOWY_PLAINS, Biomes.ICE_SPIKES, Biomes.SNOWY_TAIGA, Biomes.GROVE, Biomes.SNOWY_SLOPES);

	public final Block wall;
	public final Block mossy;
	public final Block cracked;
	public final Block trim;
	public final Block stairs;
	public final Block slab;
	public final Block floor;
	public final Block planks;
	public final Block log;
	public final Block woodSlab;
	public final Block fence;
	public final Block door;
	public final Block pane;
	public final Block carpet;
	public final Block banner;
	public final Block path;
	public final Block pathAlt;
	public final Block groundTop;
	public final Block groundFill;
	public final Block moatBed;
	public final Block lantern;
	public final boolean vines;

	CastleTheme(Block wall, Block mossy, Block cracked, Block trim, Block stairs, Block slab, Block floor, Block planks, Block log,
		Block woodSlab, Block fence, Block door, Block pane, Block carpet, Block banner, Block path, Block pathAlt, Block groundTop,
		Block groundFill, Block moatBed, Block lantern, boolean vines) {
		this.wall = wall;
		this.mossy = mossy;
		this.cracked = cracked;
		this.trim = trim;
		this.stairs = stairs;
		this.slab = slab;
		this.floor = floor;
		this.planks = planks;
		this.log = log;
		this.woodSlab = woodSlab;
		this.fence = fence;
		this.door = door;
		this.pane = pane;
		this.carpet = carpet;
		this.banner = banner;
		this.path = path;
		this.pathAlt = pathAlt;
		this.groundTop = groundTop;
		this.groundFill = groundFill;
		this.moatBed = moatBed;
		this.lantern = lantern;
		this.vines = vines;
	}

	public static CastleTheme forBiome(Holder<Biome> biome) {
		if (SNOWY_BIOMES.stream().anyMatch(biome::is)) {
			return SNOWY;
		}
		if (biome.is(Biomes.DESERT)) {
			return DESERT;
		}
		if (biome.is(BiomeTags.IS_BADLANDS)) {
			return BADLANDS;
		}
		if (biome.is(BiomeTags.IS_JUNGLE)) {
			return JUNGLE;
		}
		if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
			return SWAMP;
		}
		if (biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.PALE_GARDEN)) {
			return DARK_FOREST;
		}
		if (biome.is(BiomeTags.IS_SAVANNA)) {
			return SAVANNA;
		}
		if (biome.is(Biomes.CHERRY_GROVE)) {
			return CHERRY;
		}
		if (biome.is(BiomeTags.IS_TAIGA)) {
			return TAIGA;
		}
		return PLAINS;
	}

	/** Leaves for the courtyard trees, or null where it's too dry for trees. */
	public @Nullable Block leaves() {
		return switch (this) {
			case PLAINS -> Blocks.OAK_LEAVES;
			case TAIGA, SNOWY -> Blocks.SPRUCE_LEAVES;
			case DESERT, BADLANDS -> null;
			case JUNGLE -> Blocks.JUNGLE_LEAVES;
			case SWAMP -> Blocks.MANGROVE_LEAVES;
			case DARK_FOREST -> Blocks.DARK_OAK_LEAVES;
			case SAVANNA -> Blocks.ACACIA_LEAVES;
			case CHERRY -> Blocks.CHERRY_LEAVES;
		};
	}

	/** True where gardens are sand with cacti and dead bushes instead of grass and flowers. */
	public boolean dry() {
		return this == DESERT || this == BADLANDS;
	}

	public Block gardenSoil() {
		return switch (this) {
			case DESERT -> Blocks.SAND;
			case BADLANDS -> Blocks.RED_SAND;
			default -> Blocks.GRASS_BLOCK;
		};
	}

	public Block wallTorch() {
		return lantern == Blocks.SOUL_LANTERN ? Blocks.SOUL_WALL_TORCH : Blocks.WALL_TORCH;
	}

	/** A standing banner in the castle's colors, flown from the top of the keep. */
	public Block flag() {
		return Blocks.BANNER.pick(((AbstractBannerBlock) banner).getColor());
	}

	/** Who keeps watch on the walls. */
	public EntityType<? extends Mob> guard() {
		return switch (this) {
			case PLAINS, DARK_FOREST -> EntityTypes.VINDICATOR;
			case TAIGA, CHERRY -> EntityTypes.PILLAGER;
			case SNOWY -> EntityTypes.STRAY;
			case DESERT -> EntityTypes.HUSK;
			case BADLANDS -> EntityTypes.SKELETON;
			case JUNGLE -> EntityTypes.ZOMBIE;
			case SWAMP -> EntityTypes.BOGGED;
			case SAVANNA -> EntityTypes.WITHER_SKELETON;
		};
	}

	/** What the spawner in the dungeon makes. */
	public EntityType<?> dungeonMob() {
		return switch (this) {
			case PLAINS, SAVANNA -> EntityTypes.ZOMBIE;
			case TAIGA, BADLANDS, CHERRY -> EntityTypes.SKELETON;
			case SNOWY -> EntityTypes.STRAY;
			case DESERT -> EntityTypes.HUSK;
			case JUNGLE -> EntityTypes.CAVE_SPIDER;
			case SWAMP -> EntityTypes.BOGGED;
			case DARK_FOREST -> EntityTypes.SPIDER;
		};
	}

	public static CastleTheme byName(String name) {
		for (CastleTheme theme : values()) {
			if (theme.getSerializedName().equals(name)) {
				return theme;
			}
		}
		return PLAINS;
	}

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
