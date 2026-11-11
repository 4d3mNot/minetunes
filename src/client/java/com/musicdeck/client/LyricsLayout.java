package com.musicdeck.client;

/**
 * The lyric panel under the card, as pure geometry.
 *
 * It is a second, smaller card: a window of lines, the one being sung in the middle and its
 * neighbours dimmed, in the deck's own panel and border. Left alone it follows the card -
 * below it while there is room, above it when there is not, against the same screen edge -
 * so the two read as one object. Every one of those choices is overridable: {@link Options}
 * carries a position and a size, and a player who drags the words away from the card gets
 * exactly what they asked for.
 *
 * The box is measured once, in {@link #frame}, and both the painter and the move screen read
 * it. The resize grip is part of that frame rather than a second calculation, for the same
 * reason the card's buttons are: a handle that drifts from the corner it was drawn in is the
 * classic failure of an editor like this.
 */
public final class LyricsLayout {
	/** How many lines are on screen when nothing asks for a different number. */
	public static final int LINES = 3;
	/** The tallest window worth painting; past this it stops being lyrics and becomes a wall. */
	public static final int MAX_LINES = 9;
	/** Wide enough for a full lyric line, narrow enough not to become a second HUD. */
	public static final int MAX_WIDTH = 320;
	public static final int MIN_WIDTH = 60;
	/** The gap between the card and this panel, which is the card's own row gap. */
	public static final int GAP = 3;
	public static final int PAD = 4;
	public static final int LINE = 9;
	public static final int LINE_GAP = 2;
	/** The corner square the move screen grabs to resize, in GUI pixels. */
	public static final int GRIP = 9;
	/** The dimmest a panel may be made by hand; below this the words sit on the world. */
	public static final double MIN_OPACITY = 0.15;
	/** A requested number that means "work it out": the card's side, the widest line, three rows. */
	public static final int AUTO = -1;

	private LyricsLayout() {
	}

	/**
	 * Where the player wants the words, and how many lines when the height is automatic.
	 *
	 * Any of the four numbers being {@link #AUTO} is the shipped behaviour for that axis, which
	 * is what a fresh install and the move screen both start from.
	 */
	public record Options(int x, int y, int width, int height, int lines) {
		/** The panel as it shipped: under the card, as wide as its widest line, three rows. */
		public static final Options FOLLOW = new Options(AUTO, AUTO, AUTO, AUTO, LINES);
	}

	/**
	 * How the panel is painted, independent of where it is.
	 *
	 * {@code opacity} at or below zero keeps the deck's own alpha; a positive value replaces it,
	 * so a deck stays legible and a custom panel can be as faint or as solid as the player
	 * wants. {@code editing} adds the move screen's outline and its resize grip.
	 */
	public record Style(boolean panel, double opacity, boolean editing) {
		public static final Style DEFAULT = new Style(true, 0.0, false);
	}

	/**
	 * The box, the lines it shows, and which of them is being sung.
	 *
	 * {@code first} is the lyric at the top row and {@code current} the one in the middle;
	 * both are indices into {@code lyrics}, and {@code current} is -1 before the song's
	 * first line, when there is nothing to highlight yet.
	 */
	public record Frame(int x, int y, int width, int height, int rows, int first, int current, DeckSkin skin,
		Lyrics lyrics, double seconds) {
		/** Rows are centred in the box, so an over-tall panel does not leave all its slack at the bottom. */
		public int lineY(int row) {
			int block = rows * LINE + (rows - 1) * LINE_GAP;
			return y + (height - block) / 2 + row * (LINE + LINE_GAP);
		}

		/** The corner square the mouse grabs to resize, which is also the square that is painted. */
		public HudLayout.Rect grip() {
			int size = Math.min(GRIP, Math.min(width, height));
			return new HudLayout.Rect(x + width - size, y + height - size, size, size);
		}

		public boolean gripHolds(double mx, double my) {
			return grip().holds(mx, my);
		}

		public boolean holds(double mx, double my) {
			return new HudLayout.Rect(x, y, width, height).holds(mx, my);
		}
	}

	/** Exactly tall enough for this many rows. */
	public static int height(int rows) {
		return PAD * 2 + rows * LINE + (rows - 1) * LINE_GAP;
	}

