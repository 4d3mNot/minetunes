import com.musicdeck.client.DeckLibrary;
import com.musicdeck.client.DeckSkin;
import com.musicdeck.client.HudCanvas;
import com.musicdeck.client.HudLayout;
import com.musicdeck.client.NowPlaying;
import com.musicdeck.client.Palette;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Renders the now-playing card off-screen, to a PNG, with the game's own glyph bitmaps.
 *
 * This exists because the card's geometry is pure: {@link HudLayout} draws into a
 * {@link HudCanvas}, so the same code that runs in the overlay can run here. Launching a
 * second 1.21.11 client beside a running one to take a screenshot costs about 4 GB of a
 * 16 GB machine, which is not a fair price for a look at a widget.
 *
 * Glyph shapes and widths come from ascii.png measured the way the game measures them
 * (rightmost opaque column plus one pixel of spacing), with the space advance of 4 read
 * out of the font's own space.json, so text is pixel-accurate for ASCII. Compositing is
 * straight Java2D alpha rather than the game's, so read colour, size and placement as
 * evidence and the exact edge of a translucent fill as approximate.
 *
 * Build and run from the project root. Only the game-free classes go on the javac line:
 *   javac -d build-preview tools/HudPreview.java \
 *     src/client/java/com/musicdeck/client/{HudCanvas,HudLayout,NowPlaying,Palette,JsonLine}.java
 *   java -cp build-preview HudPreview build-art/art.png build-art/hud-preview.png
 */
public final class HudPreview {
	private static final int SCALE = 3;
	private static final int CELL = 8;
	private static final int GUI_WIDTH = 640;
	private static final int GUI_HEIGHT = 1000;
	private static final int PIXEL_BLOCK = 4;

