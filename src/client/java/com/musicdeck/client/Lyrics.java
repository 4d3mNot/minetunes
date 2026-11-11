package com.musicdeck.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One song's timed words, read from a {@code .lrc} file.
 *
 * Spotify renders its lyrics inside its own window and hands nothing about them to
 * Windows, so a third-party process cannot read them. What the card can read is the same
 * title and artist it already shows, which is enough to find a file the player supplied.
 *
 * The format is the plain-text one every player has used for thirty years: one line per
 * lyric, each prefixed with the minute and second it starts. Everything here is pure
 * text-to-data, so the whole parser runs inside the off-screen gate suite.
 */
public record Lyrics(List<Line> lines) {
	public static final Lyrics EMPTY = new Lyrics(List.of());

	/**
	 * One word of an enhanced file, timed relative to its line.
	 *
	 * Enhanced LRC stamps every word with its own {@code <mm:ss.xx>}, which is the only way
	 * to know when a syllable lands rather than when its line does. The stamp is stored as
	 * an offset from the line's own start, so the file's {@code [offset:]} tag and a line
	 * written twice for a chorus move the words with their line instead of away from it.
	 *
	 * {@code start} is an index into the line's folded text; the word ends where the next
	 * one begins, so the two always tile the line without gaps.
	 */
	public record Word(double after, int start) {
	}

	/** A lyric and the second it starts. Empty text is a timed gap, not a mistake. */
	public record Line(double at, String text, List<Word> words) {
		public Line(double at, String text) {
			this(at, text, List.of());
		}

		public Line {
			words = List.copyOf(words);
		}

		public boolean timed() {
			return !words.isEmpty();
		}
	}

	public Lyrics {
		lines = List.copyOf(lines);
	}

	public boolean present() {
		return !lines.isEmpty();
	}

	/**
	 * The line that is on screen at this second, or -1 before the first one.
	 *
	 * The list is sorted by {@link #parse}, and a line owns the time from its own stamp
	 * until the next one, so this is the last line at or before the clock.
	 */
	public int indexAt(double seconds) {
		int found = -1;
		int index = 0;
		while (index < lines.size() && lines.get(index).at() <= seconds) {
			found = index++;
		}

		return found;
	}

	public Line at(int index) {
		return index >= 0 && index < lines.size() ? lines.get(index) : null;
	}

	/**
	 * Read a whole file's text.
	 *
	 * A line can carry several stamps, which is how the format writes a chorus once and
	 * repeats it: every stamp becomes its own entry, so the reader never needs to know.
	 * The offset tag shifts the whole song and is accepted anywhere in the file rather than
	 * only at the top, because files written by different editors put it in different
	 * places; a positive offset means the words arrive earlier.
	 */
	public static Lyrics parse(String text) {
		if (text == null || text.isEmpty()) {
			return EMPTY;
		}

		if (text.charAt(0) == '\uFEFF') {
			text = text.substring(1);
		}

		List<Line> timed = new ArrayList<>();
		double offset = 0.0;
		for (String raw : text.split("\r?\n", -1)) {
			String line = raw.trim();
			if (line.isEmpty()) {
				continue;
			}

			List<Double> stamps = new ArrayList<>(1);
			int at = 0;
			while (at < line.length() && line.charAt(at) == '[') {
				int close = line.indexOf(']', at + 1);
				if (close < 0) {
					break;
				}

				String tag = line.substring(at + 1, close);
				Double seconds = time(tag);
				if (seconds != null) {
					stamps.add(seconds);
				} else if (tag.regionMatches(true, 0, "offset:", 0, 7)) {
					offset = milliseconds(tag.substring(7));
				}

				at = close + 1;
			}

			if (stamps.isEmpty()) {
				continue;
			}

			Line body = withWords(stamps.get(0), line.substring(at));
			for (double seconds : stamps) {
				// Word stamps describe the first time this line is sung. A chorus that repeats
				// the same words later gets no stamps, so it fills from the character estimate
				// rather than from times that all landed before it began.
				timed.add(new Line(seconds, body.text(),
					seconds == stamps.get(0) ? body.words() : List.of()));
			}
		}

		if (timed.isEmpty()) {
			return EMPTY;
		}

		// The shift goes on after the whole file has been read, because the tag is allowed to
		// sit at the bottom and still move the lines written above it.
		List<Line> shifted = new ArrayList<>(timed.size());
		for (Line line : timed) {
			shifted.add(new Line(Math.max(0.0, line.at() - offset), line.text(), line.words()));
		}

		shifted.sort(Comparator.comparingDouble(Line::at));
		return new Lyrics(shifted);
	}

