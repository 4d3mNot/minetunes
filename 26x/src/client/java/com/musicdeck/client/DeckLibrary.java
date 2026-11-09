package com.musicdeck.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The decks the player or the community dropped into the config folder.
 *
 * A deck is one JSON file, read and never executed, so the folder can be shared as text:
 * whoever posts a look cannot run anything on the machine that installs it. Everything the
 * folder can hand back has already been through {@link DeckSkin#parse}, which clamps every
 * number, so a file that says {@code "tile": 99999} costs its author a default rather than
 * taking the card off the screen.
 *
 * The directory arrives as an argument and nothing here touches the game, which is what lets
 * the whole of this class run inside the off-screen gate suite.
 */
public final class DeckLibrary {
	public static final String BUILT_IN = "built-in";
	private static final String SUFFIX = ".json";

	private DeckLibrary() {
	}

	/**
	 * The handle a player types for this deck: its name, lower-cased and hyphenated.
	 *
	 * Names come out of files people wrote, so they can contain spaces, and a command argument
	 * cannot. This is the same spelling {@link #resolve} matches against, which is what lets
	 * the picker offer a string the command is guaranteed to accept.
	 */
	public static String handle(DeckSkin deck) {
		return normalize(deck.name());
	}

	/** Every deck in the folder, the built-in card first, the rest alphabetical by name. */
	public static List<DeckSkin> load(Path directory) {
		List<DeckSkin> decks = new ArrayList<>();
		decks.add(DeckSkin.BUILT_IN);
		for (Path file : files(directory)) {
			decks.add(read(file));
		}

		decks.sort(byName());
		return decks;
	}

	/**
	 * The deck a name asks for, or the built-in card.
	 *
	 * Matched case-insensitively against the file name and, since that is what a shared
	 * screenshot quotes, against the display name inside it. Returning the built-in card for
	 * a name that is not there is deliberate: a typo in a config file should draw a plain
	 * panel, not no panel.
	 */
	public static DeckSkin resolve(Path directory, String name) {
		DeckSkin deck = find(directory, name);
		return deck == null ? DeckSkin.BUILT_IN : deck;
	}

	/**
	 * The deck a name asks for, or null when the folder has nothing by that name.
	 *
	 * Separate from {@link #resolve} because the two callers need different answers: the card
	 * must draw something either way, while the command has to say out loud that the name the
	 * player typed is not in the folder.
	 */
	public static DeckSkin find(Path directory, String name) {
		String wanted = normalize(name);
		if (wanted.isEmpty() || BUILT_IN.equals(wanted)) {
			return DeckSkin.BUILT_IN;
		}

		for (Path file : files(directory)) {
			DeckSkin skin = read(file);
			if (wanted.equals(key(file)) || wanted.equals(normalize(skin.name()))) {
				return skin;
			}
		}

		return null;
	}

	/**
	 * Write the example decks, but only where one is missing.
	 *
	 * A player who edits {@code minimal.json} to taste and then updates the mod keeps their
	 * edit: this never overwrites, and a broken deck they saved themselves is theirs to fix
	 * rather than something the launcher quietly replaces. Returns how many files were added.
	 */
	public static int seedMissing(Path directory) {
		int written = 0;
		for (Example example : EXAMPLES) {
			Path target = directory.resolve(example.file());
			if (Files.exists(target)) {
				continue;
			}

			try {
				Files.createDirectories(directory);
				Files.writeString(target, example.body());
				written++;
			} catch (IOException e) {
				throw new UncheckedIOException("Could not write " + target, e);
			}
		}

		return written;
	}

	/** Parse one file. A deck that cannot be read at all is the shipped card, not a crash. */
	private static DeckSkin read(Path file) {
		try {
			return DeckSkin.parse(key(file), Files.readString(file));
		} catch (IOException e) {
			return DeckSkin.BUILT_IN;
		}
	}

	private static List<Path> files(Path directory) {
		if (directory == null || !Files.isDirectory(directory)) {
			return List.of();
		}

		try (var walk = Files.list(directory)) {
			return walk.filter(DeckLibrary::isDeck).sorted().toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	private static boolean isDeck(Path file) {
		return Files.isRegularFile(file) && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(SUFFIX);
	}

	/** {@code minimal.json} to {@code minimal}: the handle a player types. */
	private static String key(Path file) {
		String name = file.getFileName().toString();
		return normalize(name.substring(0, name.length() - SUFFIX.length()));
	}

	private static String normalize(String raw) {
		return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
	}

	private static Comparator<DeckSkin> byName() {
		return Comparator.comparingInt(DeckLibrary::rank).thenComparing(deck -> normalize(deck.name()));
	}

	/** The built-in card stays at the top whatever its name sorts to. */
	private static int rank(DeckSkin deck) {
		return DeckSkin.BUILT_IN.equals(deck) ? 0 : 1;
	}

	/** One shipped example, named for the file it becomes. */
	private record Example(String file, String body) {
	}

	/**
	 * The three decks written into an empty folder.
	 *
	 * They are the format's documentation and its test at once: between them they prove a
	 * text-only card, a big-sleeve card and a flat card with a pinned colour, which is the
	 * span the knobs actually cover. The {@code readme} key in each one is unknown to the
	 * parser and ignored on purpose.
	 */
	private static final List<Example> EXAMPLES = List.of(
		new Example("minimal.json", """
			{
			  "readme": "Text only. tile 0 drops the artwork and the card becomes two lines.",
			  "layout": "row",
			  "tile": 0,
			  "pad": 3,
			  "minWidth": 90,
			  "maxWidth": 150,
			  "opacity": 0.78
			}
			"""),
		new Example("big-sleeve.json", """
			{
			  "readme": "The artwork on top, the words under it, and a tile that fills the card.",
			  "layout": "stack",
			  "tile": 64,
			  "pad": 5,
			  "gap": 4,
			  "minWidth": 64,
			  "maxWidth": 220
			}
			"""),
		new Example("flat-green.json", """
			{
			  "readme": "No outline, no clock, no progress bar, and a colour the deck owns.",
			  "layout": "row",
			  "accent": "#1DB954",
			  "panel": "#0B1512",
			  "opacity": 0.92,
			  "tile": 36,
			  "border": false,
			  "time": false,
			  "progress": false
			}
			"""));
}
