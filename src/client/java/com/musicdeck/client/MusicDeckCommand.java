package com.musicdeck.client;

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