	public static void main(String[] args) throws Exception {
		McFont font = new McFont(ImageIO.read(new File("tools/ascii.png")));
		BufferedImage art = ImageIO.read(new File(args[0]));
		int[] artPixels = art.getRGB(0, 0, art.getWidth(), art.getHeight(), null, 0, art.getWidth());
		int accent = Palette.accent(artPixels, Palette.GREEN);
		BufferedImage blocks = pixelate(art, PIXEL_BLOCK);

		long now = System.currentTimeMillis();
		NowPlaying live = state("Playing", "Boy's a liar Pt. 2", "PinkPantheress", "Boy's a liar Pt. 2",
			63.0, 131.0, "", now);
		NowPlaying paused = state("Paused", "Boy's a liar Pt. 2", "PinkPantheress", "Boy's a liar Pt. 2",
			63.0, 131.0, "", now);
		NowPlaying album = state("Playing", "Welcome To New York (From The Vault)", "Taylor's Version",
			"1989", 12.0, 231.0, "Album", now);
		NowPlaying noDuration = state("Playing", "The Long Wait", "An Interview", "", 4021.0, 0.0, "", now);

		Sheet sheet = new Sheet(font, GUI_WIDTH);
		sheet.row("a screen is open, so the row is live; the mouse is over play/pause");
		sheet.card(art, live, accent, true, 0.0, new HudLayout.Input("", 1, -1, true, false));
		sheet.row("paused: the whole card dims and the middle button becomes a triangle");
		sheet.card(art, paused, accent, true, 0.0, new HudLayout.Input("", -1, -1, true, false));
		sheet.row("the same tile at 48px, and the same tile read as 4px blocks");
		sheet.card(blocks, live, accent, true, 0.0, HudLayout.Input.INERT);
		sheet.row("repeat reported as Album, artwork missing, border lit for the first 0.7s of a track");
		sheet.card(null, album, Palette.GREEN, false, 1.0, new HudLayout.Input("Album", -1, -1, true, false));
		sheet.row("/deck move: the placement editor, an outline and nothing clickable");
		sheet.card(art, live, accent, true, 0.0, new HudLayout.Input("", -1, -1, false, true));
		sheet.row("no screen open and no duration reported, from a non-Spotify session");
		sheet.card(null, noDuration, Palette.GREY, false, 0.0, HudLayout.Input.INERT);
		sheet.row("the white theme, over a cover that says something else");
		sheet.card(art, live, Palette.themed(Palette.THEME_WHITE, accent, Palette.GREEN), true, 0.0,
			HudLayout.Input.INERT);
		sheet.row("the green theme, with no artwork to sample at all");
		sheet.card(null, live, Palette.themed(Palette.THEME_GREEN, 0, Palette.GREY), false, 0.0,
			HudLayout.Input.INERT);

		// Three phases of one card: this is the only way the slide is reviewable as a picture,
		// because a still frame cannot show motion. The times come from the layout's own clock,
		// so the third one really does land inside the rest at the far end.
		NowPlaying endless = state("Playing", "Welcome To New York (From The Vault) - 10 Minute Version",
			"Taylor Swift", "1989 (Deluxe Edition)", 12.0, 231.0, "", now);
		Paper ruler = new Paper(sheet.graphics, font, art);
		double travel = HudLayout.travelSeconds(ruler,
			HudLayout.frame(ruler, endless, new HudLayout.Options(0, 0, DeckSkin.BUILT_IN), GUI_WIDTH, GUI_HEIGHT));
		double[] phases = {0.0, travel / 2.0, travel + HudLayout.SCROLL_REST_SEC / 2.0};
		System.out.println("one card clock of " + Math.round(travel * 10.0) / 10.0 + "s per leg, phase "
			+ phaseOf(phases[0], travel) + " / " + phaseOf(phases[1], travel) + " / "
			+ phaseOf(phases[2], travel));
		sheet.row("a title wider than the card at the start of its cycle: the first letters of both lines");
		sheet.card(art, endless, accent, true, 0.0, phases[0], HudLayout.Input.INERT);
		sheet.row("halfway along: both lines slid half their own overhang, inside a clip of exactly the column");
		sheet.card(art, endless, accent, true, 0.0, phases[1], HudLayout.Input.INERT);
		sheet.row("resting at the far end, where the last letters of both lines are readable together");
		sheet.card(art, endless, accent, true, 0.0, phases[2], HudLayout.Input.INERT);

		// The decks, through the same loader the game uses: the folder is seeded here from the
		// strings in DeckLibrary, so what this row shows is what a player's first run gets.
		java.nio.file.Path seed = java.nio.file.Path.of("build-preview", "decks");
		DeckLibrary.seedMissing(seed);
		java.util.List<DeckSkin> decks = DeckLibrary.load(seed);
		sheet.row("the built-in card and every deck the mod seeds into config/musicdeck/decks:");
		for (DeckSkin deck : decks) {
			sheet.row(deck.name() + " - " + describe(deck));
			sheet.skin(deck);
			sheet.card(art, live, deck.accentOr(accent), true, 0.0, HudLayout.Input.INERT);
		}

		// The same looks dragged past the middle of the screen, where the card turns to face the
		// corner it sits in: the sleeve takes the outer edge, the text reads inward from it, and
		// the clock comes in against the sleeve instead of hiding underneath the cover.
		System.out.println("parked-right rows start at gui y " + sheet.y);
		sheet.row("each one parked flush against the right edge of the same 640px screen:");
		for (DeckSkin deck : decks) {
			sheet.row(deck.name() + ", parked right");
			sheet.skin(deck);
			sheet.cardRight(art, live, deck.accentOr(accent), true, HudLayout.Input.INERT);
		}

		sheet.row("the built-in card parked right, halfway through its slide: the title stops at the sleeve");
		sheet.skin(DeckSkin.BUILT_IN);
		sheet.cardRight(art, endless, accent, true, HudLayout.Input.INERT, phases[1]);
		sheet.row("and parked right with a screen open, so the mouse is over next");
		sheet.cardRight(art, live, accent, true, new HudLayout.Input("", HudLayout.NEXT, -1, true, false));

		File out = new File(args[1]);
		sheet.write(out);
		System.out.println("wrote " + out.getAbsolutePath() + " " + sheet.canvas.getWidth() + "x"
			+ sheet.canvas.getHeight());
		System.out.println("art " + art.getWidth() + "x" + art.getHeight() + " -> blocks "
			+ blocks.getWidth() + "x" + blocks.getHeight() + " -> accent " + hex(accent)
			+ " -> title width " + font.width(live.headline()) + "px");
	}

	private static NowPlaying state(String status, String title, String artist, String album, double pos,
		double end, String repeat, long now) {
		return new NowPlaying(true, "Spotify.exe", status, title, artist, album, pos, end, now, repeat);
	}

	/** What a deck does to the card, so the sheet names the knobs it actually read. */
	private static String describe(DeckSkin deck) {
		StringBuilder notes = new StringBuilder("tile " + deck.tile() + ", panel " + hex(deck.panel() & 0xFFFFFF)
			+ " at " + Math.round((deck.panel() >>> 24) * 100.0 / 255.0) + "%");
		if (deck.stacked()) {
			notes.append(", stacked");
		}

		if (!deck.hasArt()) {
			notes.append(", no art");
		}

		if (!deck.detail()) {
			notes.append(", no detail");
		}

		if (!deck.time()) {
			notes.append(", no clock");
		}

		if (!deck.progress()) {
			notes.append(", no bar");
		}

		if (!deck.buttons()) {
			notes.append(", no buttons");
		}

		if (!deck.border()) {
			notes.append(", no border");
		}

		if (deck.pinsAccent()) {
			notes.append(", colour ").append(deck.accent());
		}

		return notes.toString();
	}

