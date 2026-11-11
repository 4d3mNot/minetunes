package com.musicdeck.client;

/**
 * When each letter of a lyric is reached, so the words can fill as they are sung.
 *
 * Line stamps only say when a line begins, which is enough to light one row and not enough
 * to make it a karaoke bar: a fast line and a slow one look identical for their whole length.
 * This model takes the line's start, its end, and the word stamps if the file has them, and
 * answers the one question the painter asks every frame - how far into the line the voice is.
 *
 * Everything here is arithmetic on data, so the whole model runs in the off-screen gate
 * suite without a window, a font or a media session.
 */
public final class Karaoke {
	/** Seconds a line is assumed to need before its words, and per character of them. */
	private static final double LEAD = 0.9;
	private static final double PER_CHARACTER = 0.16;
	/** The floor on a line's span, so a line stamped on top of the next one still fills. */
	private static final double MIN_SPAN = 0.6;
	/** How early the count-in starts, in whole seconds. */
	public static final int COUNT_IN = 3;
	/** A space is not sung, but it is not instant either. */
	private static final double SPACE_WEIGHT = 0.5;

	private Karaoke() {
	}

	/**
	 * When the line is over.
	 *
	 * The next line's stamp is the truth, but a line that starts twenty seconds of silence is
	 * not sung for twenty seconds: the fill would crawl across it. So the gap is capped by an
	 * estimate of how long the words themselves take, and a finished line simply sits lit
	 * until the next one arrives, which is what a held note looks like anyway.
	 */
	public static double end(Lyrics lyrics, int index) {
		Lyrics.Line line = lyrics.at(index);
		if (line == null) {
			return 0.0;
		}

		double start = line.at();
		double estimate = LEAD + PER_CHARACTER * sungChars(line.text());
		Lyrics.Line next = lyrics.at(index + 1);
		double gap = next == null ? start + estimate : next.at() - start;
		if (gap < MIN_SPAN) {
			gap = MIN_SPAN;
		}

		return start + Math.min(gap, Math.max(MIN_SPAN, estimate));
	}

	/**
	 * The second each character of this line is reached at, as {@code text.length() + 1} values.
	 *
	 * With word stamps the shape of the line comes from the file. Without them it comes from
	 * the characters themselves: a long word takes longer to sing than a short one, and a
	 * space takes about half as long as a letter. That is an estimate, and it is the same
	 * estimate every karaoke display makes when the file only marks lines.
	 */
	public static double[] times(Lyrics.Line line, double end) {
		String text = line.text();
		int length = text.length();
		double start = line.at();
		double stop = Math.max(start + 0.05, end);
		double[] times = new double[length + 1];
		times[0] = start;
		if (length == 0) {
			return times;
		}

		if (line.timed()) {
			int cursor = 0;
			double reached = start;
			for (Lyrics.Word word : line.words()) {
				int from = Math.max(cursor, Math.min(word.start(), length));
				double at = Math.min(stop, Math.max(reached, start + word.after()));
				fill(times, cursor, from, reached, at);
				cursor = from;
				reached = at;
			}

			fill(times, cursor, length, reached, stop);
		} else {
			double total = 0.0;
			for (int index = 0; index < length; index++) {
				total += weight(text.charAt(index));
			}

			double cumulative = 0.0;
			for (int index = 0; index < length; index++) {
				cumulative += weight(text.charAt(index));
				times[index + 1] = start + (stop - start) * (cumulative / total);
			}
		}

		times[length] = stop;
		return times;
	}

	/** Spread the characters between two positions evenly, so no step in the line is a jump. */
	private static void fill(double[] times, int from, int to, double start, double stop) {
		if (to <= from) {
			return;
		}

		for (int index = from; index < to; index++) {
			times[index] = start + (stop - start) * ((index - from) / (double) (to - from));
		}
	}

	/**
	 * How many characters into the line the clock is, as a fraction between 0 and the length.
	 *
	 * The value between two characters is the part of that character already reached, which
	 * is what lets the painter cut the fill mid-letter instead of jumping a whole glyph.
	 */
	public static double fraction(double[] times, double seconds) {
		int last = times.length - 1;
		if (last <= 0 || seconds <= times[0]) {
			return 0.0;
		}

		if (seconds >= times[last]) {
			return last;
		}

		int low = 0;
		int high = last;
		while (low < high) {
			int middle = (low + high + 1) / 2;
			if (times[middle] <= seconds) {
				low = middle;
			} else {
				high = middle - 1;
			}
		}

		double from = times[low];
		double to = times[low + 1];
		double part = to > from ? (seconds - from) / (to - from) : 0.0;
		return Math.min(last, low + Math.max(0.0, part));
	}

	/** How many whole characters are lit at this fraction: a character lights as it starts. */
	public static int bright(double fraction, int length) {
		return Math.max(0, Math.min(length, (int) Math.ceil(fraction - 1e-6)));
	}

	/**
	 * The x the bright copy of the line stops at, in GUI pixels.
	 *
	 * The painter draws the line dim and then the same line bright inside a clip ending here,
	 * so this measures the prefix the voice has reached and the part of the next character it
	 * is inside. A canvas is all it needs, which is why the gate can predict its answer.
	 */
	public static int edge(HudCanvas canvas, String text, double fraction, int x) {
		int whole = Math.max(0, Math.min(text.length(), (int) Math.floor(fraction)));
		int edge = x + canvas.textWidth(text.substring(0, whole));
		if (whole < text.length()) {
			double part = Math.max(0.0, Math.min(1.0, fraction - whole));
			edge += (int) Math.round(canvas.textWidth(text.substring(whole, whole + 1)) * part);
		}

		return edge;
	}

	/** The countdown to the next line: 3, 2 or 1 when it is close, otherwise 0. */
	public static int countdown(Lyrics lyrics, double seconds) {
		int current = lyrics.indexAt(seconds);
		Lyrics.Line next = lyrics.at(current + 1);
		if (next == null) {
			return 0;
		}

		double left = next.at() - seconds;
		if (left <= 0.0 || left > COUNT_IN) {
			return 0;
		}

		return Math.max(1, (int) Math.ceil(left - 1e-6));
	}

	private static int sungChars(String text) {
		int sung = 0;
		for (int index = 0; index < text.length(); index++) {
			if (text.charAt(index) != ' ') {
				sung++;
			}
		}

		return sung;
	}

	private static double weight(char c) {
		return c == ' ' ? SPACE_WEIGHT : 1.0;
	}
}
