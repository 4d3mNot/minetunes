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

	/** A lyric and the second it starts. Empty text is a timed gap, not a mistake. */
	public record Line(double at, String text) {
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

			String words = plain(line.substring(at));
			for (double seconds : stamps) {
				timed.add(new Line(seconds, words));
			}
		}

		if (timed.isEmpty()) {
			return EMPTY;
		}

		// The shift goes on after the whole file has been read, because the tag is allowed to
		// sit at the bottom and still move the lines written above it.
		List<Line> shifted = new ArrayList<>(timed.size());
		for (Line line : timed) {
			shifted.add(new Line(Math.max(0.0, line.at() - offset), line.text()));
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
	 * The words as the game's font can draw them.
	 *
	 * Lyrics are pasted from the web, so they arrive with the curly quotes, en dashes and
	 * ellipses that a word processor writes. Minecraft's default font has a glyph for the
	 * ASCII forms and not always for these, and a missing glyph is a hole in the middle of
	 * the line rather than a character, so they are folded here once.
	 *
	 * Enhanced LRC marks every word with its own {@code <mm:ss.xx>} stamp. The panel paints
	 * whole lines, so those stamps are spacing, not words: leaving them in draws the timing
	 * across the screen.
	 */
	static String plain(String text) {
		return text
			.replaceAll("<\\d{1,3}:\\d{1,2}(?:\\.\\d{1,3})?>", " ")
			.replaceAll("[\\t ]+", " ")
			.replace('\u2018', '\'').replace('\u2019', '\'')
			.replace('\u201A', '\'').replace('\u201B', '\'')
			.replace('\u201C', '"').replace('\u201D', '"').replace('\u201E', '"')
			.replace("\u2026", "...")
			.replace('\u2013', '-').replace('\u2014', '-')
			.replace('\u00A0', ' ')
			.trim();
	}
}