	/**
	 * The game's "pixel" look, made the way {@code AlbumArt} makes it: sample every Nth
	 * pixel of the authored tile into a smaller texture, then let the NEAREST upload blow
	 * it back up. Scaling an already-large image is not the same picture.
	 */
	private static BufferedImage pixelate(BufferedImage source, int block) {
		int edge = Math.max(1, source.getWidth() / block);
		BufferedImage small = new BufferedImage(edge, edge, BufferedImage.TYPE_INT_ARGB);
		for (int row = 0; row < edge; row++) {
			for (int column = 0; column < edge; column++) {
				small.setRGB(column, row, source.getRGB(column * block, row * block));
			}
		}

		return small;
	}

	private static String hex(int argb) {
		return String.format("#%06x", argb & 0xFFFFFF);
	}

	/** Where the layout's own clock puts the card at this time, for the log line. */
	private static double phaseOf(double seconds, double travel) {
		return Math.round(HudLayout.scrollPhase(seconds, travel) * 100.0) / 100.0;
	}

	/** One label and one card per row, stacked on a grey page and trimmed to fit. */
	private static final class Sheet {
		/** Room for every row; Graphics2D silently drops pixels painted outside the canvas. */
		private static final int PAGE_HEIGHT = 2400;
		private final McFont font;
		private final Graphics2D graphics;
		private final BufferedImage canvas;
		private int y = 4;
		/** The deck the next card is drawn from; the built-in look until a row asks otherwise. */
		private DeckSkin deck = DeckSkin.BUILT_IN;

		Sheet(McFont font, int width) {
			this.font = font;
			this.canvas = new BufferedImage(width * SCALE, PAGE_HEIGHT * SCALE, BufferedImage.TYPE_INT_ARGB);
			this.graphics = canvas.createGraphics();
			this.graphics.setColor(new Color(0x54, 0x5c, 0x66));
			this.graphics.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
		}

		void row(String label) {
			font.draw(graphics, label, 6, y, 0xFFD8DEE4);
			y += 11;
		}

		void skin(DeckSkin skin) {
			this.deck = skin;
		}

		void card(BufferedImage art, NowPlaying playing, int accent, boolean hasArt, double flash,
			HudLayout.Input input) {
			card(art, playing, accent, hasArt, flash, 0.0, input);
		}

		void card(BufferedImage art, NowPlaying playing, int accent, boolean hasArt, double flash,
			double scroll, HudLayout.Input input) {
			Paper paper = new Paper(graphics, font, art);
			HudLayout.Frame frame = HudLayout.frame(paper, playing, new HudLayout.Options(0, 0, deck),
				GUI_WIDTH, GUI_HEIGHT);
			HudLayout.draw(new Offset(paper, 6, y), frame, playing, input, accent, hasArt, flash, scroll);
			y += frame.height() + 6;
		}

		/**
		 * The same card asked for the far side of the screen, drawn where the layout lands it.
		 *
		 * The page is exactly as wide as the virtual screen, so a parked-right card sits flush
		 * with the right edge of the picture - which is the only way to look at it and see that
		 * the sleeve took the corner rather than the panel just moving.
		 */
		void cardRight(BufferedImage art, NowPlaying playing, int accent, boolean hasArt,
			HudLayout.Input input) {
			cardRight(art, playing, accent, hasArt, input, 0.0);
		}

		void cardRight(BufferedImage art, NowPlaying playing, int accent, boolean hasArt,
			HudLayout.Input input, double scroll) {
			Paper paper = new Paper(graphics, font, art);
			HudLayout.Frame frame = HudLayout.frame(paper, playing, new HudLayout.Options(GUI_WIDTH, 0, deck),
				GUI_WIDTH, GUI_HEIGHT);
			// No page margin here: a parked-right frame already carries its own absolute x, so
			// shifting it again would push the card off the page and paint nothing.
			HudLayout.draw(new Offset(paper, 0, y), frame, playing, input, accent, hasArt, 0.0, scroll);
			y += frame.height() + 6;
		}

		void write(File out) throws java.io.IOException {
			BufferedImage trimmed = canvas.getSubimage(0, 0, canvas.getWidth(), Math.min(canvas.getHeight(), y * SCALE));
			BufferedImage page = new BufferedImage(trimmed.getWidth(), trimmed.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = page.createGraphics();
			g.drawImage(trimmed, 0, 0, null);
			g.dispose();
			ImageIO.write(page, "png", out);
		}
	}

	private static final class Offset implements HudCanvas {
		private final HudCanvas inner;
		private final int dx;
		private final int dy;

		Offset(HudCanvas inner, int dx, int dy) {
			this.inner = inner;
			this.dx = dx;
			this.dy = dy;
		}

