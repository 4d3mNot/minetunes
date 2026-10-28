package com.musicdeck.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The now-playing card, as pure geometry.
 *
 * Given a canvas, a snapshot and a colour, this decides every pixel. Nothing in here
 * knows about Minecraft, which is what makes the card reviewable as a rendered picture
 * instead of a guess.
 *
 * The boxes are computed once, in {@link #frame}, and both the painter and the mouse
 * read them. A button whose click target drifts away from its drawn rectangle is the
 * classic failure of an overlay like this, so there is deliberately no second copy of
 * the arithmetic to drift from.
 */
public final class HudLayout {
	public static final int TILE = 48;
	public static final int PREV = 0;
	public static final int PLAY = 1;
	public static final int NEXT = 2;
	public static final int REPEAT = 3;
	public static final int BUTTONS = 4;
	public static final int BTN_W = 14;
	public static final int BTN_H = 11;
	public static final int PAD = 4;
	public static final int GAP = 5;
	private static final int LINE = 9;
	/** How far below the top of its band the first line of text sits. */
	private static final int TEXT_SINK = 3;
	private static final int BAR_GAP = 3;
	private static final int ROW_GAP = 3;
	public static final int MIN_CONTENT = 44;
	public static final int MAX_CONTENT = 118;
	/**
	 * The one length a headline is still cut at, and it is a runaway guard rather than a
	 * display budget: SMTC hands over whatever text the player typed, and a marquee that has
	 * to crawl through a thousand characters never reaches its end inside one track.
	 */
	public static final int MAX_HEADLINE_CHARS = 200;
	private static final int BTN_STEP = BTN_W + 2;
	/** How much of the panel's light a paused card keeps; the built-in deck ships exactly this. */
	private static final double IDLE_RATIO = 180.0 / 216.0;
	private static final double PAUSED = 0.68;
	private static final double INERT = 0.45;
	/** How fast a line that does not fit travels, in GUI pixels per second. */
	public static final double SCROLL_PX_PER_SEC = 22.0;
	/** How long the card rests at each end, which is how long it takes to read a line. */
	public static final double SCROLL_REST_SEC = 1.1;
	/** The shortest leg there is: below this the slide is a twitch rather than a read. */
	private static final double MIN_TRAVEL_SEC = 1.5;
	/** The longest: no title should make the player wait ten seconds for its last word. */
	private static final double MAX_TRAVEL_SEC = 6.0;
	/** An overhang smaller than about one glyph is not worth sliding for. */
	private static final int SLIDE_MIN_PX = 6;

	private HudLayout() {
	}

	/** Where the player asked for the card, and which deck it is drawn from. */
	public record Options(int x, int y, DeckSkin skin) {
	}

	/**
	 * @param repeat      the mode to paint, empty when neither the player nor the session knows
	 * @param hover       which button the mouse is over, or -1
	 * @param pressed     which button was just clicked, or -1; it stays lit past the hover
	 * @param interactive false while the game owns the mouse, so the buttons paint as inert
	 * @param editing     draw the placement outline instead of a finished card
	 */
	public record Input(String repeat, int hover, int pressed, boolean interactive, boolean editing) {
		public static final Input INERT = new Input("", -1, -1, false, false);
	}

	public record Rect(int x, int y, int w, int h) {
		public boolean holds(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	/**
	 * The boxes inside the panel, measured once.
	 *
	 * Every one of these is a place the painter draws and the mouse clicks, so they are
	 * computed together and carried as a unit. A deck can move the artwork above the text,
	 * shrink the tile to nothing or drop the control row, and the two still agree because
	 * neither of them is allowed to do its own arithmetic.
	 */
	public record Parts(int tileX, int tileY, int textX, int textY, int column, int barY, int rowY) {
		public static final Parts NONE = new Parts(0, 0, 0, 0, 0, 0, 0);
	}

	/**
	 * Everything the card occupies, with the text measured but never cut to fit.
	 *
	 * {@code mirror} says the card is reading from the other side because it is parked against
	 * the right half of the screen; every box in {@code parts} already accounts for it.
	 */
	public record Frame(int x, int y, int width, int height, Parts parts, boolean mirror, DeckSkin skin, String title,
		String detail, String time, List<Rect> buttons) {

		/** Before the first frame is measured there is no box to grab. */
		public static final Frame EMPTY = new Frame(0, 0, 0, 0, Parts.NONE, false, DeckSkin.BUILT_IN, "", "", "",
			List.of());

		public int buttonAt(double mx, double my) {
			for (int index = 0; index < buttons.size(); index++) {
				if (buttons.get(index).holds(mx, my)) {
					return index;
				}
			}

			return -1;
		}

		public boolean holds(double mx, double my) {
			return new Rect(x, y, width, height).holds(mx, my);
		}
	}

	/**
	 * Whether the card should exist at all this frame.
	 *
	 * The rule lives here rather than in the draw call so it can be gated: a panel that
	 * silently vanishes when the player pauses, or one that refuses to hide, is the same
	 * kind of bug as a misplaced button.
	 */
	public static boolean visible(NowPlaying playing, boolean showHud, boolean onlyWhenPlaying, boolean editing) {
		if (editing) {
			// The placement editor always has something to drag, even with no session.
			return true;
		}

		return showHud && playing.hasTrack() && (!onlyWhenPlaying || playing.playing());
	}

	/** The width the control row needs: four buttons and the gaps between them. */
	public static int rowWidth() {
		return BUTTONS * BTN_W + (BUTTONS - 1) * (BTN_STEP - BTN_W);
	}

	/** What the elapsed clock takes off the end of the detail line, so the two never touch. */
	private static int reserved(HudCanvas canvas, String time) {
		return time.isEmpty() ? 0 : canvas.textWidth(time) + 4;
	}

	/**
	 * How long one leg of the slide takes for a card whose worst line overhangs by this much.
	 *
	 * Both lines share the answer, and that is what keeps them in step: the card runs out,
	 * rests and comes back as one piece rather than two clocks drifting apart, so the start of
	 * the title and the start of the artist are on screen together and so are their ends.
	 *
	 * Clamped at both ends. A long title must not crawl for ten seconds before its last word
	 * appears, and a line that barely overhangs must not twitch to fill a whole leg.
	 */
	public static double travelSeconds(int overhang) {
		if (overhang <= SLIDE_MIN_PX) {
			return 0.0;
		}

		return Math.min(MAX_TRAVEL_SEC, Math.max(MIN_TRAVEL_SEC, overhang / SCROLL_PX_PER_SEC));
	}

	/**
	 * How long one leg of this card's slide takes, measured from the line that overhangs most.
	 *
	 * Both lines are drawn against the answer, so the whole card runs out and comes back as
	 * one clock. Callers that show the motion must time it with this rather than their own
	 * guess at the column, or the picture and the overlay disagree.
	 */
	public static double travelSeconds(HudCanvas canvas, Frame frame) {
		int column = frame.parts().column();
		int detailRoom = column - reserved(canvas, frame.time());
		return travelSeconds(Math.max(canvas.textWidth(frame.title()) - column,
			frame.detail().isEmpty() ? 0 : canvas.textWidth(frame.detail()) - detailRoom));
	}

	/**
	 * Where the card's shared clock sits right now: 0 at the first letters, 1 at the last.
	 *
	 * It travels out, rests, travels back, rests, so the whole string can be read without the
	 * player doing anything.
	 */
	public static double scrollPhase(double seconds, double travel) {
		if (travel <= 0.0 || seconds < 0.0) {
			return 0.0;
		}

		double cycle = 2.0 * (travel + SCROLL_REST_SEC);
		double t = seconds - Math.floor(seconds / cycle) * cycle;
		if (t < travel) {
			return t / travel;
		}

		t -= travel;
		if (t < SCROLL_REST_SEC) {
			return 1.0;
		}

		t -= SCROLL_REST_SEC;
		if (t < travel) {
			return 1.0 - t / travel;
		}

		return 0.0;
	}

	/**
	 * How far left a line that overhangs its column by {@code overflow} has slid.
	 *
	 * The shift is bounded by the overhang by construction: sliding further would pull the
	 * text off the end of its own column and leave a hole in the panel.
	 */
	public static int scrollShift(int overflow, double phase) {
		return overflow <= SLIDE_MIN_PX ? 0 : (int) Math.round(overflow * Math.min(1.0, Math.max(0.0, phase)));
	}

	/**
	 * Draw one line inside a column: standing still when it fits, sliding when it does not.
	 *
	 * The clip is what makes the slide readable rather than a mess of glyphs painted across
	 * the panel border, so it spans exactly the column the text belongs to. It stays on even
	 * when the overhang is too small to be worth sliding for: the clip protects the
	 * neighbours, only the motion is skipped.
	 */
	private static void line(HudCanvas canvas, String text, int x, int room, int y, int colour, boolean shadow,
		double phase) {
		int overflow = canvas.textWidth(text) - room;
		if (overflow <= 0) {
			canvas.text(text, x, y, colour, shadow);
			return;
		}

		canvas.clip(x, y - 1, x + room, y + LINE + 1);
		canvas.text(text, x - scrollShift(overflow, phase), y, colour, shadow);
		canvas.unclip();
	}

	/** Measure and place the card. Safe to call every frame: it only reads strings. */
	public static Frame frame(HudCanvas canvas, NowPlaying playing, Options options, int guiWidth, int guiHeight) {
		DeckSkin skin = options.skin();
		int pad = skin.pad();
		int gap = skin.gap();
		int tile = skin.tile();
		boolean art = skin.hasArt();
		boolean stacked = art && skin.stacked();
		String title = NowPlaying.cap(playing.headline(), MAX_HEADLINE_CHARS);
		String detail = skin.detail() ? detail(playing) : "";
		String time = skin.time() ? playing.elapsedText() : "";

		int reserved = reserved(canvas, time);
		int content = Math.max(canvas.textWidth(title),
			Math.min(canvas.textWidth(detail), skin.maxWidth()) + reserved);
		content = Math.max(skin.minWidth(), Math.min(content, skin.maxWidth()));

		// A deck that hides the second line gets a one-line block, and the panel shrinks to it:
		// reserving room for text that is not drawn is how a minimal deck ends up a grey box.
		// The clock lives on that second row, so hiding the detail line alone does not empty it.
		int textBlock = (skin.detail() || skin.time()) ? LINE * 2 : LINE;
		// The sink keeps the ascenders off the panel edge and the bar gap keeps the descenders
		// off the progress bar. The body has to pay for both: a deck that borrowed the
		// artwork's height for its text - a text-only card, or one with the sleeve on top -
		// would otherwise run its last line straight into the bar. The shipped card is
		// unaffected, because its 48px tile already dwarfs the two lines.
		int textRoom = textBlock + TEXT_SINK + BAR_GAP;
		int body = stacked ? tile + gap + textRoom : Math.max(art ? tile : 0, textRoom);
		int width = pad * 2 + (art && !stacked ? tile + gap : 0) + content;
		if (stacked) {
			width = pad * 2 + Math.max(tile, content);
		}

		int height = pad + body + (skin.progress() ? BAR_GAP + 2 : 0)
			+ (skin.buttons() ? ROW_GAP + BTN_H : 0) + pad;
		int x = Math.max(0, Math.min(options.x(), guiWidth - width));
		int y = Math.max(0, Math.min(options.y(), guiHeight - height));

		int tileY = y + pad;
		// Which side of the screen the card is parked on decides which side of its own panel the
		// sleeve sits on: a card in the right-hand corner keeps its artwork against that edge and
		// reads inward, so the corner it lives in and the corner it faces agree. Only the sleeve
		// and the text column trade places - the panel is exactly as wide, and the bar and the
		// control row are already centred - and a card as wide as the screen has no side to be on.
		boolean mirror = width < guiWidth && 2 * x + width > guiWidth;
		boolean sleeveRight = art && mirror;
		int tileX = sleeveRight ? x + width - pad - tile : x + pad;
		// The column always ends where the sleeve begins, so the clock pinned to its far end
		// sits against the artwork on either side instead of drifting under the sleeve.
		int textX = sleeveRight || stacked ? x + pad : tileX + (art ? tile + gap : 0);
		int textEnd = sleeveRight && !stacked ? tileX - gap : x + width - pad;
		int textY = stacked ? y + pad + tile + gap : y + pad + TEXT_SINK;
		int column = textEnd - textX;
		int barY = y + pad + body;
		int rowY = barY + (skin.progress() ? BAR_GAP + 2 : 0) + ROW_GAP;
		Parts parts = new Parts(tileX, tileY, textX, textY, column, barY, rowY);

		// Only the runaway guard above cuts anything, and both the click target and the text
		// are measured from the same string. A line that does not fit the box is not cut
		// again: the painter slides it, so every letter the card was given is reachable.
		title = trimDangling(title);

		List<Rect> buttons = new ArrayList<>(BUTTONS);
		// Centred under the whole card rather than left-aligned to the text column: the row
		// is the only thing below the artwork and it reads as a control bar only when it
		// sits under the middle of the panel.
		int rowW = rowWidth();
		int rowX = x + (width - rowW) / 2;
		if (skin.buttons()) {
			for (int index = 0; index < BUTTONS; index++) {
				buttons.add(new Rect(rowX + index * BTN_STEP, rowY, BTN_W, BTN_H));
			}
		}

		return new Frame(x, y, width, height, parts, mirror, skin, title, detail, time, List.copyOf(buttons));
	}

	/**
	 * Paint a measured frame.
	 *
	 * Nothing here does arithmetic: every box comes from {@code frame.parts()} and every
	 * choice about what exists at all comes from {@code frame.skin()}. That is what lets a
	 * deck remove the artwork or the control row without the mouse agreeing to disagree.
	 *
	 * @param accent the card's colour, sampled from the artwork by the caller
	 * @param hasArt whether an album tile is available to blit
	 * @param flash  0..1, how hard the border is lit right after a track change
	 * @param scroll seconds since the card started showing this track, which is what drives
	 *               the slide of any line too wide for its column
	 */
	public static void draw(HudCanvas canvas, Frame frame, NowPlaying playing, Input input, int accent,
		boolean hasArt, double flash, double scroll) {
		DeckSkin skin = frame.skin();
		Parts parts = frame.parts();
		boolean live = playing.playing();
		int x = frame.x();
		int y = frame.y();
		int width = frame.width();
		int height = frame.height();
		int pad = skin.pad();
		int tile = skin.tile();
		int edge = Palette.scale(accent, 0.55);
		if (flash > 0.0) {
			edge = Palette.mix(edge, Palette.WHITE, 0.55 * flash);
		}

		if (!live) {
			edge = Palette.scale(edge, PAUSED);
		}

		// A deck with no border is a flat panel: no accent edge and no top highlight, because
		// both of those are the outline, and the panel fill still needs the whole rectangle.
		int inset = skin.border() ? 1 : 0;
		if (inset > 0) {
			roundRect(canvas, x, y, width, height, edge);
		}

		roundRect(canvas, x + inset, y + inset, width - 2 * inset, height - 2 * inset,
			live ? skin.panel() : dim(skin.panel()));
		if (inset > 0) {
			canvas.fill(x + 2, y + 1, x + width - 2, y + 2, Palette.withAlpha(Palette.WHITE, live ? 26 : 14));
		}

		if (skin.hasArt()) {
			if (hasArt) {
				canvas.art(parts.tileX(), parts.tileY(), tile);
			} else {
				canvas.fill(parts.tileX(), parts.tileY(), parts.tileX() + tile, parts.tileY() + tile,
					Palette.withAlpha(accent, live ? 26 : 16));
				mark(canvas, parts.tileX() + tile / 2, parts.tileY() + tile / 2, live, shade(accent, live));
			}

			if (inset > 0) {
				ring(canvas, parts.tileX() - 1, parts.tileY() - 1, tile + 2, tile + 2,
					Palette.withAlpha(accent, live ? 120 : 60));
			}
		}

		int titleColour = live ? Palette.WHITE : Palette.scale(Palette.WHITE, PAUSED);
		int detailColour = shade(accent, live);
		int detailRoom = parts.column() - reserved(canvas, frame.time());
		// One clock for the card, timed from whichever line overhangs the most, so the two
		// lines reach their ends at the same moment instead of drifting apart.
		double phase = scrollPhase(scroll, travelSeconds(canvas, frame));
		line(canvas, frame.title(), parts.textX(), parts.column(), parts.textY(), titleColour, true, phase);
		if (!frame.detail().isEmpty()) {
			line(canvas, frame.detail(), parts.textX(), detailRoom, parts.textY() + LINE, detailColour, false, phase);
		}

		if (!frame.time().isEmpty()) {
			// Pinned to the far end of the column, which is the end nearest the sleeve whichever
			// side the card reads from; the panel's own pad would drop the clock under the artwork.
			canvas.text(frame.time(), parts.textX() + parts.column() - canvas.textWidth(frame.time()),
				parts.textY() + LINE, detailColour, false);
		}

		if (skin.progress()) {
			int barX = x + pad;
			int barW = width - pad * 2;
			canvas.fill(barX, parts.barY(), barX + barW, parts.barY() + 2,
				Palette.withAlpha(Palette.WHITE, live ? 56 : 34));

			double fraction = playing.fraction();
			int filled = (int) Math.round(barW * fraction);
			if (filled > 0) {
				int barColour = shade(accent, live);
				canvas.fill(barX, parts.barY(), barX + Math.min(filled, barW), parts.barY() + 2, barColour);
				if (fraction < 1.0) {
					canvas.fill(barX + filled - 1, parts.barY() - 1, barX + filled + 1, parts.barY() + 3,
						Palette.mix(barColour, Palette.WHITE, 0.6));
				}
			}
		}

		for (int index = 0; index < frame.buttons().size(); index++) {
			button(canvas, frame.buttons().get(index), index, index == input.hover(),
				index == input.pressed(), input.interactive(), live, accent, repeatMode(input.repeat()));
		}

		if (input.editing()) {
			dashed(canvas, x - 2, y - 2, width + 4, height + 4, Palette.withAlpha(accent, 200));
		}
	}

	/**
	 * The same panel with less light in it, for a paused track.
	 *
	 * The alpha is scaled rather than replaced, so a deck that chose a see-through panel
	 * still goes see-through-minus when the music stops instead of snapping back to the
	 * built-in's opacity. The ratio is the shipped card's own: 0xD8 live to 0xB4 idle.
	 */
	private static int dim(int panel) {
		int alpha = (int) Math.round(((panel >>> 24) & 0xFF) * IDLE_RATIO);
		return Palette.withAlpha(panel, alpha);
	}

	/** The verb a button sends, or null for nothing. The click path and the paint share this. */
	public static String command(int index, String repeat) {
		return switch (index) {
			case PREV -> "prev";
			case PLAY -> "toggle";
			case NEXT -> "next";
			case REPEAT -> "repeat:" + nextMode(repeat);
			default -> null;
		};
	}

	/** None, Track and Album as SMTC names them; Spotify will not read any of them back. */
	static String nextMode(String current) {
		return switch (normalise(current)) {
			case "Track" -> "Album";
			case "Album" -> "None";
			default -> "Track";
		};
	}

	private static String normalise(String raw) {
		if (raw == null || raw.isEmpty()) {
			return "None";
		}

		String lowered = raw.toLowerCase();
		if (lowered.startsWith("track")) {
			return "Track";
		}

		return lowered.startsWith("al") || lowered.startsWith("repeatal") ? "Album" : "None";
	}

	private static int repeatMode(String raw) {
		return switch (normalise(raw)) {
			case "Track" -> 1;
			case "Album" -> 2;
			default -> 0;
		};
	}

	/** A paused card is dimmer, and dimming is a multiplier, never a different hue. */
	private static int shade(int accent, boolean live) {
		return live ? accent : Palette.scale(accent, PAUSED);
	}

	private static void button(HudCanvas canvas, Rect box, int index, boolean hover, boolean press,
		boolean interactive, boolean live, int accent, int mode) {
		int cx = box.x() + box.w() / 2;
		int cy = box.y() + box.h() / 2;
		boolean lit = hover || press;
		int ink = interactive
			? (lit ? Palette.WHITE : Palette.scale(accent, live ? 1.0 : PAUSED))
			: Palette.scale(Palette.GREY, INERT);

		if (interactive) {
			canvas.fill(box.x(), box.y(), box.x() + box.w(), box.y() + box.h(),
				Palette.withAlpha(lit ? accent : Palette.WHITE, lit ? 110 : 22));
			ring(canvas, box.x(), box.y(), box.w(), box.h(),
				Palette.withAlpha(lit ? Palette.mix(accent, Palette.WHITE, 0.5) : accent, lit ? 235 : 80));
		}

		switch (index) {
			case PREV -> previous(canvas, cx, cy, ink);
			case PLAY -> mark(canvas, cx, cy, live, ink);
			case NEXT -> next(canvas, cx, cy, ink);
			default -> repeat(canvas, cx, cy, ink, mode);
		}
	}

	/** The mark inside the tile and inside the play button: what space would do next. */
	private static void mark(HudCanvas canvas, int cx, int cy, boolean playing, int colour) {
		if (playing) {
			canvas.fill(cx - 3, cy - 4, cx - 1, cy + 4, colour);
			canvas.fill(cx + 1, cy - 4, cx + 3, cy + 4, colour);
			return;
		}

		// A right-pointing triangle, one pixel shorter at each end for every two columns.
		for (int column = 0; column < 7; column++) {
			int inset = column / 2;
			canvas.fill(cx - 3 + column, cy - 4 + inset, cx - 2 + column, cy + 4 - inset, colour);
		}
	}

	private static void previous(HudCanvas canvas, int cx, int cy, int colour) {
		// The skip bar is two pixels wide on purpose: a single one reads as part of
		// the triangle, and a bare right-pointing triangle is already the play mark.
		canvas.fill(cx - 5, cy - 4, cx - 3, cy + 4, colour);
		for (int column = 0; column < 7; column++) {
			int inset = (6 - column) / 2;
			canvas.fill(cx - 3 + column, cy - 4 + inset, cx - 2 + column, cy + 4 - inset, colour);
		}
	}

	private static void next(HudCanvas canvas, int cx, int cy, int colour) {
		for (int column = 0; column < 7; column++) {
			int inset = column / 2;
			canvas.fill(cx - 3 + column, cy - 4 + inset, cx - 2 + column, cy + 4 - inset, colour);
		}

		canvas.fill(cx + 4, cy - 4, cx + 6, cy + 4, colour);
	}

	/**
	 * A loop with a head on it, and zero, one or two dots for off, one track and the queue.
	 *
	 * The dots matter more than they look: SMTC reports the repeat mode back for some
	 * players and nothing for Spotify, so when nothing comes back the card shows what the
	 * last click asked for, and the dots are the only place that claim can live.
	 */
	private static void repeat(HudCanvas canvas, int cx, int cy, int colour, int mode) {
		canvas.fill(cx - 4, cy - 3, cx + 4, cy - 2, colour);
		canvas.fill(cx - 4, cy + 2, cx + 4, cy + 3, colour);
		canvas.fill(cx - 5, cy - 2, cx - 4, cy + 2, colour);
		canvas.fill(cx + 4, cy - 2, cx + 5, cy + 2, colour);
		// The arrow: the loop is open at the top left and points back into itself.
		canvas.fill(cx - 6, cy - 4, cx - 4, cy - 2, colour);
		canvas.fill(cx - 5, cy - 5, cx - 3, cy - 4, colour);

		if (mode == 1) {
			canvas.fill(cx - 1, cy - 1, cx + 1, cy + 1, colour);
		} else if (mode >= 2) {
			canvas.fill(cx - 3, cy - 1, cx - 1, cy + 1, colour);
			canvas.fill(cx + 1, cy - 1, cx + 3, cy + 1, colour);
		}
	}

	/** A 1px outline with gaps in it: the only honest way to say "this is not finished". */
	private static void dashed(HudCanvas canvas, int x, int y, int w, int h, int colour) {
		for (int column = 0; column < w; column += 3) {
			if (column % 6 == 0) {
				canvas.fill(x + column, y, x + column + 1, y + 1, colour);
				canvas.fill(x + column, y + h - 1, x + column + 1, y + h, colour);
			}
		}

		for (int row = 0; row < h; row += 3) {
			if (row % 6 == 0) {
				canvas.fill(x, y + row, x + 1, y + row + 1, colour);
				canvas.fill(x + w - 1, y + row, x + w, y + row + 1, colour);
			}
		}
	}

	/** A cut can land right after the " - " that joins artist to album, or just after an
	 * opening bracket, which leaves a mark pointing at a half that never arrives. */
	private static String trimDangling(String text) {
		if (!text.endsWith("...")) {
			return text;
		}

		int end = text.length() - 3;
		while (end > 0 && "- ([{".indexOf(text.charAt(end - 1)) >= 0) {
			end--;
		}

		return end == 0 ? text : text.substring(0, end) + "...";
	}

	/** The second line: who and what, or the player's own state when there is nothing. */
	private static String detail(NowPlaying playing) {
		// A session with no title puts the artist on the first line instead, so it must
		// not be repeated underneath itself.
		String artist = playing.title().isEmpty() ? "" : playing.artist();
		String album = playing.album();
		if (!artist.isEmpty()) {
			// ASCII separator, for the same font reason as the title.
			return album.isEmpty() ? artist : artist + " - " + album;
		}

		if (!album.isEmpty()) {
			return album;
		}

		return playing.status();
	}

	/** A rectangle with its two corner pixels bitten off; the only rounding a 1px grid allows. */
	private static void roundRect(HudCanvas canvas, int x, int y, int w, int h, int colour) {
		canvas.fill(x, y + 1, x + w, y + h - 1, colour);
		canvas.fill(x + 1, y, x + w - 1, y + 1, colour);
		canvas.fill(x + 1, y + h - 1, x + w - 1, y + h, colour);
	}

	private static void ring(HudCanvas canvas, int x, int y, int w, int h, int colour) {
		canvas.fill(x + 1, y, x + w - 1, y + 1, colour);
		canvas.fill(x + 1, y + h - 1, x + w - 1, y + h, colour);
		canvas.fill(x, y + 1, x + 1, y + h - 1, colour);
		canvas.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
	}
}
