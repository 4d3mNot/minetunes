package com.musicdeck;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicDeck implements ModInitializer {
	public static final String MOD_ID = "musicdeck";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Music Deck loading");
	}
}