		@Override
		public int textWidth(String text) {
			return inner.textWidth(text);
		}

		@Override
		public void clip(int x1, int y1, int x2, int y2) {
			inner.clip(x1 + dx, y1 + dy, x2 + dx, y2 + dy);
		}

		@Override
		public void unclip() {
			inner.unclip();
		}

		@Override
		public void text(String text, int x, int y, int colour, boolean shadow) {
			inner.text(text, x + dx, y + dy, colour, shadow);
		}

		@Override
		public void fill(int x1, int y1, int x2, int y2, int colour) {
			inner.fill(x1 + dx, y1 + dy, x2 + dx, y2 + dy, colour);
		}

		@Override
		public void art(int x, int y, int size) {
			inner.art(x + dx, y + dy, size);
		}
	}

	private static final class Paper implements HudCanvas {
		private final Graphics2D graphics;
		private final McFont font;
		private final BufferedImage art;
		/** What to hand back on unclip: the layout never nests a clip inside another. */
		private Shape outer;

		Paper(Graphics2D graphics, McFont font, BufferedImage art) {
			this.graphics = graphics;
			this.font = font;
			this.art = art;
		}

		@Override
		public int textWidth(String text) {
			return font.width(text);
		}

		@Override
		public void clip(int x1, int y1, int x2, int y2) {
			outer = graphics.getClip();
			graphics.clip(new Rectangle(x1 * SCALE, y1 * SCALE, (x2 - x1) * SCALE, (y2 - y1) * SCALE));
		}

		@Override
		public void unclip() {
			graphics.setClip(outer);
			outer = null;
		}

		@Override
		public void text(String text, int x, int y, int colour, boolean shadow) {
			if (shadow) {
				font.draw(graphics, text, x + 1, y + 1, shadowOf(colour));
			}

			font.draw(graphics, text, x, y, colour);
		}

		@Override
		public void fill(int x1, int y1, int x2, int y2, int colour) {
			graphics.setColor(new Color(colour, true));
			graphics.fillRect(x1 * SCALE, y1 * SCALE, (x2 - x1) * SCALE, (y2 - y1) * SCALE);
		}

		@Override
		public void art(int x, int y, int size) {
			if (art == null) {
				return;
			}

			// The game uploads this texture with a NEAREST sampler, so the preview has to
			// magnify the same way or the "pixel" style would look like a smooth thumbnail.
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			graphics.drawImage(art, x * SCALE, y * SCALE, (x + size) * SCALE, (y + size) * SCALE,
				0, 0, art.getWidth(), art.getHeight(), null);
		}

		/** The game's own rule: a quarter of the text colour, offset one pixel down and right. */
		private static int shadowOf(int colour) {
			int alpha = (colour >>> 24) & 0xFF;
			return alpha / 4 << 24 | (colour & 0xFCFCFC) >> 2;
		}
	}

	/** The game's bitmap font: 8x8 cells, sixteen per row, in codepoint order. */
	private static final class McFont {
		private final boolean[][] bits = new boolean[128][CELL * CELL];
		private final int[] advance = new int[128];

		McFont(BufferedImage sheet) {
			int columns = sheet.getWidth() / CELL;
			for (int cp = 0; cp < 128; cp++) {
				int originX = (cp % columns) * CELL;
				int originY = (cp / columns) * CELL;
				int rightmost = -1;
				for (int row = 0; row < CELL; row++) {
					for (int column = 0; column < CELL; column++) {
						boolean on = ((sheet.getRGB(originX + column, originY + row) >>> 24) & 0xFF) != 0;
						bits[cp][row * CELL + column] = on;
						if (on && column > rightmost) {
							rightmost = column;
						}
					}
				}

				// One pixel of spacing after every glyph, except the space, whose advance
				// the font's own space.json sets to 4.
				advance[cp] = cp == ' ' ? 4 : rightmost + 2;
			}
		}

		int advance(char ch) {
			return ch < 128 ? advance[ch] : 0;
		}

		int width(String text) {
			int total = 0;
			for (int i = 0; i < text.length(); i++) {
				total += advance(text.charAt(i));
			}

			return total;
		}

		void draw(Graphics2D graphics, String text, int x, int y, int colour) {
			int rgb = colour & 0xFFFFFF;
			graphics.setColor(new Color(colour >>> 24 << 24 | rgb, true));
			int pen = x;
			for (int i = 0; i < text.length(); i++) {
				char ch = text.charAt(i);
				if (ch < 128) {
					for (int row = 0; row < CELL; row++) {
						for (int column = 0; column < CELL; column++) {
							if (bits[ch][row * CELL + column]) {
								graphics.fillRect((pen + column) * SCALE, (y + row) * SCALE, SCALE, SCALE);
							}
						}
					}
				}

				pen += advance(ch);
			}
		}
	}
}