	/**
	 * {@code [mm:ss]}, {@code [mm:ss.f]} and the thousandths some editors write.
	 *
	 * The fraction is read as a decimal of a second whatever its length, so {@code .5} is
	 * half a second rather than five hundredths. Anything that is not a stamp - a title
	 * tag, a comment, a malformed line - answers null here and is skipped by the caller.
	 */
	private static Double time(String tag) {
		int colon = tag.indexOf(':');
		if (colon <= 0) {
			return null;
		}

		String minutes = tag.substring(0, colon).trim();
		String rest = tag.substring(colon + 1).trim();
		int dot = rest.indexOf('.');
		String seconds = dot < 0 ? rest : rest.substring(0, dot);
		String fraction = dot < 0 ? "" : rest.substring(dot + 1);
		if (!digits(minutes) || !digits(seconds) || (dot >= 0 && !digits(fraction))) {
			return null;
		}

		try {
			double value = Integer.parseInt(minutes) * 60 + Integer.parseInt(seconds);
			return fraction.isEmpty() ? value : value + Double.parseDouble("0." + fraction);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static boolean digits(String text) {
		if (text.isEmpty()) {
			return false;
		}

		for (int index = 0; index < text.length(); index++) {
			if (!Character.isDigit(text.charAt(index))) {
				return false;
			}
		}

		return true;
	}

	/** The offset tag is signed milliseconds, and a file that gets it wrong gets no shift. */
	private static double milliseconds(String raw) {
		try {
			return Integer.parseInt(raw.trim()) / 1000.0;
		} catch (NumberFormatException e) {
			return 0.0;
		}
	}

	/**
	 * The words as the game's font can draw them, keeping any word stamps as timings.
	 *
	 * Lyrics are pasted from the web, so they arrive with the curly quotes, en dashes and
	 * ellipses that a word processor writes. Minecraft's default font has a glyph for the
	 * ASCII forms and not always for these, and a missing glyph is a hole in the middle of
	 * the line rather than a character, so they are folded here once - and the fold is
	 * tracked, because the word offsets are positions in the folded text, not the raw one.
	 *
	 * A {@code <mm:ss.xx>} tag is a word boundary; anything else between angle brackets is
	 * text somebody typed, and is kept.
	 */
	static Line withWords(double at, String body) {
		List<Double> marks = new ArrayList<>(1);
		List<Integer> starts = new ArrayList<>(1);
		StringBuilder raw = new StringBuilder(body.length());
		int cursor = 0;
		while (cursor < body.length()) {
			int open = body.indexOf('<', cursor);
			if (open < 0) {
				break;
			}

			int close = body.indexOf('>', open + 1);
			if (close < 0) {
				break;
			}

			Double stamp = time(body.substring(open + 1, close));
			if (stamp == null) {
				raw.append(body, cursor, open + 1);
				cursor = open + 1;
				continue;
			}

			raw.append(body, cursor, open);
			marks.add(stamp - at);
			starts.add(raw.length());
			cursor = close + 1;
		}

		raw.append(body, cursor, body.length());

		StringBuilder text = new StringBuilder(raw.length());
		int[] map = new int[raw.length() + 1];
		for (int index = 0; index < raw.length(); index++) {
			map[index] = text.length();
			char c = raw.charAt(index);
			if (c == ' ' || c == '\t' || c == '\u00A0') {
				if (text.length() > 0 && text.charAt(text.length() - 1) != ' ') {
					text.append(' ');
				}

				continue;
			}

			String folded = fold(c);
			text.append(folded == null ? String.valueOf(c) : folded);
		}

		map[raw.length()] = text.length();
		while (text.length() > 0 && text.charAt(text.length() - 1) == ' ') {
			text.setLength(text.length() - 1);
		}

		List<Word> words = new ArrayList<>(marks.size());
		for (int index = 0; index < marks.size(); index++) {
			words.add(new Word(Math.max(0.0, marks.get(index)),
				Math.min(map[starts.get(index)], text.length())));
		}

		return new Line(at, text.toString(), words);
	}

	/** The ASCII stand-ins for the punctuation a word processor writes, or null to keep it. */
	private static String fold(char c) {
		return switch (c) {
			case '\u2018', '\u2019', '\u201A', '\u201B' -> "'";
			case '\u201C', '\u201D', '\u201E' -> "\"";
			case '\u2026' -> "...";
			case '\u2013', '\u2014' -> "-";
			default -> null;
		};
	}
}
