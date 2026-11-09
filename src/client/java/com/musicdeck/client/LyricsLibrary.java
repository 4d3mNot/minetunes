package com.musicdeck.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The lyrics the player dropped into the config folder, matched to what is playing.
 *
 * One {@code .lrc} file per song, named after the track the card already shows. Names are
 * compared with their punctuation and case thrown away, so the spelling of a file is not
 * something the player has to get right - what has to be right is that the words in it are
 * the ones for this song.
 *
 * The directory arrives as an argument and nothing here touches the game, which is what
 * lets the matcher and the reader run inside the off-screen gate suite.
 */
public final class LyricsLibrary {
	public static final String SUFFIX = ".lrc";
	private static final Charset LEGACY = Charset.forName("windows-1252");

	private LyricsLibrary() {
	}

	/**
	 * The file for this track, or null when the folder has none.
	 *
	 * Tried most specific first: artist and title together, then the title alone, then the
	 * same two with any bracketed suffix removed - Spotify's title often carries a note like
	 * {@code (From The Vault)} that nobody puts in a file name. A title-only match is
	 * deliberately last, because it is the one that can pick up the wrong song.
	 */
	public static Path find(Path directory, String artist, String title) {
		if (directory == null || !Files.isDirectory(directory)) {
			return null;
		}

		List<String> wanted = candidates(artist, title);
		if (wanted.isEmpty()) {
			return null;
		}

		try (var walk = Files.list(directory)) {
			List<Path> files = walk.filter(LyricsLibrary::isLyrics).sorted().toList();
			for (String name : wanted) {
				for (Path file : files) {
					if (name.equals(key(stem(file)))) {
						return file;
					}
				}
			}
		} catch (IOException e) {
			return null;
		}

		return null;
	}

	/** Read one file. A file that cannot be read is no lyrics, never a crash. */
	public static Lyrics read(Path file) {
		try {
			return Lyrics.parse(decode(Files.readAllBytes(file)));
		} catch (IOException e) {
			return Lyrics.EMPTY;
		}
	}

	/**
	 * UTF-8 where the bytes are valid, Windows-1252 where they are not.
	 *
	 * A lyrics file is something a person saved by hand, often from a program that did not
	 * ask which encoding to use. UTF-8 is the modern answer and stays the default; the
	 * fallback exists so an older Latin-1 file reads as accented letters instead of being
	 * thrown away as unreadable.
	 */
	static String decode(byte[] bytes) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException e) {
			return new String(bytes, LEGACY);
		}
	}

	/**
	 * Write the example file, but only where it is missing.
	 *
	 * It never matches a real track - it is named after no song and carries no song's
	 * title - so its only job is to show the format to a player who has just discovered the
	 * folder. Like the deck examples, it is never overwritten.
	 */
	public static int seedMissing(Path directory) {
		Path target = directory.resolve("example.lrc");
		if (Files.exists(target)) {
			return 0;
		}

		try {
			Files.createDirectories(directory);
			Files.writeString(target, EXAMPLE);
			return 1;
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + target, e);
		}
	}

	private static boolean isLyrics(Path file) {
		return Files.isRegularFile(file)
			&& baseName(file).toLowerCase(Locale.ROOT).endsWith(SUFFIX);
	}

	private static String baseName(Path file) {
		return file.getFileName().toString();
	}

	/** The file name without its suffix, which is the part a track is compared against. */
	private static String stem(Path file) {
		String name = baseName(file);
		return name.substring(0, name.length() - SUFFIX.length());
	}

	/**
	 * How a file name and a track are compared: case, spacing and punctuation all ignored.
	 *
	 * Only letters and digits survive, so {@code Brent Faiyaz - Trust}, {@code brent_faiyaz
	 * - trust} and {@code Brent Faiyaz - Trust (Explicit)} are the same name once the note
	 * in brackets is gone. Letters are kept in whatever script they are written in, which
	 * matters for the accented names a lot of songs have.
	 */
	public static String key(String raw) {
		StringBuilder kept = new StringBuilder();
		String text = raw == null ? "" : raw;
		boolean space = true;
		for (int index = 0; index < text.length(); index++) {
			char ch = text.charAt(index);
			if (Character.isLetterOrDigit(ch)) {
				kept.append(Character.toLowerCase(ch));
				space = false;
			} else if (!space) {
				kept.append(' ');
				space = true;
			}
		}

		return kept.toString().trim();
	}

	/** The names to try, deduplicated and in order. */
	public static List<String> candidates(String artist, String title) {
		List<String> names = new ArrayList<>(4);
		String by = artist == null ? "" : artist;
		String of = title == null ? "" : title;
		add(names, key(by + " - " + of));
		add(names, key(of));

		String plain = stripBrackets(of);
		if (!plain.equals(of)) {
			add(names, key(by + " - " + plain));
			add(names, key(plain));
		}

		return names;
	}

	private static void add(List<String> names, String name) {
		if (!name.isEmpty() && !names.contains(name)) {
			names.add(name);
		}
	}

	/** Drops bracketed asides, which are the part of a title a file name usually omits. */
	static String stripBrackets(String title) {
		String text = title == null ? "" : title;
		text = text.replaceAll("\\([^)]*\\)", " ").replaceAll("\\[[^\\]]*\\]", " ");
		return text.replaceAll("\\s+", " ").trim();
	}

	private static final String EXAMPLE = """
		[ti:Example]
		[ar:Example Artist]
		[offset:0]
		[00:00.00]One .lrc file per song, named "Artist - Title.lrc".
		[00:04.00]This file matches no track; it only shows the format.
		[00:08.00]Each line starts with the minute:second it begins.
		[00:12.00]offset shifts every line earlier (positive) or later (negative).
		""";
}
