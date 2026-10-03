package com.ancientcastles.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.ancientcastles.block.SealedVaultBlockEntity;
import com.ancientcastles.castle.CastleTheme;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The castle lords: powered-up vanilla mobs, one kind per castle theme, with a boss health bar. Each remembers its
 * vault (in an entity tag), so killing it opens that vault.
 */
public final class CastleBosses {
	public static final String BOSS_TAG = "ancientcastles.boss";
	private static final String VAULT_TAG = "ancientcastles.vault:";
	private static final double BAR_RANGE = 48.0;
	private static final double MESSAGE_RANGE = 32.0;

	/** What each theme's lord is, what it's called (boss.ancientcastles.id), and what it carries. */
	private enum Lord {
		WARLORD(EntityTypes.VINDICATOR, BossEvent.BossBarColor.RED, 160, Items.DIAMOND_AXE, Items.IRON_HELMET, Items.IRON_CHESTPLATE),
		PILLAGER_CAPTAIN(EntityTypes.PILLAGER, BossEvent.BossBarColor.GREEN, 140, Items.CROSSBOW, null, Items.CHAINMAIL_CHESTPLATE),
		FROST_LORD(EntityTypes.STRAY, BossEvent.BossBarColor.BLUE, 140, Items.BOW, Items.DIAMOND_HELMET, Items.IRON_CHESTPLATE),
		HUSK_KING(EntityTypes.HUSK, BossEvent.BossBarColor.YELLOW, 170, Items.IRON_SWORD, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE),
		BONE_BARON(EntityTypes.SKELETON, BossEvent.BossBarColor.WHITE, 140, Items.BOW, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE),
		JUNGLE_TYRANT(EntityTypes.ZOMBIE, BossEvent.BossBarColor.GREEN, 170, Items.IRON_SWORD, Items.IRON_HELMET, Items.CHAINMAIL_CHESTPLATE),
		BOG_KING(EntityTypes.BOGGED, BossEvent.BossBarColor.GREEN, 140, Items.BOW, Items.IRON_HELMET, Items.LEATHER_CHESTPLATE),
		DARK_SORCERER(EntityTypes.EVOKER, BossEvent.BossBarColor.PURPLE, 150, null, null, null),
		ASHEN_KNIGHT(EntityTypes.WITHER_SKELETON, BossEvent.BossBarColor.WHITE, 170, Items.DIAMOND_SWORD, Items.GOLDEN_HELMET, Items.IRON_CHESTPLATE),
		BLOSSOM_WITCH(EntityTypes.WITCH, BossEvent.BossBarColor.PINK, 140, null, null, null);

		final EntityType<? extends Mob> type;
		final BossEvent.BossBarColor color;
		final double health;
		final @Nullable Item weapon;
		final @Nullable Item helmet;
		final @Nullable Item chestplate;

		Lord(EntityType<? extends Mob> type, BossEvent.BossBarColor color, double health, @Nullable Item weapon, @Nullable Item helmet, @Nullable Item chestplate) {
			this.type = type;
			this.color = color;
			this.health = health;
			this.weapon = weapon;
			this.helmet = helmet;
			this.chestplate = chestplate;
		}

		static Lord of(CastleTheme theme) {
			return switch (theme) {
				case PLAINS -> WARLORD;
				case TAIGA -> PILLAGER_CAPTAIN;
				case SNOWY -> FROST_LORD;
				case DESERT -> HUSK_KING;
				case BADLANDS -> BONE_BARON;
				case JUNGLE -> JUNGLE_TYRANT;
				case SWAMP -> BOG_KING;
				case DARK_FOREST -> DARK_SORCERER;
				case SAVANNA -> ASHEN_KNIGHT;
				case CHERRY -> BLOSSOM_WITCH;
			};
		}

		Component displayName() {
			return Component.translatable("boss.ancientcastles." + name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	private record Tracked(LivingEntity entity, ServerBossEvent bar) {
	}

	private static final Map<UUID, Tracked> BARS = new HashMap<>();

	private CastleBosses() {
	}

	public static void initialize() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living && entity.entityTags().contains(BOSS_TAG)) {
				ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), living.getDisplayName(), colorOf(living), BossEvent.BossBarOverlay.NOTCHED_10);
				Tracked old = BARS.put(living.getUUID(), new Tracked(living, bar));
				if (old != null) {
					old.bar().removeAllPlayers();
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> stopTracking(entity));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.entityTags().contains(BOSS_TAG) && entity.level() instanceof ServerLevel level) {
				BlockPos vault = vaultOf(entity);
				if (vault != null && level.isLoaded(vault) && level.getBlockEntity(vault) instanceof SealedVaultBlockEntity sealed) {
					sealed.open(level);
				}
				stopTracking(entity);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(CastleBosses::updateBars);
	}

