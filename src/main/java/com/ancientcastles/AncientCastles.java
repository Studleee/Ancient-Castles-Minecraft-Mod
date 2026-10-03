package com.ancientcastles;

import com.ancientcastles.boss.CastleBosses;
import com.ancientcastles.registry.ModBlockEntities;
import com.ancientcastles.registry.ModBlocks;
import com.ancientcastles.registry.ModCreativeTab;
import com.ancientcastles.registry.ModStructures;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AncientCastles implements ModInitializer {
	public static final String MOD_ID = "ancientcastles";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.initialize();
		ModBlockEntities.initialize();
		ModCreativeTab.initialize();
		ModStructures.initialize();
		CastleBosses.initialize();
		LOGGER.info("{} loaded", MOD_ID);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