	public static int height() {
		return height(LINES);
	}

	/** How many whole rows a box this tall shows, never fewer than one nor more than the ceiling. */
	public static int rowsFor(int height) {
		int rows = (height - PAD * 2 + LINE_GAP) / (LINE + LINE_GAP);
		return Math.max(1, Math.min(MAX_LINES, rows));
	}

	/** Measure the panel as it shipped: under the card, following its side. */
	public static Frame frame(HudCanvas canvas, Lyrics lyrics, double seconds, HudLayout.Frame card,
		int guiWidth, int guiHeight) {
		return frame(canvas, lyrics, seconds, card, Options.FOLLOW, guiWidth, guiHeight);
	}

	/**
	 * Measure the panel for this moment of this track.
	 *
	 * The automatic width comes from the widest line in the file rather than the rows being
	 * shown, so the panel does not breathe in and out as the song moves from a long line to a
	 * short one, and it never shrinks below the card above it: a narrow panel under a wide card
	 * looks like two unrelated pieces of furniture. A width or height the player set is used
	 * as asked, inside the screen: that is what the drag handles write.
	 */
	public static Frame frame(HudCanvas canvas, Lyrics lyrics, double seconds, HudLayout.Frame card,
		Options options, int guiWidth, int guiHeight) {
		int rows = options.lines() <= 0 ? LINES : Math.min(MAX_LINES, Math.max(1, options.lines()));
		int height = options.height() <= 0 ? height(rows) : options.height();
		// The panel is drawn on the screen it was measured against, so no part of it may fall
		// outside: a window too short for even one row still gets a box, just a clamped one.
		height = Math.max(1, Math.min(height, guiHeight));
		rows = rowsFor(height);

		int width;
		if (options.width() <= 0) {
			int widest = 0;
			for (Lyrics.Line line : lyrics.lines()) {
				widest = Math.max(widest, canvas.textWidth(line.text()));
			}

			width = Math.max(card.width(), widest + PAD * 2);
			width = Math.max(MIN_WIDTH, Math.min(width, Math.min(MAX_WIDTH, guiWidth)));
		} else {
			width = Math.max(MIN_WIDTH, Math.min(options.width(), guiWidth));
		}

		int placed = card.mirror() ? card.x() + card.width() - width : card.x();
		int x = options.x() < 0 ? placed : options.x();
		x = Math.max(0, Math.min(x, guiWidth - width));

		int y = options.y() < 0 ? place(card, height, guiHeight) : options.y();
		y = Math.max(0, Math.min(y, guiHeight - height));

		int current = lyrics.indexAt(seconds);
		int first = current < 0 ? 0 : Math.max(0, Math.min(current - rows / 2, lyrics.lines().size() - rows));
		return new Frame(x, y, width, height, rows, first, current, card.skin(), lyrics, seconds);
	}

	/**
	 * Below the card, else above it, else as far inside the screen as it will go.
	 *
	 * The last branch is the card sitting in the middle of a window too short for both, and
	 * it prefers the side with more room so the overlap lands on the empty half.
	 */
	static int place(HudLayout.Frame card, int height, int guiHeight) {
		int below = card.y() + card.height() + GAP;
		if (below + height <= guiHeight) {
			return below;
		}

		int above = card.y() - GAP - height;
		if (above >= 0) {
			return above;
		}

		int roomBelow = guiHeight - (card.y() + card.height());
		int roomAbove = card.y();
		int y = roomBelow >= roomAbove ? below : above;
		return Math.max(0, Math.min(y, Math.max(0, guiHeight - height)));
	}

	/** Paint a measured panel. Nothing here decides a box; it only fills the ones it was given. */
	public static void draw(HudCanvas canvas, Frame frame, int accent, boolean live) {
		draw(canvas, frame, accent, live, Style.DEFAULT);
	}

