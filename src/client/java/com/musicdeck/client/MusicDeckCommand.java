package com.musicdeck.client;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client-side commands, so they work on any server and need no operator rights.
 *
 * Every verb is a one-way request to the media session; the bridge answers whether the
 * player accepted it, which is why {@link #send} waits a moment for the ack instead of
 * claiming success the instant the command file lands.
 */
public final class MusicDeckCommand {
	private MusicDeckCommand() {
	}

	public static void register(MusicDeckClient client) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(tree(client, "minetunes"));
			dispatcher.register(tree(client, "music"));
		});
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> tree(MusicDeckClient client, String root) {
		return ClientCommandManager.literal(root)
			.then(ClientCommandManager.literal("status").executes(context -> status(client, context)))
			.then(ClientCommandManager.literal("toggle").executes(context -> verb(client, context, "toggle")))
			.then(ClientCommandManager.literal("play").executes(context -> verb(client, context, "play")))
			.then(ClientCommandManager.literal("pause").executes(context -> verb(client, context, "pause")))
			.then(ClientCommandManager.literal("next").executes(context -> verb(client, context, "next")))
			.then(ClientCommandManager.literal("prev").executes(context -> verb(client, context, "prev")))
			.then(ClientCommandManager.literal("shuffle").executes(context -> verb(client, context, "shuffle"))
				.then(ClientCommandManager.argument("state", StringArgumentType.word())
					.suggests((context, builder) -> builder.suggest("on").suggest("off").buildFuture())
					.executes(context -> verb(client, context, "shuffle:" + StringArgumentType.getString(context, "state")))))
			.then(ClientCommandManager.literal("repeat").executes(context -> verb(client, context, "repeat"))
				.then(ClientCommandManager.argument("mode", StringArgumentType.word())
					.suggests((context, builder) -> builder.suggest("none").suggest("track").suggest("playlist").buildFuture())
					.executes(context -> verb(client, context, "repeat:" + mode(context)))))
			.then(ClientCommandManager.literal("seek")
				.then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(0, 20000))
					.executes(context -> verb(client, context, "seek:" + IntegerArgumentType.getInteger(context, "seconds")))))
			.then(ClientCommandManager.literal("hud").executes(context -> hud(client, context))
				.then(ClientCommandManager.argument("position", IntegerArgumentType.integer(0))
					.then(ClientCommandManager.argument("y", IntegerArgumentType.integer(0))
						.executes(context -> place(client, context)))))
			.then(ClientCommandManager.literal("move").executes(context -> move(client, context)))
			.then(ClientCommandManager.literal("style").executes(context -> style(client, context, null))
				.then(ClientCommandManager.argument("art", StringArgumentType.word())
					.suggests((context, builder) -> builder.suggest(MediaConfig.SMOOTH).suggest(MediaConfig.PIXEL).buildFuture())
					.executes(context -> style(client, context, StringArgumentType.getString(context, "art")))))
			.then(ClientCommandManager.literal("restart").executes(context -> restart(client, context)))
			.then(ClientCommandManager.literal("settings").executes(context -> settings(client, context)))
			.then(ClientCommandManager.literal("lyrics").executes(context -> lyrics(client, context))
				.then(ClientCommandManager.literal("on").executes(context -> online(client, context, true)))
				.then(ClientCommandManager.literal("off").executes(context -> online(client, context, false)))
				.then(ClientCommandManager.literal("panel").executes(context -> lyricPanel(client, context, !client.config().lyricsPanel))
					.then(ClientCommandManager.argument("state", StringArgumentType.word())
						.suggests((context, builder) -> builder.suggest("on").suggest("off").buildFuture())
						.executes(context -> lyricPanel(client, context,
							"on".equalsIgnoreCase(StringArgumentType.getString(context, "state"))))))
				.then(ClientCommandManager.literal("lines")
					.then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1, LyricsLayout.MAX_LINES))
						.executes(context -> lyricLines(client, context))))
				.then(ClientCommandManager.literal("opacity")
					.then(ClientCommandManager.argument("percent", StringArgumentType.word())
						.suggests((context, builder) -> builder.suggest(MediaConfig.AUTO)
							.suggest("85").suggest("100").buildFuture())
						.executes(context -> lyricOpacity(client, context))))
				.then(ClientCommandManager.literal("size")
					.then(ClientCommandManager.argument("width", StringArgumentType.word())
						.then(ClientCommandManager.argument("height", StringArgumentType.word())
							.executes(context -> lyricSize(client, context)))))
				.then(ClientCommandManager.literal("place")
					.then(ClientCommandManager.argument("x", StringArgumentType.word())
						.then(ClientCommandManager.argument("y", StringArgumentType.word())
							.executes(context -> lyricPlace(client, context)))))
				.then(ClientCommandManager.literal("reset").executes(context -> lyricReset(client, context))))
			.then(ClientCommandManager.literal("theme").executes(context -> theme(client, context, null))
				.then(ClientCommandManager.argument("colour", StringArgumentType.word())
					.suggests((context, builder) -> builder.suggest(Palette.THEME_AUTO)
						.suggest(Palette.THEME_GREEN).suggest(Palette.THEME_WHITE).buildFuture())
					.executes(context -> theme(client, context, StringArgumentType.getString(context, "colour")))))
			.then(ClientCommandManager.literal("deck").executes(context -> deck(client, context, null))
				.then(ClientCommandManager.argument("name", StringArgumentType.word())
					.suggests((context, builder) -> suggestDecks(client, builder))
					.executes(context -> deck(client, context, StringArgumentType.getString(context, "name")))));
	}

	private static CompletableFuture<Suggestions> suggestDecks(MusicDeckClient client, SuggestionsBuilder builder) {
		for (DeckSkin deck : DeckLibrary.load(MediaConfig.decks())) {
			builder.suggest(DeckLibrary.handle(deck));
		}

		return builder.buildFuture();
	}

	private static String mode(CommandContext<FabricClientCommandSource> context) {
		// The enum member is capitalised on the Windows side; accept either spelling.
		String raw = StringArgumentType.getString(context, "mode");
		return raw.substring(0, 1).toUpperCase() + raw.substring(1).toLowerCase();
	}

	private static int status(MusicDeckClient client, CommandContext<FabricClientCommandSource> context) {
		MediaBridge bridge = client.bridge();
		FabricClientCommandSource source = context.getSource();
		if (!client.enabled()) {
			source.sendFeedback(Component.literal("MineTunes is disabled in musicdeck.properties (bridgeEnabled=false)."));
			return 0;
		}

		if (bridge.isUnavailable()) {
			source.sendFeedback(Component.literal("Bridge is down: " + bridge.lastAck()));
			return 0;
		}

		source.sendFeedback(Component.literal("helper " + (bridge.isRunning() ? "running" : "starting")
			+ ", state " + (bridge.stateAgeMillis() < MediaBridge.STALE_MILLIS ? "fresh" : "stale")));

		NowPlaying playing = bridge.state();
		if (!playing.session()) {
			source.sendFeedback(Component.literal("No Windows media session. Start Spotify and play something."));
			return 0;
		}

		if (!playing.spotify()) {
			source.sendFeedback(Component.literal("Note: " + playing.source()
				+ " owns the media session right now, not Spotify."));
		}

		source.sendFeedback(Component.literal(playing.label(60)));
		source.sendFeedback(Component.literal(playing.status() + "  " + playing.progressText()
			+ "  album: " + (playing.album().isEmpty() ? "-" : playing.album())));
		source.sendFeedback(Component.literal("Shuffle and repeat: this player does not report them, "
			+ "so the commands work but the state cannot be read back."));
		return 1;
	}

	private static int verb(MusicDeckClient client, CommandContext<FabricClientCommandSource> context, String command) {
		MediaBridge bridge = client.bridge();
		if (!bridge.isRunning()) {
			bridge.tick();
			context.getSource().sendError(Component.literal("The media helper is not up yet; try again in a second."));
			return 0;
		}

		if (!bridge.send(command)) {
			context.getSource().sendError(Component.literal("Could not queue " + command + "; see .minecraft/musicdeck/bridge.log"));
			return 0;
		}

		client.announceLater(command);
		return 1;
	}

	private static int hud(MusicDeckClient client, CommandContext<FabricClientCommandSource> context) {
		MediaConfig config = client.config();
		config.showHud = !config.showHud;
		config.save();
		context.getSource().sendFeedback(Component.literal("Now-playing panel " + (config.showHud ? "on" : "off") + "."));
		return 1;
	}

	private static int place(MusicDeckClient client, CommandContext<FabricClientCommandSource> context) {
		MediaConfig config = client.config();
		config.hudX = Integer.toString(IntegerArgumentType.getInteger(context, "position"));
		config.hudY = Integer.toString(IntegerArgumentType.getInteger(context, "y"));
		config.showHud = true;
		config.save();
		context.getSource().sendFeedback(Component.literal("Panel moved to " + config.hudX + ", " + config.hudY + "."));
		return 1;
	}

	private static int move(MusicDeckClient client, CommandContext<FabricClientCommandSource> context) {
		client.move(Minecraft.getInstance());
		return 1;
	}

	/**
	 * Swap the album tile between the full-size decode and deliberate blocks.
	 *
	 * With no argument it flips, because the question this answers is always "the other one".
	 */
	private static int style(MusicDeckClient client, CommandContext<FabricClientCommandSource> context, String raw) {
		MediaConfig config = client.config();
		config.artStyle = raw == null
			? (MediaConfig.PIXEL.equals(config.artStyle()) ? MediaConfig.SMOOTH : MediaConfig.PIXEL)
			: raw.trim().toLowerCase();
		config.save();
		context.getSource().sendFeedback(Component.literal("Album art is now " + config.artStyle() + "."));
		return 1;
	}

	private static int restart(MusicDeckClient client, CommandContext<FabricClientCommandSource> context) {
		client.bridge().restart();
		context.getSource().sendFeedback(Component.literal("Relaunching the media helper."));
		return 1;
	}

	private static int settings(MusicDeckClient client, CommandContext<FabricClientCommandSource> context) {
		client.settings(Minecraft.getInstance());
		return 1;
	}

	/**
	 * Say whether the track that is playing has words, and where they come from.
	 *
	 * Spotify draws its own lyrics inside its window and hands no part of them to another
	 * process, so the words come from a file in the folder, or from lrclib.net when there is
	 * no file and {@link MediaConfig#onlineLyrics} is on. This verb exists because the
	 * folder starts with one example in it and a panel that silently never appears looks
	 * like a broken feature.
	 */
	private static int lyrics(MusicDeckClient client, CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		Path folder = MediaConfig.lyrics();
		boolean online = client.config().onlineLyrics;
		NowPlaying playing = client.bridge().state();
		if (!playing.hasTrack()) {
			source.sendFeedback(Component.literal("Lyrics are .lrc files in " + folder + "."));
			source.sendFeedback(Component.literal("Name one \"Artist - Title.lrc\" and the card shows it while that track plays."));
			source.sendFeedback(Component.literal("Online lookup is " + (online ? "on" : "off") + " (/music lyrics on|off)."));
			return 0;
		}

		Path file = LyricsLibrary.find(folder, playing.artist(), playing.title());
		if (file == null) {
			source.sendFeedback(Component.literal("No lyrics for " + playing.label(60) + "."));
			source.sendFeedback(Component.literal(online
				? "Asking lrclib.net; a hit is saved as \"" + LyricsFetcher.fileName(playing.artist(), playing.title())
					+ "\" in " + folder + "."
				: "Online lookup is off (/music lyrics on), so save them as \""
					+ LyricsFetcher.fileName(playing.artist(), playing.title()) + "\" in " + folder + "."));
			return 0;
		}

		Lyrics words = LyricsLibrary.read(file);
		if (!words.present()) {
			source.sendFeedback(Component.literal(file.getFileName()
				+ " has no timed lines; every line needs a [mm:ss.xx] stamp."));
			return 0;
		}

		int line = words.indexAt(playing.position());
		source.sendFeedback(Component.literal(words.lines().size() + " lines from " + file.getFileName()
			+ "; " + (line < 0 ? "before the first line" : "on line " + (line + 1)) + "."));
		return 1;
	}

	/** Turn the lrclib.net lookup on or off and keep it. A local file is used either way. */
	private static int online(MusicDeckClient client, CommandContext<FabricClientCommandSource> context, boolean on) {
		MediaConfig config = client.config();
		config.onlineLyrics = on;
		config.save();
		context.getSource().sendFeedback(Component.literal(on
			? "Online lyrics on: a track with no file is looked up on lrclib.net, and the words are saved into the lyrics folder."
			: "Online lyrics off: only .lrc files in the lyrics folder are used, and no request is made."));
		return 1;
	}

	/**
	 * Give the words their own panel band, or take it away.
	 *
	 * With no argument it flips, because the question is always "the other one" - and the
	 * panel is the one lyric knob that survives the drag screen, so it earns a verb.
	 */
	private static int lyricPanel(MusicDeckClient client, CommandContext<FabricClientCommandSource> context, boolean on) {
		MediaConfig config = client.config();
		config.lyricsPanel = on;
		config.save();
		context.getSource().sendFeedback(Component.literal("Lyrics panel background " + (on ? "on" : "off") + "."));
		return 1;
	}

	/** How many rows an automatic height shows. A height set by hand would overrule this, so it is released. */
	private static int lyricLines(MusicDeckClient client, CommandContext<FabricClientCommandSource> context) {
		MediaConfig config = client.config();
		config.lyricsLines = IntegerArgumentType.getInteger(context, "count");
		config.lyricsHeight = MediaConfig.AUTO;
		config.save();
		context.getSource().sendFeedback(Component.literal("Lyrics show " + config.lyricsLines()
			+ " lines (height back to auto)."));
		return 1;
	}

	/**
	 * How solid the words' panel is, as a percentage, or {@code auto} for the deck's own alpha.
	 *
	 * A number in the file is clamped to the range the layout can paint, but the command says
	 * so out loud instead of accepting 400% and painting 100.
	 */
	private static int lyricOpacity(MusicDeckClient client, CommandContext<FabricClientCommandSource> context) {
		MediaConfig config = client.config();
		String raw = StringArgumentType.getString(context, "percent").trim();
		if (MediaConfig.AUTO.equalsIgnoreCase(raw)) {
			config.lyricsOpacity = MediaConfig.AUTO;
			config.save();
			context.getSource().sendFeedback(Component.literal(
				"Lyrics panel opacity follows the deck (auto)."));
			return 1;
		}

		Integer percent = lyricPercent(raw);
		if (percent == null) {
			context.getSource().sendError(Component.literal(
				"Opacity takes auto or a percentage from 15 to 100."));
			return 0;
		}

		config.lyricsOpacity = Integer.toString(percent);
		config.save();
		context.getSource().sendFeedback(Component.literal("Lyrics panel opacity " + percent + "%."));
		return 1;
	}

	/** Pin the words' box, or put either axis back to following the card. */
	private static int lyricSize(MusicDeckClient client, CommandContext<FabricClientCommandSource> context) {
		MediaConfig config = client.config();
		String width = StringArgumentType.getString(context, "width");
		String height = StringArgumentType.getString(context, "height");
		Integer pixelsW = lyricPixels(width);
		Integer pixelsH = lyricPixels(height);
		if ((pixelsW == null && !isAuto(width)) || (pixelsH == null && !isAuto(height))) {
			context.getSource().sendError(Component.literal("Size takes auto or a pixel count, e.g. /music lyrics size 220 60."));
			return 0;
		}

		config.lyricsWidth = pixelsW == null ? MediaConfig.AUTO : Integer.toString(pixelsW);
		config.lyricsHeight = pixelsH == null ? MediaConfig.AUTO : Integer.toString(pixelsH);
		config.save();
		context.getSource().sendFeedback(Component.literal("Lyrics box "
			+ (pixelsW == null ? "auto" : pixelsW) + " x " + (pixelsH == null ? "auto" : pixelsH) + "."));
		return 1;
	}

	/** Park the words at a point on the GUI, or put either axis back to following the card. */
	private static int lyricPlace(MusicDeckClient client, CommandContext<FabricClientCommandSource> context) {
		MediaConfig config = client.config();
		String x = StringArgumentType.getString(context, "x");
		String y = StringArgumentType.getString(context, "y");
		Integer pixelsX = lyricPixels(x);
		Integer pixelsY = lyricPixels(y);
		if ((pixelsX == null && !isAuto(x)) || (pixelsY == null && !isAuto(y))) {
			context.getSource().sendError(Component.literal("Place takes auto or a pixel position, e.g. /music lyrics place 40 90."));
			return 0;
		}

		config.lyricsX = pixelsX == null ? MediaConfig.AUTO : Integer.toString(pixelsX);
		config.lyricsY = pixelsY == null ? MediaConfig.AUTO : Integer.toString(pixelsY);
		config.save();
		context.getSource().sendFeedback(Component.literal("Lyrics at "
			+ (pixelsX == null ? "auto" : pixelsX) + ", " + (pixelsY == null ? "auto" : pixelsY) + "."));
		return 1;
	}

	/** Every lyric setting back to the shipped look: following the card, three lines, deck colours. */
	private static int lyricReset(MusicDeckClient client, CommandContext<FabricClientCommandSource> context) {
		MediaConfig config = client.config();
		config.lyricsX = MediaConfig.AUTO;
		config.lyricsY = MediaConfig.AUTO;
		config.lyricsWidth = MediaConfig.AUTO;
		config.lyricsHeight = MediaConfig.AUTO;
		config.lyricsLines = LyricsLayout.LINES;
		config.lyricsPanel = true;
		config.lyricsOpacity = MediaConfig.AUTO;
		config.save();
		context.getSource().sendFeedback(Component.literal(
			"Lyrics back to auto: under the card, three lines, the deck's own panel."));
		return 1;
	}

	private static boolean isAuto(String raw) {
		return MediaConfig.AUTO.equalsIgnoreCase(raw.trim());
	}

	/** A pixel count, or null for anything that is not a non-negative whole number. */
	private static Integer lyricPixels(String raw) {
		try {
			return Math.max(0, Integer.parseInt(raw.trim()));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Integer lyricPercent(String raw) {
		String digits = raw.endsWith("%") ? raw.substring(0, raw.length() - 1).trim() : raw;
		Integer percent = lyricPixels(digits);
		return percent == null ? null : Math.max(15, Math.min(100, percent));
	}

	/**
	 * Pick where the card gets its colour, or cycle through the three answers.
	 *
	 * With no argument it advances rather than flipping: three values cannot be a toggle,
	 * and repeating the command is the natural way to audition a theme.
	 */
	private static int theme(MusicDeckClient client, CommandContext<FabricClientCommandSource> context, String raw) {
		MediaConfig config = client.config();
		if (raw == null) {
			String current = config.theme();
			config.theme = Palette.THEME_AUTO.equals(current) ? Palette.THEME_GREEN
				: Palette.THEME_GREEN.equals(current) ? Palette.THEME_WHITE : Palette.THEME_AUTO;
		} else {
			config.theme = raw.trim().toLowerCase();
		}

		config.save();
		context.getSource().sendFeedback(Component.literal("Panel colour is now " + config.theme() + "."));
		return 1;
	}

	/**
	 * Pick the deck the card is drawn from, or step through the ones the folder holds.
	 *
	 * Cycling is what makes this usable without reading the folder first: the card redraws the
	 * instant the name changes, so repeating the command auditions every look the player has.
	 */
	private static int deck(MusicDeckClient client, CommandContext<FabricClientCommandSource> context, String raw) {
		MediaConfig config = client.config();
		List<DeckSkin> decks = DeckLibrary.load(MediaConfig.decks());
		String name = raw == null ? nextDeck(decks, config.deckSkin()) : raw.trim();
		boolean known = config.chooseDeck(name);
		DeckSkin skin = config.deckSkin();
		context.getSource().sendFeedback(Component.literal("Deck: " + skin.name()
			+ (known ? "" : " (nothing in config/musicdeck/decks is named " + name + ")") + "."));
		context.getSource().sendFeedback(Component.literal("Community decks are JSON files in "
			+ "config/musicdeck/decks - the folder holds three examples to copy."));
		return 1;
	}

	private static String nextDeck(List<DeckSkin> decks, DeckSkin current) {
		for (int index = 0; index < decks.size(); index++) {
			if (DeckLibrary.handle(decks.get(index)).equals(DeckLibrary.handle(current))) {
				return DeckLibrary.handle(decks.get((index + 1) % decks.size()));
			}
		}

		return DeckLibrary.handle(decks.get(0));
	}
}
