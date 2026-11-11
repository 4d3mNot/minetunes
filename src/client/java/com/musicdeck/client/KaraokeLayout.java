package com.musicdeck.client;

/**
 * The karaoke screen as pure geometry: one line big in the middle, the next one under it.
 *
 * The panel under the card already shows the words, so this is not a second copy of the same
 * thing: it is the version you can read from the back of the room. The line is drawn at an
 * integer scale chosen so it fits, the character the voice is on is lit one at a time, and a
 * line that follows a gap gets a count-in so nobody comes in on the wrong beat.
 *
 * Everything is measured and painted through {@link HudCanvas}, which is what lets the gate
 * suite check the placement and the lit prefix without a window. The caller applies
 * {@link Stage#scale()} to the drawing pose, so every coordinate here is in the stage's own
 * units: a screen twice the size shows the same layout twice as large.
 */
public final class KaraokeLayout {
	/** How far the words stay from the screen edge, in stage pixels. */
	public static final int MARGIN = 12;
	/** Where the header and the footer sit, measured from the edge they belong to. */
	public static final int HEADER_Y = 10;
	public static final int HINT_Y = 12;
	/** The gap under the sung line, and the one above the count-in. */
	public static final int NEXT_GAP = 7;
	public static final int COUNT_GAP = 8;
	/** The largest zoom worth trying; past this a line stops fitting and the words get chunky. */
	public static final int MAX_SCALE = 3;
	/** How dark the world is behind the words. Not black: the game staying visible is the point. */
	private static final int WASH = 0xFF0B0B0B;

	/**
	 * Where the stage paints this moment.
	 *
	 * {@code fraction} is how many characters of {@code current} the voice has reached, which
	 * is fractional in the middle of a letter; the painter lights whole characters from it.
	 * {@code countdown} is 0 unless a line is coming up.
	 */
	public record Stage(int scale, String current, int currentX, int currentY, double fraction,
		String next, int nextX, int nextY, int countdown, int countdownX, int countdownY) {
		public static final Stage EMPTY = new Stage(1, "", 0, 0, 0.0, "", 0, 0, 0, 0, 0);
	}

	private KaraokeLayout() {
	}

	/**
	 * The zoom at which the line and its neighbour both fit, as a whole number.
	 *
	 * Whole numbers only: half-size text on a pixel font lands on half pixels and reads as a
	 * blur, and the point of this screen is that it is legible from across a room.
	 */
	public static int scale(HudCanvas canvas, String current, String next, int width) {
		int widest = Math.max(canvas.textWidth(current), canvas.textWidth(next));
		if (widest <= 0) {
			return 1;
		}

		for (int candidate = MAX_SCALE; candidate > 1; candidate--) {
			if (widest * candidate + MARGIN * 2 <= width) {
				return candidate;
			}
		}

		return 1;
	}

	/**
	 * Measure the stage for this moment of this track, in the stage's own pixels.
	 *
	 * {@code width} and {@code height} are the screen's, because that is what decides the zoom
	 * and how far down the middle is; everything returned is then divided by the zoom, so the
	 * caller only has to scale the pose. A line too wide for even the smallest zoom is cut, for
	 * the same reason the panel cuts: a glyph may not run over the edge of the screen.
	 */
	public static Stage stage(HudCanvas canvas, Lyrics lyrics, double seconds, int width, int height) {
		int index = lyrics.indexAt(seconds);
		Lyrics.Line line = lyrics.at(index);
		if (line == null) {
			return Stage.EMPTY;
		}

		Lyrics.Line after = lyrics.at(index + 1);
		String nextRaw = after == null ? "" : after.text();
		int zoom = scale(canvas, line.text(), nextRaw, width);
		int logical = Math.max(1, width / zoom);
		int room = Math.max(1, logical - MARGIN * 2);
		String current = LyricsLayout.fit(canvas, line.text(), room);
		String next = LyricsLayout.fit(canvas, nextRaw, room);
		int count = Karaoke.countdown(lyrics, seconds);
		String numeral = count == 0 ? "" : Integer.toString(count);
		int currentY = Math.max(0, (height * 44 / 100) / zoom);
		int countY = Math.max(0, currentY - LyricsLayout.LINE - COUNT_GAP);
		double fraction = Math.min(current.length(),
			Karaoke.fraction(Karaoke.times(line, Karaoke.end(lyrics, index)), seconds));
		return new Stage(zoom, current, centred(canvas, current, logical), currentY, fraction,
			next, centred(canvas, next, logical), currentY + LyricsLayout.LINE + NEXT_GAP,
			count, centred(canvas, numeral, logical), countY);
	}

	/** Paint a measured stage. The caller has already scaled the pose by {@link Stage#scale()}. */
	public static void draw(HudCanvas canvas, Stage stage, int accent, boolean live) {
		int ink = live ? Palette.WHITE : Palette.scale(Palette.WHITE, HudLayout.PAUSED);
		int dim = Palette.withAlpha(ink, live ? 120 : 90);
		if (!stage.next().isEmpty()) {
			// The next line is dim all the way through rather than swept: it is a warning, not
			// a second clock.
			canvas.text(stage.next(), stage.nextX(), stage.nextY(), dim, true);
		}

		if (stage.countdown() > 0) {
			canvas.text(Integer.toString(stage.countdown()), stage.countdownX(), stage.countdownY(),
				accent, true);
		}

		String text = stage.current();
		if (text.isEmpty()) {
			return;
		}

		canvas.text(text, stage.currentX(), stage.currentY(), dim, true);
		int lit = Karaoke.bright(stage.fraction(), text.length());
		int x = stage.currentX();
		for (int index = 0; index < lit; index++) {
			String glyph = text.substring(index, index + 1);
			canvas.text(glyph, x, stage.currentY(), ink, true);
			x += canvas.textWidth(glyph);
		}
	}

	/**
	 * The part of the screen that is not the words: the wash, the track, the way out.
	 *
	 * {@code message} replaces the stage when there is nothing to sing - no session, no lyrics
	 * file - because a blank screen with a hint at the bottom looks like a bug rather than a
	 * missing file.
	 */
	public static void chrome(HudCanvas canvas, String header, String hint, String message,
		int width, int height, int accent) {
		canvas.fill(0, 0, width, height, Palette.withAlpha(WASH, 190));
		if (!header.isEmpty()) {
			canvas.text(header, centred(canvas, header, width), HEADER_Y, accent, true);
		}

		if (!message.isEmpty()) {
			int y = Math.max(0, (height - LyricsLayout.LINE) / 2);
			canvas.text(message, centred(canvas, message, width), y, Palette.WHITE, true);
		}

		if (!hint.isEmpty()) {
			int y = Math.max(0, height - HINT_Y - LyricsLayout.LINE);
			canvas.text(hint, centred(canvas, hint, width), y, Palette.withAlpha(Palette.WHITE, 150), true);
		}
	}

	/** Centred at least the margin inside a box this wide, so a long line does not touch both walls. */
	static int centred(HudCanvas canvas, String text, int room) {
		return Math.max(MARGIN, (room - canvas.textWidth(text)) / 2);
	}
}