	/**
	 * Wakes the lord for a castle of this theme in front of its vault, announces it, and returns its id (or null if
	 * it couldn't be made).
	 */
	public static @Nullable UUID spawn(ServerLevel level, BlockPos vault, Direction facing, CastleTheme theme) {
		Lord lord = Lord.of(theme);
		Mob mob = lord.type.create(level, EntitySpawnReason.TRIGGERED);
		if (mob == null) {
			return null;
		}
		BlockPos at = groundBelow(level, vault.relative(facing, 5));
		mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, facing.toYRot(), 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.TRIGGERED, null);
		mob.stopRiding();
		mob.setBaby(false);

		equip(mob, EquipmentSlot.MAINHAND, lord.weapon);
		equip(mob, EquipmentSlot.CHEST, lord.chestplate);
		if (lord == Lord.PILLAGER_CAPTAIN) {
			mob.setItemSlot(EquipmentSlot.HEAD, Raid.getOminousBannerInstance(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
			mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
		} else {
			equip(mob, EquipmentSlot.HEAD, lord.helmet);
		}

		setBase(mob, Attributes.MAX_HEALTH, lord.health);
		addBase(mob, Attributes.ARMOR, 6.0);
		addBase(mob, Attributes.ATTACK_DAMAGE, 3.0);
		setBase(mob, Attributes.KNOCKBACK_RESISTANCE, 0.6);
		setBase(mob, Attributes.FOLLOW_RANGE, 40.0);
		mob.setHealth(mob.getMaxHealth());

		mob.setCustomName(lord.displayName());
		mob.setPersistenceRequired();
		mob.addTag(BOSS_TAG);
		mob.addTag(VAULT_TAG + vault.getX() + "," + vault.getY() + "," + vault.getZ());
		level.addFreshEntity(mob);

		level.playSound(null, at, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.5F, 1.2F);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 40, 0.5, 1.0, 0.5, 0.02);
		Vec3 center = Vec3.atCenterOf(at);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(center) <= MESSAGE_RANGE * MESSAGE_RANGE) {
				player.sendOverlayMessage(Component.translatable("message.ancientcastles.awakens", lord.displayName()));
			}
		}
		return mob.getUUID();
	}

	/** Steps down from pos onto the floor, so the lord doesn't appear hovering. */
	private static BlockPos groundBelow(ServerLevel level, BlockPos pos) {
		BlockPos at = pos;
		for (int i = 0; i < 4 && level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty(); i++) {
			at = at.below();
		}
		return at;
	}

	private static void equip(Mob mob, EquipmentSlot slot, @Nullable Item item) {
		if (item != null) {
			mob.setItemSlot(slot, new ItemStack(item));
		}
		mob.setDropChance(slot, 0.0F);
	}

	private static void setBase(Mob mob, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private static void addBase(Mob mob, Holder<Attribute> attribute, double amount) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(instance.getBaseValue() + amount);
		}
	}

	private static BossEvent.BossBarColor colorOf(LivingEntity entity) {
		for (Lord lord : Lord.values()) {
			if (lord.type == entity.getType()) {
				return lord.color;
			}
		}
		return BossEvent.BossBarColor.RED;
	}

	private static @Nullable BlockPos vaultOf(Entity entity) {
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(VAULT_TAG)) {
				String[] parts = tag.substring(VAULT_TAG.length()).split(",");
				try {
					return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
				} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
					return null;
				}
			}
		}
		return null;
	}

	private static void stopTracking(Entity entity) {
		Tracked tracked = BARS.remove(entity.getUUID());
		if (tracked != null) {
			tracked.bar().removeAllPlayers();
		}
	}

	/** Shows each lord's health to players near it. */
	private static void updateBars(MinecraftServer server) {
		if (server.getTickCount() % 5 != 0) {
			return;
		}
		Iterator<Tracked> iterator = BARS.values().iterator();
		while (iterator.hasNext()) {
			Tracked tracked = iterator.next();
			LivingEntity entity = tracked.entity();
			ServerBossEvent bar = tracked.bar();
			if (entity.isRemoved() || !(entity.level() instanceof ServerLevel level)) {
				bar.removeAllPlayers();
				iterator.remove();
				continue;
			}
			bar.setProgress(Math.clamp(entity.getHealth() / entity.getMaxHealth(), 0.0F, 1.0F));
			for (ServerPlayer player : new ArrayList<>(bar.getPlayers())) {
				if (player.level() != level || player.isRemoved() || player.distanceToSqr(entity) > BAR_RANGE * BAR_RANGE) {
					bar.removePlayer(player);
				}
			}
			for (ServerPlayer player : level.players()) {
				if (player.distanceToSqr(entity) <= BAR_RANGE * BAR_RANGE) {
					bar.addPlayer(player);
				}
			}
		}
	}
}