	/**
	 * Paint a measured panel in the style the player asked for.
	 *
	 * A panel switched off is words only, every row shadowed, because without the band behind
	 * them a dim neighbour over a bright world is unreadable. An opacity set by hand replaces
	 * the deck's alpha rather than scaling it, so the number in the settings file is the number
	 * on screen; the paused dim is still applied on top, which keeps the two panels in step.
	 */
	public static void draw(HudCanvas canvas, Frame frame, int accent, boolean live, Style style) {
		DeckSkin skin = frame.skin();
		int edge = Palette.scale(accent, 0.55);
		if (!live) {
			edge = Palette.scale(edge, HudLayout.PAUSED);
		}

		int inset = skin.border() ? 1 : 0;
		if (style.panel()) {
			int band = skin.panel();
			if (style.opacity() > 0.0) {
				band = Palette.withAlpha(band,
					(int) Math.round(255 * Math.min(1.0, Math.max(MIN_OPACITY, style.opacity()))));
			}

			if (!live) {
				band = HudLayout.dim(band);
			}

			if (inset > 0) {
				HudLayout.roundRect(canvas, frame.x(), frame.y(), frame.width(), frame.height(), edge);
			}

			HudLayout.roundRect(canvas, frame.x() + inset, frame.y() + inset, frame.width() - 2 * inset,
				frame.height() - 2 * inset, band);
			if (inset > 0) {
				canvas.fill(frame.x() + 2, frame.y() + 1, frame.x() + frame.width() - 2, frame.y() + 2,
					Palette.withAlpha(Palette.WHITE, live ? 26 : 14));
			}
		}

		int room = frame.width() - PAD * 2;
		int currentInk = live ? Palette.WHITE : Palette.scale(Palette.WHITE, HudLayout.PAUSED);
		int otherInk = Palette.withAlpha(currentInk, live ? 130 : 90);
		for (int row = 0; row < frame.rows(); row++) {
			int index = frame.first() + row;
			Lyrics.Line line = frame.lyrics().at(index);
			if (line == null) {
				continue;
			}

			String text = fit(canvas, line.text(), room);
			if (text.isEmpty()) {
				continue;
			}

			boolean singing = index == frame.current();
			int textX = frame.x() + (frame.width() - canvas.textWidth(text)) / 2;
			int textY = frame.lineY(row);
			if (!singing) {
				canvas.text(text, textX, textY, otherInk, !style.panel());
				continue;
			}

			// The row is painted twice: dim underneath, then the part the voice has reached
			// bright inside a scissor that ends at the current character. That is one clip and
			// no second font, so the sweep costs a single extra draw call per frame.
			canvas.text(text, textX, textY, otherInk, true);
			double fraction = Karaoke.fraction(Karaoke.times(line, Karaoke.end(frame.lyrics(), index)),
				frame.seconds());
			int stop = Karaoke.edge(canvas, text, Math.min(fraction, text.length()), textX);
			if (stop > textX) {
				canvas.clip(textX - 1, textY - 1, stop, textY + LINE + 1);
				canvas.text(text, textX, textY, currentInk, true);
				canvas.unclip();
			}
		}

		if (style.editing()) {
			HudLayout.dashed(canvas, frame.x() - 2, frame.y() - 2, frame.width() + 4, frame.height() + 4,
				Palette.withAlpha(accent, 200));
			grip(canvas, frame, Palette.withAlpha(accent, 220));
		}
	}

	/** Three dots stepping in from the corner: the part of the panel the mouse resizes. */
	static void grip(HudCanvas canvas, Frame frame, int colour) {
		int x = frame.x() + frame.width();
		int y = frame.y() + frame.height();
		for (int step = 1; step <= 3; step++) {
			canvas.fill(x - step * 3, y - step * 3, x - step * 3 + 2, y - step * 3 + 2, colour);
		}
	}

	/**
	 * The line as it can be drawn, cut at the end when it cannot.
	 *
	 * Only the pathological line reaches this: an automatic panel was measured from the widest
	 * line in the file. It exists for a panel the player resized smaller than its words, and for
	 * the line wider than the largest panel a screen will be given, and it cuts with dots rather
	 * than letting the glyphs run over the border.
	 */
	static String fit(HudCanvas canvas, String text, int room) {
		if (text.isEmpty() || canvas.textWidth(text) <= room) {
			return text;
		}

		String dots = "...";
		if (canvas.textWidth(dots) > room) {
			return "";
		}

		int end = text.length();
		while (end > 0) {
			String cut = text.substring(0, end).trim() + dots;
			if (canvas.textWidth(cut) <= room) {
				return cut;
			}

			end--;
		}

		return dots;
	}
}
