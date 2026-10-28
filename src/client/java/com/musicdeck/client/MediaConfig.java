package com.musicdeck.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

import com.musicdeck.MusicDeck;

/**
 * Plain properties file next to the game's options.txt.
 *
 * The HUD offset is in GUI pixels, the same space the overlay draws in, so it can be
 * nudged out from under the crosshair or a scoreboard without rebuilding anything.
 */
public final class MediaConfig {
	public static final String SMOOTH = "smooth";
	public static final String PIXEL = "pixel";
	public static final String THEME_AUTO = Palette.THEME_AUTO;
	public static final String THEME_GREEN = Palette.THEME_GREEN;
	public static final String THEME_WHITE = Palette.THEME_WHITE;
	public String hudX = "6";
	public String hudY = "6";
	public boolean showHud = true;
	public boolean bridgeEnabled = true;
	/** Hide the panel the moment playback stops, so a paused track is not furniture. */
	public boolean onlyWhenPlaying = false;
	/** How the album tile is sampled: a clean 1:1 tile, or deliberate blocks. */
	public String artStyle = SMOOTH;
	/** Where the card's colour comes from: the cover, the brand green, or plain white. */
	public String theme = THEME_AUTO;
	/** The transport buttons only answer while a screen owns the mouse. */
	public boolean showControls = true;
	/** Which deck from {@link #decks()} the card is drawn from; {@code built-in} is the shipped look. */
	public String deck = DeckLibrary.BUILT_IN;
	/**
	 * The parsed deck behind {@link #deck}, resolved once here rather than on every frame.
	 *
	 * The card asks for this sixty times a second, and reading and re-parsing a folder that
	 * often is how a cosmetic feature ends up in the frame budget. It is refreshed when the
	 * player picks a deck, which is the only moment the answer can change.
	 */
	private DeckSkin deckSkin = DeckSkin.BUILT_IN;

	// Only a floor: the window decides the far edge and HudLayout clamps to it every frame,
	// so a place past the right border is legal in the file and draws against the border.
	public int x() {
		return read(hudX, 6, 0, Integer.MAX_VALUE);
	}

	public int y() {
		return read(hudY, 6, 0, Integer.MAX_VALUE);
	}

	private static int read(String raw, int fallback, int low, int high) {
		try {
			return Math.max(low, Math.min(high, Integer.parseInt(raw.trim())));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public static Path file() {
		return FabricLoader.getInstance().getGameDir().resolve("musicdeck.properties");
	}

	/**
	 * The folder community decks live in: {@code .minecraft/config/musicdeck/decks}.
	 *
	 * Under {@code config} rather than next to {@code options.txt}, because this is the one
	 * part of the mod a player is meant to edit by hand and share, and that is what the
	 * folder is for.
	 */
	public static Path decks() {
		return FabricLoader.getInstance().getConfigDir().resolve("musicdeck").resolve("decks");
	}

	/** The deck the card draws from. Never null: an unreadable name is the built-in card. */
	public DeckSkin deckSkin() {
		return deckSkin;
	}

	/**
	 * Switch to a deck and keep it. True when the folder actually had that name.
	 *
	 * The name is stored even when it does not resolve, so a deck someone deletes and puts
	 * back a minute later comes back on its own; the card only draws the built-in look in the
	 * meantime.
	 */
	public boolean chooseDeck(String name) {
		deck = name == null || name.trim().isEmpty() ? DeckLibrary.BUILT_IN : name.trim();
		DeckSkin found = DeckLibrary.find(decks(), deck);
		deckSkin = found == null ? DeckSkin.BUILT_IN : found;
		save();
		return found != null;
	}

	/** Re-read the folder: a deck dropped in while the game runs is live after this. */
	public void refreshDeck() {
		deckSkin = DeckLibrary.resolve(decks(), deck);
	}

	/**
	 * The name this file had before the mod was renamed.
	 *
	 * Read-only, and only when the new file is missing: a panel someone spent time placing
	 * must not jump back to the corner because of a cosmetic change. The old file is left
	 * in place rather than moved, so a player who goes back to the old jar keeps theirs.
	 */
	private static Path legacyFile() {
		return FabricLoader.getInstance().getGameDir().resolve("spotify.properties");
	}

	public static MediaConfig load() {
		MediaConfig config = new MediaConfig();
		Properties props = new Properties();
		Path from = Files.exists(file()) ? file() : legacyFile();

		// Seed the example decks before the stored name is resolved against the folder below.
		DeckLibrary.seedMissing(decks());

		try (InputStream in = Files.newInputStream(from)) {
			props.load(in);
		} catch (IOException e) {
			// No file yet is normal on a first run; write one so it can be edited.
			config.save();
			return config;
		}

		config.hudX = props.getProperty("hudX", config.hudX);
		config.hudY = props.getProperty("hudY", config.hudY);
		config.showHud = Boolean.parseBoolean(props.getProperty("showHud", "true"));
		config.bridgeEnabled = Boolean.parseBoolean(props.getProperty("bridgeEnabled", "true"));
		config.onlyWhenPlaying = Boolean.parseBoolean(props.getProperty("onlyWhenPlaying", "false"));
		config.artStyle = pixel(props.getProperty("artStyle", SMOOTH)) ? PIXEL : SMOOTH;
		config.theme = theme(props.getProperty("theme", THEME_AUTO));
		config.showControls = Boolean.parseBoolean(props.getProperty("showControls", "true"));
		config.deck = props.getProperty("deck", config.deck).trim();
		config.deckSkin = DeckLibrary.resolve(decks(), config.deck);
		return config;
	}

	private static boolean pixel(String raw) {
		return raw.trim().equalsIgnoreCase(PIXEL);
	}

	/** Only ever one of the three known names, so a typo in the file colours the card rather than breaking it. */
	private static String theme(String raw) {
		String value = raw.trim().toLowerCase();
		// "spotify" was this theme's name before the mod was renamed, and old files still say it.
		if (THEME_GREEN.equals(value) || "spotify".equals(value)) {
			return THEME_GREEN;
		}

		if (THEME_WHITE.equals(value)) {
			return THEME_WHITE;
		}

		return THEME_AUTO;
	}

	/** Never an unrecognised value: a typo in the file falls back rather than blanking the tile. */
	public String artStyle() {
		return PIXEL.equals(artStyle) ? PIXEL : SMOOTH;
	}

	public String theme() {
		return theme(theme);
	}

	public void save() {
		Properties props = new Properties();
		props.setProperty("showHud", Boolean.toString(showHud));
		props.setProperty("bridgeEnabled", Boolean.toString(bridgeEnabled));
		props.setProperty("onlyWhenPlaying", Boolean.toString(onlyWhenPlaying));
		props.setProperty("showControls", Boolean.toString(showControls));
		props.setProperty("hudX", hudX);
		props.setProperty("hudY", hudY);
		props.setProperty("artStyle", artStyle());
		props.setProperty("theme", theme());
		props.setProperty("deck", deck);

		try (OutputStream out = Files.newOutputStream(file())) {
			props.store(out, "Music Deck - hudX/hudY are GUI pixels from the top left, "
				+ "artStyle is smooth or pixel, theme is auto, green or white, "
				+ "deck is a file name in config/musicdeck/decks");
		} catch (IOException e) {
			MusicDeck.LOGGER.warn("Could not write musicdeck.properties", e);
		}
	}
}
