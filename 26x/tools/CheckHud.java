import com.musicdeck.client.DeckLibrary;
import com.musicdeck.client.DeckSkin;
import com.musicdeck.client.HudCanvas;
import com.musicdeck.client.HudLayout;
import com.musicdeck.client.JsonLine;
import com.musicdeck.client.Lyrics;
import com.musicdeck.client.LyricsCache;
import com.musicdeck.client.LyricsFetcher;
import com.musicdeck.client.LyricsLayout;
import com.musicdeck.client.LyricsLibrary;
import com.musicdeck.client.NowPlaying;
import com.musicdeck.client.Palette;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry and colour gates for the now-playing card, run off-screen.
 *
 * The card is drawn by {@link HudLayout} through {@link HudCanvas}, so a recorder can
 * capture every pixel decision and assert the invariants a screenshot cannot: that a line
 * too wide for its column stays inside it while it slides, that the time keeps its own
 * space at the right edge, that a paused card changes colour but not shape, and - the one
 * that matters most here - that every button's click target is the same rectangle that was
 * painted.
 *
 * Widths come from a fake 6px-per-glyph font so the arithmetic is predictable; the real
 * font is measured in the preview render.
 *
 *   javac -encoding UTF-8 -d out tools/CheckHud.java \
 *     src/client/java/com/musicdeck/client/{HudCanvas,HudLayout,NowPlaying,Palette,JsonLine,DeckSkin,DeckLibrary}.java
 *   java -cp out CheckHud
 */
public final class CheckHud {
	private static final String CLIENT_DIR = "src/client/java/com/musicdeck/client/";
	private static final String CLIENT_LANG = "src/client/resources/assets/musicdeck/lang/en_us.json";

	private static int passed;
	private static int failed;

	record Op(String kind, int x1, int y1, int x2, int y2, int colour, String text) {
	}

	static final class Recorder implements HudCanvas {
		final List<Op> ops = new ArrayList<>();

		@Override
		public int textWidth(String text) {
			return text.isEmpty() ? 0 : text.length() * 6;
		}

		@Override
		public void text(String text, int x, int y, int colour, boolean shadow) {
			ops.add(new Op("text", x, y, x + textWidth(text), y + 9, colour, text));
		}

		@Override
		public void clip(int x1, int y1, int x2, int y2) {
			ops.add(new Op("clip", x1, y1, x2, y2, 0, ""));
		}

		@Override
		public void unclip() {
			ops.add(new Op("unclip", 0, 0, 0, 0, 0, ""));
		}

		@Override
		public void fill(int x1, int y1, int x2, int y2, int colour) {
			ops.add(new Op("fill", x1, y1, x2, y2, colour, ""));
		}

		@Override
		public void art(int x, int y, int size) {
			ops.add(new Op("art", x, y, size, size, 0, ""));
		}

		List<Op> kind(String kind) {
			return ops.stream().filter(op -> op.kind().equals(kind)).toList();
		}

		/** The clip opened immediately around this text run, or null if it stands unclipped. */
		Op clipAround(Op text) {
			for (int index = 1; index < ops.size(); index++) {
				if (ops.get(index) == text && ops.get(index - 1).kind().equals("clip")) {
					return ops.get(index - 1);
				}
			}

			return null;
		}
	}

	/** Records only whether each text run asked for a shadow, which a panel-less lyric depends on. */
	static final class Ink implements HudCanvas {
		final List<Boolean> shadows = new ArrayList<>();

		@Override
		public int textWidth(String text) {
			return text.isEmpty() ? 0 : text.length() * 6;
		}

		@Override
		public void text(String text, int x, int y, int colour, boolean shadow) {
			shadows.add(shadow);
		}

		@Override
		public void clip(int x1, int y1, int x2, int y2) {
		}

		@Override
		public void unclip() {
		}

		@Override
		public void fill(int x1, int y1, int x2, int y2, int colour) {
		}

		@Override
		public void art(int x, int y, int size) {
		}
	}

	static NowPlaying playing(String status, String title, String artist, String album, double pos, double end) {
		return playing(status, title, artist, album, pos, end, "");
	}

	static NowPlaying playing(String status, String title, String artist, String album, double pos, double end,
		String repeat) {
		return new NowPlaying(true, "Spotify.exe", status, title, artist, album, pos, end,
			System.currentTimeMillis(), repeat);
	}

	/** Measure and paint a card the way the game does: one frame, then that frame drawn. */
	static Recorder draw(NowPlaying state, boolean hasArt, int accent, double flash, int guiW, int guiH,
		HudLayout.Input input) {
		return drawAt(state, hasArt, accent, flash, 6, guiW, guiH, input, 0.0);
	}

	static Recorder drawAt(NowPlaying state, boolean hasArt, int accent, double flash, int at, int guiW, int guiH,
		HudLayout.Input input, double scroll) {
		Recorder recorder = new Recorder();
		HudLayout.Frame frame = HudLayout.frame(recorder, state, new HudLayout.Options(at, at, DeckSkin.BUILT_IN), guiW, guiH);
		HudLayout.draw(recorder, frame, state, input, accent, hasArt, flash, scroll);
		return recorder;
	}

	/** The card rectangle, read back off the border band rather than assumed. */
	static int[] bounds(Recorder r) {
		Op band = r.kind("fill").get(0);
		return new int[] {band.x1(), band.y1() - 1, band.x2() - band.x1(), (band.y2() - band.y1()) + 2};
	}

	static HudLayout.Frame frame(NowPlaying state, int guiW, int guiH) {
		return HudLayout.frame(new Recorder(), state, new HudLayout.Options(6, 6, DeckSkin.BUILT_IN), guiW, guiH);
	}

	/**
	 * One deck, written inline for the gate that needs it.
	 *
	 * Everything the deck does not name is the shipped card's own value, so each check below
	 * says which knob it turned and can be read without the file in front of it.
	 */
	static DeckSkin gateDeck(String json) {
		return DeckSkin.parse("gate.json", json);
	}

	/** Measure a card drawn from one deck. */
	static HudLayout.Frame deckFrame(DeckSkin skin, NowPlaying state, int guiW, int guiH) {
		return HudLayout.frame(new Recorder(), state, new HudLayout.Options(6, 6, skin), guiW, guiH);
	}

	/** Measure and paint a card from one deck, the way {@link #drawAt} does for the shipped one. */
	static Recorder deckPaint(DeckSkin skin, NowPlaying state, boolean hasArt, int accent, int guiW, int guiH,
		HudLayout.Input input) {
		Recorder recorder = new Recorder();
		HudLayout.Frame frame = HudLayout.frame(recorder, state, new HudLayout.Options(6, 6, skin), guiW, guiH);
		HudLayout.draw(recorder, frame, state, input, accent, hasArt, 0.0, 0.0);
		return recorder;
	}

	/** Measure a card the player parked at one place on the screen, from one deck. */
	static HudLayout.Frame placeFrame(DeckSkin skin, NowPlaying state, int at, int guiW, int guiH) {
		return HudLayout.frame(new Recorder(), state, new HudLayout.Options(at, at, skin), guiW, guiH);
	}

	/** Measure and paint that same parked card, so the painter can be checked against the mouse. */
	static Recorder placePaint(DeckSkin skin, NowPlaying state, boolean hasArt, int accent, int at, int guiW,
		int guiH, HudLayout.Input input) {
		Recorder recorder = new Recorder();
		HudLayout.Frame frame = HudLayout.frame(recorder, state, new HudLayout.Options(at, at, skin), guiW, guiH);
		HudLayout.draw(recorder, frame, state, input, accent, hasArt, 0.0, 0.0);
		return recorder;
	}

	/**
	 * The progress bar's row: the widest two-pixel fill is the track itself, and anything
	 * that starts on the same pixel belongs to it. The repeat dots are also two pixels tall,
	 * but they live a row lower and start nowhere near the track, so this tells the bar apart
	 * without depending on how full it happens to be.
	 */
	static List<Op> bar(Recorder recorder) {
		List<Op> pairs = recorder.kind("fill").stream().filter(op -> op.y2() - op.y1() == 2).toList();
		Op track = pairs.stream().max(java.util.Comparator.comparingInt(op -> op.x2() - op.x1()))
			.orElseThrow(() -> new IllegalStateException("no progress bar was drawn"));
		return pairs.stream().filter(op -> op.x1() == track.x1() && op.y1() == track.y1()).toList();
	}

	/**
	 * The panel band, found by where it starts rather than by what colour it is: one pixel
	 * in from the border and two down, which no other rectangle in the card occupies.
	 */
	static Op panel(Recorder recorder) {
		int[] box = bounds(recorder);
		return recorder.kind("fill").stream()
			.filter(op -> op.x1() == box[0] + 1 && op.y1() == box[1] + 2)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no panel band was drawn"));
	}

	/**
	 * The painted face of one button: the single fill whose rectangle is exactly the
	 * hit box. This is the whole point of measuring the frame first - if the painter and
	 * the mouse ever disagree about where a button is, this finds no such fill.
	 */
	static Op face(Recorder recorder, HudLayout.Rect box) {
		return recorder.kind("fill").stream()
			.filter(op -> op.x1() == box.x() && op.y1() == box.y()
				&& op.x2() == box.x() + box.w() && op.y2() == box.y() + box.h())
			.findFirst()
			.orElse(null);
	}

	/**
	 * The strokes of one button's glyph: narrow fills that sit strictly inside its box, so
	 * the face and the ring around it are not mistaken for the mark.
	 */
	static List<Op> glyph(Recorder recorder, HudLayout.Rect box) {
		return recorder.kind("fill").stream()
			.filter(op -> op.x2() - op.x1() <= 3
				&& op.x1() > box.x() && op.x2() < box.x() + box.w()
				&& op.y1() > box.y() && op.y2() < box.y() + box.h())
			.toList();
	}

	/**
	 * Read a file from the project root, or fail loudly rather than gate on nothing.
	 *
	 * These checks are about the source tree, so a missing file is a broken run, not a
	 * passing one.
	 */
	static String read(String path) {
		try {
			return java.nio.file.Files.readString(java.nio.file.Path.of(path));
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Cannot read " + path + "; run CheckHud from the project root", e);
		}
	}

	/**
	 * The PowerShell helper with its comment lines gone.
	 *
	 * The artwork notes in that file name the very numbers the gates below look for, so a
	 * scan that left the comments in would fail on the explanation instead of on the bug.
	 */
	static String scriptOnly(String path) {
		StringBuilder kept = new StringBuilder();
		for (String line : read(path).split("\n", -1)) {
			kept.append(line.trim().startsWith("#") ? "\n" : line).append('\n');
		}

		return kept.toString();
	}

	static int count(String haystack, String needle) {
		int found = 0;
		for (int at = haystack.indexOf(needle); at >= 0;
			at = haystack.indexOf(needle, at + needle.length())) {
			found++;
		}

		return found;
	}

	/** A layout constant a screen declares for itself, read off its source so the gate cannot drift. */
	static int intLiteral(String source, String name) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(name + " = (\\d+)").matcher(source);
		if (!matcher.find()) {
			throw new IllegalStateException("No " + name + " constant in the source the gate was handed");
		}

		return Integer.parseInt(matcher.group(1));
	}

	/**
	 * The source with its comments gone and its string literals intact.
	 *
	 * Both screens carry a comment naming the call that is forbidden, so a scan that left
	 * comments in would fail on the fix instead of on the bug.
	 */
	static String codeOnly(String path) {
		StringBuilder kept = new StringBuilder();
		boolean insideBlock = false;
		for (String line : read(path).split("\n", -1)) {
			String trimmed = line.trim();
			if (insideBlock) {
				if (trimmed.startsWith("*/")) {
					insideBlock = false;
				}

				kept.append('\n');
				continue;
			}

			if (trimmed.startsWith("/*") && !trimmed.contains("*/")) {
				insideBlock = true;
			}

			kept.append(trimmed.startsWith("/*") ? "" : cutLineComment(line)).append('\n');
		}

		return kept.toString();
	}

	/** Cut a trailing // comment, but not a pair of slashes that lives inside a string. */
	static String cutLineComment(String line) {
		boolean quoted = false;
		for (int at = 0; at < line.length() - 1; at++) {
			char c = line.charAt(at);
			if (c == '"' && (at == 0 || line.charAt(at - 1) != '\\')) {
				quoted = !quoted;
			} else if (!quoted && c == '/' && line.charAt(at + 1) == '/') {
				return line.substring(0, at);
			}
		}

		return line;
	}

	/** The translation keys a source file shows a player, skipping the half-built ones. */
	static List<String> shownKeys(String path) {
		List<String> found = new ArrayList<>();
		// resolve() builds file names, and "musicdeck.properties" is shaped exactly like a
		// translation key. No key is ever passed to it, so the call site is removed first.
		String code = codeOnly(path).replaceAll("resolve\\(\\s*\"[^\"]*\"\\s*\\)", "resolve()");
		java.util.regex.Matcher matcher = java.util.regex.Pattern
			.compile("\"((?:key\\.|musicdeck\\.)[A-Za-z0-9._-]+)\"").matcher(code);
		while (matcher.find()) {
			String key = matcher.group(1);
			if (!key.endsWith(".")) {
				found.add(key);
			}
		}

		return found;
	}

	/** True when the control row leaves the same margin on both sides of the card. */
	static boolean centred(HudLayout.Frame card) {
		List<HudLayout.Rect> row = card.buttons();
		int left = row.get(0).x() - card.x();
		int right = card.x() + card.width() - (row.get(row.size() - 1).x() + HudLayout.BTN_W);
		return Math.abs(left - right) <= 1;
	}

	/**
	 * How wide the skip bar is: the outermost full-height stroke of a glyph. A triangle's
	 * own first column is full height too, so only the one at the far edge can be the bar.
	 */
	static int barWidth(List<Op> glyph, boolean barOnTheLeft) {
		int edge = barOnTheLeft ? Integer.MAX_VALUE : Integer.MIN_VALUE;
		int width = 0;
		for (Op op : glyph) {
			if (op.y2() - op.y1() != 8) {
				continue;
			}

			boolean outer = barOnTheLeft ? op.x1() < edge : op.x1() > edge;
			if (outer) {
				edge = op.x1();
				width = op.x2() - op.x1();
			}
		}

		return width;
	}

	static int alphaOf(int argb) {
		return (argb >>> 24) & 0xFF;
	}

	static int redOf(int argb) {
		return (argb >> 16) & 0xFF;
	}

	static int greenOf(int argb) {
		return (argb >> 8) & 0xFF;
	}

	static int blueOf(int argb) {
		return argb & 0xFF;
	}

	/** The panel band of a lyrics frame: one pixel in, two down, as the rounded rect is drawn. */
	static Op lyricBand(Recorder recorder, LyricsLayout.Frame frame) {
		return recorder.kind("fill").stream()
			.filter(op -> op.x1() == frame.x() + 1 && op.y1() == frame.y() + 2)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no lyric panel band was drawn"));
	}

	static void deleteTree(java.nio.file.Path root) {
		try (var walk = java.nio.file.Files.walk(root)) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
				try {
					java.nio.file.Files.deleteIfExists(path);
				} catch (java.io.IOException ignored) {
					// A temp fixture nobody can delete is the OS's problem, not the gate's.
				}
			});
		} catch (java.io.IOException ignored) {
			// Same.
		}
	}

	static void expect(boolean condition, String what) {
		if (condition) {
			passed++;
			System.out.println("ok   " + what);
		} else {
			failed++;
			System.out.println("FAIL " + what);
		}
	}

	public static void main(String[] args) throws Exception {
		int accent = 0xFF1DB954;
		NowPlaying live = playing("Playing", "Trust", "Brent Faiyaz", "Björk", 17.0, 158.4);
		NowPlaying huge = playing("Playing",
			"The Life That You Save Is The One You Lose", "A Very Long Artist Name That Keeps Going",
			"An Album Of Some Length", 30.0, 240.0);
		HudLayout.Input inert = HudLayout.Input.INERT;
		HudLayout.Input clickable = new HudLayout.Input("", -1, -1, true, false);

		// --- the model's own lines ---
		expect(live.headline().equals("Trust"), "headline is the title alone, not title plus artist");
		expect(new NowPlaying(true, "s", "Playing", "", "DJ Shadow", "a", 1, 2, 1L, "").headline()
			.equals("DJ Shadow"), "headline falls back to the artist when there is no title");
		expect(live.elapsedText().equals("0:17"), "elapsed clock, got: " + live.elapsedText());
		expect(live.label(34).equals("Trust - Brent Faiyaz"), "label joins with an ascii dash: " + live.label(34));
		expect(NowPlaying.cap("abcdefghij", 8).equals("abcde..."), "cap marks the cut, got: "
			+ NowPlaying.cap("abcdefghij", 8));
		expect(NowPlaying.cap("short", 8).equals("short"), "cap leaves a short string alone");
		expect(NowPlaying.cap("exact!", 6).equals("exact!"), "cap is inclusive at the budget");

		// --- the bridge contract the model is built from ---
		NowPlaying reported = NowPlaying.from(JsonLine.parse(
			"{\"session\":true,\"source\":\"Spotify.exe\",\"status\":\"Playing\",\"title\":\"T\","
				+ "\"artist\":\"A\",\"album\":\"B\",\"pos\":12.5,\"end\":200.0,\"repeat\":\"Track\"}"));
		expect(reported.repeat().equals("Track"), "a reported repeat mode is kept, got: " + reported.repeat());
		NowPlaying silent = NowPlaying.from(JsonLine.parse(
			"{\"session\":true,\"source\":\"Spotify.exe\",\"status\":\"Playing\",\"title\":\"T\","
				+ "\"artist\":\"A\",\"album\":\"B\",\"pos\":12.5,\"end\":200.0,\"repeat\":null}"));
		expect(silent.repeat().isEmpty(), "a null repeat from Spotify reads as unknown, not as a word");

		// --- geometry of a normal card ---
		HudLayout.Frame f = frame(live, 640, 360);
		Recorder r = draw(live, true, accent, 0.0, 640, 360, inert);
		int[] box = bounds(r);
		expect(box[2] >= HudLayout.PAD * 2 + HudLayout.TILE + HudLayout.GAP + HudLayout.MIN_CONTENT
			&& box[2] <= HudLayout.PAD * 2 + HudLayout.TILE + HudLayout.GAP + HudLayout.MAX_CONTENT,
			"card width stays between the floor and the ceiling, got " + box[2]);
		expect(frame(NowPlaying.EMPTY, 640, 360).width()
			== HudLayout.PAD * 2 + HudLayout.TILE + HudLayout.GAP + HudLayout.MIN_CONTENT,
			"a card with nothing to say still reserves the floor, so the controls never crowd");
		expect(f.buttons().size() == HudLayout.BUTTONS, "the frame carries one rect per button");
		expect(f.width() == box[2] && f.height() == box[3], "the painter drew the frame it was measured as");
		expect(box[3] > HudLayout.TILE + 2 * HudLayout.PAD + 2, "the card grew to make room for the controls");
		expect(f.x() == box[0] && f.y() == box[1], "the frame and the border band agree on the origin");
		expect(r.kind("art").size() == 1, "exactly one album tile");
		Op tile = r.kind("art").get(0);
		expect(tile.x2() == HudLayout.TILE && tile.y2() == HudLayout.TILE,
			"the tile is blitted at its texture size, so NEAREST never resamples it");
		expect(tile.x1() > box[0] && tile.y1() > box[1], "the tile sits inside the card");

		List<Op> texts = r.kind("text");
		expect(texts.size() == 3, "title, detail and time, got " + texts.size());
		Op title = texts.get(0);
		Op detail = texts.get(1);
		Op time = texts.get(2);
		expect(title.x1() == tile.x1() + HudLayout.TILE + HudLayout.GAP, "title starts one gap right of the tile");
		expect(title.y1() == detail.y1() - 9, "the two lines are one font row apart");
		expect(r.textWidth(title.text()) + title.x1() <= box[0] + box[2], "title stays inside the card");
		// A line wider than its column is no longer cut with dots: it slides inside a clip
		// that stops where the column stops. This detail is 120px against a 90px column once
		// the elapsed clock has taken its room, so it cannot fit, and the time pinned to the
		// right pad is exactly what its clip must not cross.
		Op detailClip = r.clipAround(detail);
		expect(r.textWidth(detail.text()) > box[2] - HudLayout.PAD * 2 - HudLayout.TILE - HudLayout.GAP,
			"this detail really does overflow its column, got " + r.textWidth(detail.text()) + "px");
		expect(detailClip != null, "a detail too wide for its column is drawn inside a clip");
		if (detailClip != null) {
			expect(detailClip.x1() == title.x1(), "the detail slides inside the same column as the title");
			expect(detailClip.x2() <= time.x1(), "its clip ends before the time, so they never overlap");
			expect(detailClip.y1() == detail.y1() - 1 && detailClip.y2() >= detail.y1() + 9,
				"the clip covers the whole line, shadow included, and cuts only sideways");
		}
		expect(time.text().equals("0:17"), "the time line is the elapsed clock, got: " + time.text());
		expect(time.x1() + r.textWidth(time.text()) == box[0] + box[2] - HudLayout.PAD,
			"time is pinned to the right pad");
		expect(detail.y1() == title.y1() + 9, "detail follows title");

		// --- the bar: the only wide two-pixel fills in the card ---
		List<Op> bar = bar(r);
		expect(bar.size() == 2, "a playing track draws a track and a fill, got " + bar.size());
		Op back = bar.get(0);
		Op fill = bar.get(1);
		expect(back.y1() >= tile.y1() + HudLayout.TILE, "the bar is below the tile");
		expect(fill.x1() == back.x1() && fill.x2() <= back.x2(), "the filled length is inside the bar");
		double fraction = (fill.x2() - fill.x1()) / (double) (back.x2() - back.x1());
		expect(Math.abs(fraction - 17.0 / 158.4) < 0.02, "the bar matches the position, got " + fraction);

		// --- the control row: below the bar, inside the card, and not touching each other ---
		List<HudLayout.Rect> buttons = f.buttons();
		HudLayout.Rect first = buttons.get(0);
		HudLayout.Rect last = buttons.get(buttons.size() - 1);
		expect(first.y() > back.y1(), "the control row is under the progress bar, not over it");
		expect(first.y() + HudLayout.BTN_H + HudLayout.PAD == box[1] + box[3],
			"the card is exactly tall enough for the row, got " + box[3]);
		expect(first.x() >= box[0] + HudLayout.PAD && last.x() + HudLayout.BTN_W <= box[0] + box[2] - HudLayout.PAD,
			"the row stays inside the side padding");
		expect(last.y() < box[1] + box[3], "no button hangs off the bottom of the card");
		boolean disjoint = true;
		for (int i = 0; i < buttons.size(); i++) {
			for (int j = i + 1; j < buttons.size(); j++) {
				if (buttons.get(i).holds(buttons.get(j).x(), buttons.get(j).y())) {
					disjoint = false;
				}
			}

			expect(buttons.get(i).w() == HudLayout.BTN_W && buttons.get(i).h() == HudLayout.BTN_H,
				"button " + i + " is the size it is painted at");
		}

		expect(disjoint, "the hit boxes do not overlap, so one click cannot mean two verbs");

		// --- the row is centred under the card, at both ends of its width range ---
		expect(centred(f), "the control row is centred under the card");
		HudLayout.Frame skinny = frame(playing("Playing", "Ok", "Zed", "", 5.0, 0.0), 640, 360);
		HudLayout.Frame widest = frame(huge, 640, 360);
		expect(skinny.width() == HudLayout.PAD * 2 + HudLayout.TILE + HudLayout.GAP + HudLayout.MIN_CONTENT,
			"a stub title really does give the narrowest card there is, got " + skinny.width());
		expect(widest.width() == HudLayout.PAD * 2 + HudLayout.TILE + HudLayout.GAP + HudLayout.MAX_CONTENT,
			"a long title really does stop at the widest card there is, got " + widest.width());
		expect(centred(skinny), "the narrowest card still centres its row");
		expect(centred(widest), "the widest card still centres its row");
		expect(last.x() + HudLayout.BTN_W - first.x() == HudLayout.rowWidth(),
			"the row is four buttons and three gaps wide, got " + (last.x() + HudLayout.BTN_W - first.x()));

		// --- the click target is the painted button, in both directions ---
		for (int i = 0; i < buttons.size(); i++) {
			HudLayout.Rect rect = buttons.get(i);
			expect(f.buttonAt(rect.x() + rect.w() / 2.0, rect.y() + rect.h() / 2.0) == i,
				"the middle of button " + i + " answers as button " + i);
			expect(f.buttonAt(rect.x() - 1, rect.y() + rect.h() / 2.0) < 0,
				"a pixel left of button " + i + " is not a button");
			expect(f.buttonAt(rect.x() + rect.w(), rect.y() + rect.h() / 2.0) < 0,
				"the right edge of button " + i + " belongs to the gap, not to it");
		}

		expect(f.buttonAt(box[0] + 2, box[1] + 2) < 0, "the corner of the card is not a button");
		expect(f.buttonAt(f.x() + f.width() / 2.0, f.y() + HudLayout.PAD + HudLayout.TILE / 2.0) < 0,
			"clicking the artwork does not skip the track");

		// --- inert chrome: while the game owns the mouse the buttons are marks, not targets ---
		for (int i = 0; i < buttons.size(); i++) {
			expect(face(r, buttons.get(i)) == null, "an inert card paints no face for button " + i);
		}

		expect(r.ops.size() < draw(live, true, accent, 0.0, 640, 360, clickable).ops.size(),
			"a clickable card adds chrome, nothing else can remove it");

		Recorder c = draw(live, true, accent, 0.0, 640, 360, clickable);
		for (int i = 0; i < buttons.size(); i++) {
			expect(face(c, buttons.get(i)) != null, "button " + i + " is painted exactly where it can be clicked");
		}

		expect(bounds(c)[2] == box[2] && bounds(c)[3] == box[3] && c.kind("text").size() == texts.size(),
			"the controls change the paint, not the shape of the card");
		expect(c.kind("art").get(0).x1() == tile.x1() && c.kind("art").get(0).y1() == tile.y1(),
			"the artwork does not move when the controls light up");

		// --- hover lights one button, and only that one ---
		HudLayout.Input hover2 = new HudLayout.Input("", 2, -1, true, false);
		Recorder h = draw(live, true, accent, 0.0, 640, 360, hover2);
		int lit = 0;
		for (HudLayout.Rect rect : buttons) {
			Op painted = face(h, rect);
			if (painted != null && alphaOf(painted.colour()) > 60) {
				lit++;
			}
		}

		expect(lit == 1, "hover lights exactly one button face, got " + lit);
		expect(alphaOf(face(h, buttons.get(2)).colour()) > 60, "and it is the hovered one");
		expect(alphaOf(face(h, buttons.get(3)).colour()) < 60, "its neighbour stays dim");
		expect(alphaOf(face(c, buttons.get(0)).colour()) < 60, "with no mouse over it, nothing is lit");

		// --- a press stays lit even after the pointer moves off ---
		HudLayout.Input press3 = new HudLayout.Input("", 1, 3, true, false);
		Recorder p = draw(live, true, accent, 0.0, 640, 360, press3);
		int pressed2 = 0;
		for (HudLayout.Rect rect : buttons) {
			Op painted = face(p, rect);
			if (painted != null && alphaOf(painted.colour()) > 60) {
				pressed2++;
			}
		}

		expect(pressed2 == 2, "a held press keeps its button lit next to the hovered one, got " + pressed2);
		expect(alphaOf(face(p, buttons.get(3)).colour()) > 60, "the pressed button is the lit one, not the hovered");

		// --- the verbs behind the buttons ---
		expect(HudLayout.command(HudLayout.PREV, "").equals("prev"), "the left button is previous");
		expect(HudLayout.command(HudLayout.PLAY, "").equals("toggle"), "the middle button is play or pause");
		expect(HudLayout.command(HudLayout.NEXT, "").equals("next"), "the right button is next");
		expect(HudLayout.command(HudLayout.REPEAT, "").equals("repeat:Track"),
			"unknown repeat asks for the first mode, got " + HudLayout.command(HudLayout.REPEAT, ""));
		expect(HudLayout.command(99, "") == null, "an index past the row means nothing");
		// The cycle is only observable through the verb the button sends, which is how the
		// bridge sees it too, so that is what gets gated rather than a helper.
		expect(HudLayout.command(HudLayout.REPEAT, "Track").equals("repeat:Album")
			&& HudLayout.command(HudLayout.REPEAT, "Album").equals("repeat:None")
			&& HudLayout.command(HudLayout.REPEAT, "None").equals("repeat:Track"),
			"repeat walks track, album, none and back to track");
		expect(HudLayout.command(HudLayout.REPEAT, "Album").equals("repeat:None"),
			"the button asks for the mode after the one it shows");

		// --- the repeat dots are the only place the unread state can be shown ---
		Recorder off = draw(live, true, accent, 0.0, 640, 360, new HudLayout.Input("None", -1, -1, true, false));
		Recorder one = draw(live, true, accent, 0.0, 640, 360, new HudLayout.Input("Track", -1, -1, true, false));
		Recorder two = draw(live, true, accent, 0.0, 640, 360, new HudLayout.Input("Album", -1, -1, true, false));
		expect(one.ops.size() == off.ops.size() + 1, "one track repeating adds one dot, got "
			+ (one.ops.size() - off.ops.size()));
		expect(two.ops.size() == off.ops.size() + 2, "the whole queue adds a second dot, got "
			+ (two.ops.size() - off.ops.size()));
		expect(bounds(one)[2] == box[2] && bounds(two)[3] == box[3], "the dots do not resize the card");
		HudLayout.Rect repeatBox = buttons.get(HudLayout.REPEAT);
		boolean inside = true;
		for (Op op : two.kind("fill")) {
			if (op.y1() >= repeatBox.y() && op.y2() <= repeatBox.y() + repeatBox.h()
				&& op.x2() - op.x1() == 2 && op.y2() - op.y1() == 2) {
				inside = op.x1() >= repeatBox.x() && op.x2() <= repeatBox.x() + repeatBox.w();
			}
		}

		expect(inside, "a dot lives inside the repeat button, not in the gap beside it");

		// --- the play mark flips with the session, in the button and in the tile ---
		NowPlaying paused = playing("Paused", "Trust", "Brent Faiyaz", "Björk", 17.0, 158.4);
		Recorder idle = draw(paused, true, accent, 0.0, 640, 360, inert);
		// The play button's own glyph is two bars while playing and a seven-column triangle
		// while paused, so the op count is allowed to move. What is not allowed is the card
		// itself moving.
		expect(idle.kind("text").size() == texts.size(), "pause keeps the same lines");
		expect(idle.kind("art").get(0).equals(tile), "pause does not move the artwork");
		expect(bounds(idle)[2] == box[2], "same width while paused");
		int livePanel = panel(r).colour();
		int idlePanel = panel(idle).colour();
		expect(livePanel != idlePanel, "the panel is a different colour while paused");
		expect(alphaOf(idlePanel) < alphaOf(livePanel), "the paused panel is more see-through, got "
			+ alphaOf(idlePanel) + " against " + alphaOf(livePanel));
		expect((idlePanel & 0x00FFFFFF) == (livePanel & 0x00FFFFFF), "only the alpha moves, the ink stays");
		expect(panel(idle).x1() == panel(r).x1() && panel(idle).y2() == panel(r).y2(),
			"the panel occupies the same box while paused");
		expect(idle.kind("text").get(0).colour() != title.colour(), "the title dims while paused");
		HudLayout.Rect playBox = buttons.get(HudLayout.PLAY);
		Recorder idleLive = draw(paused, true, accent, 0.0, 640, 360, clickable);
		Op pausedFace = face(idleLive, playBox);
		expect(pausedFace != null && alphaOf(pausedFace.colour()) == alphaOf(face(c, playBox).colour()),
			"the button chrome is the same lit shape paused or playing; only the ink dims");
		expect(redOf(face(idleLive, playBox).colour()) == redOf(face(c, playBox).colour()),
			"a paused card keeps the accent hue in its buttons");

		// --- the mark in the middle button is the state, not the promise ---
		expect(glyph(r, playBox).size() == 2, "a playing card shows two pause bars even when nothing can be clicked");
		expect(glyph(c, playBox).size() == 2, "and the same two when the card is live");
		expect(glyph(idleLive, playBox).size() > 2, "paused, the middle button is a triangle, not a bar pair");
		expect(glyph(c, buttons.get(HudLayout.PREV)).size() == 8, "previous is a triangle plus a bar");
		expect(glyph(c, buttons.get(HudLayout.NEXT)).size() == 8, "next is the mirror of previous");
		expect(!glyph(c, buttons.get(HudLayout.PREV)).equals(glyph(c, buttons.get(HudLayout.NEXT))),
			"the two skip buttons are not the same mark");

		// A paused card shows a bare right-pointing triangle, which is what the next button
		// is made of. The bar beside a skip glyph is the only thing telling them apart, and
		// at one pixel it disappears into the triangle's own full-height column.
		expect(barWidth(glyph(c, buttons.get(HudLayout.PREV)), true) == 2,
			"the previous bar is two pixels wide");
		expect(barWidth(glyph(c, buttons.get(HudLayout.NEXT)), false) == 2, "the next bar is two pixels wide");
		expect(barWidth(glyph(idleLive, playBox), true) == 1, "and the play triangle has no bar to be read as");
		expect(!glyph(idleLive, playBox).equals(glyph(idleLive, buttons.get(HudLayout.NEXT))),
			"paused, the play mark is not the next mark");

		// --- when the card exists at all ---
		expect(HudLayout.visible(live, true, false, false), "a track with the panel on is drawn");
		expect(!HudLayout.visible(live, false, false, false), "the hud switch hides it");
		expect(!HudLayout.visible(paused, true, true, false), "onlyWhenPlaying suppresses the card while paused");
		expect(HudLayout.visible(live, true, true, false), "onlyWhenPlaying still draws while playing");
		expect(HudLayout.visible(NowPlaying.EMPTY, false, true, true), "the editor always has a card to drag");
		expect(!HudLayout.visible(NowPlaying.EMPTY, true, false, false), "an empty session draws nothing");
		expect(!HudLayout.visible(playing("Playing", "", "", "", 0.0, 0.0), true, false, false),
			"a session with no title and no artist is not a card");

		// --- the track-change flash lights the border and nothing else ---
		Recorder lit2 = draw(live, true, accent, 1.0, 640, 360, inert);
		expect(lit2.kind("fill").get(0).colour() != r.kind("fill").get(0).colour(), "the border lights up");
		expect(lit2.kind("text").get(0).colour() == title.colour(), "the text is not part of the flash");
		expect(lit2.ops.size() == r.ops.size(), "the flash adds no geometry");

		// --- the placement outline is drawn outside the card and adds nothing inside ---
		Recorder editing = draw(live, true, accent, 0.0, 640, 360, new HudLayout.Input("", -1, -1, false, true));
		expect(editing.ops.size() > r.ops.size(), "the editor draws an outline around the card");
		expect(bounds(editing)[2] == box[2] && bounds(editing)[3] == box[3], "the outline does not resize the card");
		expect(editing.kind("fill").stream().anyMatch(op -> op.x1() == box[0] - 2),
			"the outline sits outside the border, not on top of it");
		expect(frame(live, 640, 360).buttons().equals(buttons), "the frame is the same in the editor");

		// --- degenerate metadata ---
		NowPlaying noAlbum = playing("Playing", "Solo", "Oda", "", 5.0, 60.0);
		Recorder na = draw(noAlbum, true, accent, 0.0, 640, 360, inert);
		expect(na.kind("text").get(1).text().equals("Oda"), "an empty album leaves no dangling dash");
		expect(na.kind("text").size() == 3, "still three lines with no album");

		// Both lines here are short enough to stand still, so nothing may be clipped: a
		// card that scrolls text that already fits is worse than the dots it replaced.
		expect(na.kind("clip").isEmpty(), "a card whose lines fit opens no clip at all");

		// Nine glyphs plus " - " plus an album: the box used to cut this at the separator and
		// leave " - " hanging. Now the line is drawn whole and slides, so the separator can
		// only ever be the session's own.
		NowPlaying cut = playing("Playing", "Trust", "Faiyaz123", "An Album Name", 5.0, 60.0);
		String wholeDetail = draw(cut, true, accent, 0.0, 640, 360, inert).kind("text").get(1).text();
		expect(wholeDetail.equals("Faiyaz123 - An Album Name"),
			"the box never cuts the detail any more, got: " + wholeDetail);

		// The only cut left is the runaway guard, and a cut is allowed to end only on a whole
		// word: an opening bracket the guard cannot close has to go with what it opened.
		StringBuilder runaway = new StringBuilder();
		for (int glyph = 0; glyph < 195; glyph++) {
			runaway.append('A');
		}

		runaway.append(" (and a tail nobody could read inside one track anyway)");
		NowPlaying giant = playing("Playing", runaway.toString(), "", "", 5.0, 0.0);
		String guarded = draw(giant, true, accent, 0.0, 640, 360, inert).kind("text").get(0).text();
		expect(guarded.length() < runaway.length() && guarded.endsWith("..."),
			"a headline past the guard is cut, got " + guarded.length() + " of " + runaway.length());
		expect(!guarded.contains("("), "and the cut drops the bracket it cannot close, got: " + guarded);

		NowPlaying endless = playing("Playing", "Stream", "Radio", "", 4021.0, 0.0);
		Recorder en = draw(endless, false, accent, 0.0, 640, 360, inert);
		expect(en.kind("text").size() == 2, "no duration means no time on the card");
		expect(bar(en).size() == 1, "an unknown length leaves the bar empty rather than full");

		Recorder hg = draw(huge, true, accent, 0.0, 640, 360, inert);
		int[] hbox = bounds(hg);
		expect(hbox[2] == box[2], "a long title stops at the content ceiling, not past it");
		Op htitle = hg.kind("text").get(0);
		expect(htitle.text().equals("The Life That You Save Is The One You Lose"),
			"a long title reaches the card whole and slides instead of ending in dots, got: " + htitle.text());
		int column = hbox[2] - HudLayout.PAD * 2 - HudLayout.TILE - HudLayout.GAP;
		int overflow = r.textWidth(htitle.text()) - column;
		expect(overflow > 0, "this title really does overflow its column, got " + overflow);
		Op hclip = hg.clipAround(htitle);
		expect(hclip != null, "an overflowing title is drawn inside a clip instead of across the border");
		if (hclip != null) {
			expect(hclip.x1() == htitle.x1() && hclip.x2() == htitle.x1() + column,
				"the clip spans exactly the column the text belongs to");
		}

		// --- the slide itself: one clock for the card, out, rest, back, rest ---
		Op hdetail = hg.kind("text").get(1);
		Op hdetailClip = hg.clipAround(hdetail);
		expect(hdetailClip != null, "this detail overflows its column too, so it is clipped as well");
		int detailOverflow = hdetailClip == null ? 0
			: r.textWidth(hdetail.text()) - (hdetailClip.x2() - hdetailClip.x1());
		double travel = HudLayout.travelSeconds(Math.max(overflow, detailOverflow));
		expect(travel > 0.0 && travel <= 6.0,
			"the card runs a leg of its own, clamped to six seconds, got " + travel);

		Recorder slid = drawAt(huge, true, accent, 0.0, 6, 640, 360, inert, travel / 2.0);
		Op slidTitle = slid.kind("text").get(0);
		Op slidDetail = slid.kind("text").get(1);
		int slidTitleBy = htitle.x1() - slidTitle.x1();
		int slidDetailBy = hdetail.x1() - slidDetail.x1();
		expect(Math.abs(slidTitleBy * 2 - overflow) <= 1, "halfway along, the title has slid half its own "
			+ "overhang, got " + slidTitleBy + " of " + overflow);
		expect(Math.abs(slidDetailBy * 2 - detailOverflow) <= 1, "and so has the detail: " + slidDetailBy
			+ " of " + detailOverflow);

		// The case that actually separates one clock from two: these lines overhang by enough
		// to want different legs, so on clocks of their own the short line would already be
		// sitting at its far end while the long one was still halfway out.
		NowPlaying uneven = playing("Playing", "abcdefghijklmnopqrstuvwx", "Artist Sixteen Chars",
			"Album Sixteen Chars!", 5.0, 60.0);
		Recorder u0 = draw(uneven, true, accent, 0.0, 640, 360, inert);
		Op uTitle = u0.kind("text").get(0);
		Op uDetail = u0.kind("text").get(1);
		Op uTitleClip = u0.clipAround(uTitle);
		Op uDetailClip = u0.clipAround(uDetail);
		expect(uTitleClip != null && uDetailClip != null, "both lines of this card overflow, so both slide");
		int uTitleOver = uTitleClip == null || uDetailClip == null ? 0
			: u0.textWidth(uTitle.text()) - (uTitleClip.x2() - uTitleClip.x1());
		int uDetailOver = uTitleClip == null || uDetailClip == null ? 0
			: u0.textWidth(uDetail.text()) - (uDetailClip.x2() - uDetailClip.x1());
		expect(HudLayout.travelSeconds(uDetailOver) != HudLayout.travelSeconds(uTitleOver),
			"on clocks of their own these two legs would differ: " + HudLayout.travelSeconds(uTitleOver)
				+ "s against " + HudLayout.travelSeconds(uDetailOver) + "s");
		Recorder uh = drawAt(uneven, true, accent, 0.0, 6, 640, 360, inert,
			HudLayout.travelSeconds(Math.max(uTitleOver, uDetailOver)) / 2.0);
		int uTitleBy = uTitle.x1() - uh.kind("text").get(0).x1();
		int uDetailBy = uDetail.x1() - uh.kind("text").get(1).x1();
		expect(Math.abs(uTitleBy * 2 - uTitleOver) <= 1, "halfway out the short line is halfway, not already "
			+ "at its end: " + uTitleBy + " of " + uTitleOver);
		expect(Math.abs(uDetailBy * 2 - uDetailOver) <= 1, "and the long line is halfway along its own: "
			+ uDetailBy + " of " + uDetailOver);
		expect(HudLayout.scrollShift(overflow, 0.0) == 0, "a track always starts read from its first letter");
		boolean backwards = true;
		for (double t = -0.2; t >= -8.0; t -= 0.2) {
			backwards &= HudLayout.scrollPhase(t, travel) == 0.0;
		}

		expect(backwards, "a clock that steps backwards leaves the card at its first letters, not mid-cycle");
		expect(HudLayout.scrollShift(overflow, 1.0) == overflow, "and ends it on its last");
		expect(HudLayout.scrollShift(overflow, 1.7) == overflow && HudLayout.scrollShift(overflow, -2.0) == 0,
			"a phase outside 0..1 cannot pull the line past either end of its column");
		expect(HudLayout.scrollShift(0, 1.0) == 0, "a line that fits never moves");
		expect(HudLayout.scrollShift(-8, 1.0) == 0, "a negative overflow cannot invent a shift");
		boolean bounded = true;
		boolean teleports = false;
		int atTheEnd = 0;
		int atTheStart = 0;
		int previous = 0;
		for (double t = 0.0; t <= 20.0; t += 0.05) {
			double phase = HudLayout.scrollPhase(t, travel);
			if (phase < 0.0 || phase > 1.0) {
				bounded = false;
			}

			int shift = HudLayout.scrollShift(overflow, phase);
			if (shift < 0 || shift > overflow) {
				bounded = false;
			}

			if (Math.abs(shift - previous) > 2) {
				teleports = true;
			}

			previous = shift;
			if (phase == 1.0) {
				atTheEnd++;
			}

			if (phase == 0.0) {
				atTheStart++;
			}
		}

		expect(bounded, "the slide never pulls the line further than its own overhang, so no hole is left");
		expect(!teleports, "the line travels rather than jumping between ends");
		expect(atTheEnd * 0.05 >= 1.0, "it rests on the last letters long enough to read, got "
			+ atTheEnd + " samples");
		expect(atTheStart * 0.05 >= 1.0, "it rests on the first letters too, got " + atTheStart + " samples");

		// Both ends of the leg are clamped: a card that overhangs by one glyph must not spend
		// six seconds creeping, and a headline that overhangs by a paragraph must not blur.
		expect(HudLayout.travelSeconds(0) == 0.0 && HudLayout.travelSeconds(6) == 0.0,
			"an overhang of a glyph or less is not worth sliding for");
		expect(HudLayout.travelSeconds(7) == 1.5, "a hair over the guard still gets the shortest leg");
		expect(HudLayout.travelSeconds(44) == 2.0, "a leg long enough to read is left as measured");
		expect(HudLayout.travelSeconds(4000) == 6.0, "a runaway headline is capped at six seconds out");

		// A line that overhangs by less than a glyph keeps its clip - that is what holds it off
		// the time - but it must stand still rather than creep two pixels.
		NowPlaying tight = playing("Playing", "abcdefghijklmnopqrst", "", "", 5.0, 60.0);
		Recorder still = draw(tight, true, accent, 0.0, 640, 360, inert);
		Op tightTitle = still.kind("text").get(0);
		Op tightClip = still.clipAround(tightTitle);
		int tightOverflow = tightClip == null ? 0
			: still.textWidth(tightTitle.text()) - (tightClip.x2() - tightClip.x1());
		expect(tightClip != null && tightOverflow > 0 && tightOverflow <= 6,
			"a twenty-glyph title overhangs by a few pixels, got " + tightOverflow);
		expect(drawAt(tight, true, accent, 0.0, 6, 640, 360, inert, 3.0).kind("text").get(0).x1()
			== tightTitle.x1(), "and it does not creep sideways to reveal them");
		NowPlaying oneMore = playing("Playing", "abcdefghijklmnopqrstu", "", "", 5.0, 60.0);
		expect(drawAt(oneMore, true, accent, 0.0, 6, 640, 360, inert, 3.0).kind("text").get(0).x1()
			< draw(oneMore, true, accent, 0.0, 640, 360, inert).kind("text").get(0).x1(),
			"one glyph more of the same title is worth sliding for, so the guard is not just a freeze");

		// The shared clock is the reason this needs its own case: the card runs to the detail's
		// leg, so the title is handed a phase well past zero while it is still only two pixels
		// wide of its column. Without the shift guard that tiny line gets tugged sideways by a
		// neighbour it has nothing to do with.
		NowPlaying beside = playing("Playing", "abcdefghijklmnopqrst", "abcdefghijklmnopqrstuvwxyz",
			"abcdefgh", 5.0, 60.0);
		Recorder bBase = drawAt(beside, true, accent, 0.0, 6, 640, 360, inert, 0.0);
		Recorder bMid = drawAt(beside, true, accent, 0.0, 6, 640, 360, inert, 3.0);
		Op bTitle = bBase.kind("text").get(0);
		Op mTitle = bMid.kind("text").get(0);
		Op mClip = bMid.clipAround(mTitle);
		int besideOver = mClip == null ? -1 : bMid.textWidth(mTitle.text()) - (mClip.x2() - mClip.x1());
		expect(besideOver > 0 && besideOver <= 6,
			"this title overhangs by a couple of pixels beside a detail that overhangs by a mile, got "
				+ besideOver);
		expect(mTitle.x1() == bTitle.x1(), "and the long line's clock does not tug the short one sideways");
		expect(bMid.kind("text").get(1).x1() < bBase.kind("text").get(1).x1(),
			"while the long line itself does run, so the guard above is not a freeze on the whole card");
		HudLayout.Frame hf = frame(huge, 640, 360);
		HudLayout.Rect hrepeat = hf.buttons().get(HudLayout.REPEAT);
		expect(hf.buttonAt(hrepeat.x() + hrepeat.w() - 1, hrepeat.y() + hrepeat.h() - 1) == HudLayout.REPEAT,
			"at the widest the card ever gets, the repeat button answers on its own far corner");
		int stride = hf.buttons().get(1).x() - hf.buttons().get(0).x();
		boolean even = true;
		for (int i = 2; i < hf.buttons().size(); i++) {
			even &= hf.buttons().get(i).x() - hf.buttons().get(i - 1).x() == stride;
		}

		expect(even && stride > HudLayout.BTN_W, "the row is evenly spaced, with a gap wide enough to miss");

		// --- placement clamps, in both directions and at every corner ---
		Recorder top = drawAt(live, true, accent, 0.0, 0, 640, 360, inert, 0.0);
		expect(bounds(top)[0] == 0 && bounds(top)[1] == 0, "the corner of the screen is a legal placement");
		Recorder off2 = new Recorder();
		HudLayout.Frame pushed = HudLayout.frame(off2, live, new HudLayout.Options(600, 350, DeckSkin.BUILT_IN), 640, 360);
		HudLayout.draw(off2, pushed, live, inert, accent, true, 0.0, 0.0);
		expect(pushed.x() + pushed.width() <= 640 && pushed.y() + pushed.height() <= 360,
			"a card asked for off-screen is pulled back in");
		expect(pushed.buttons().get(0).x() >= pushed.x(), "and its buttons come back with it");
		expect(off2.ops.size() == r.ops.size(), "clamping does not change what is drawn");
		Recorder tiny = new Recorder();
		HudLayout.Frame narrow = HudLayout.frame(tiny, live, new HudLayout.Options(400, 300, DeckSkin.BUILT_IN), 200, 120);
		HudLayout.draw(tiny, narrow, live, inert, accent, true, 0.0, 0.0);
		expect(narrow.x() >= 0 && narrow.x() + narrow.width() <= 200
				&& narrow.y() >= 0 && narrow.y() + narrow.height() <= 120,
			"a 200x120 window still gets the whole card");
		expect(narrow.buttons().get(HudLayout.REPEAT).x() + HudLayout.BTN_W <= 200,
			"even there the control row is on screen");
		HudLayout.Frame crushed = HudLayout.frame(new Recorder(), live, new HudLayout.Options(400, 300, DeckSkin.BUILT_IN), 100, 60);
		expect(crushed.x() == 0 && crushed.y() == 0, "a window smaller than the card pins it to the origin");
		expect(crushed.width() == box[2] && crushed.height() == box[3],
			"and the card keeps its shape instead of squashing to fit");

		// --- palette ---
		int[] red = new int[HudLayout.TILE * HudLayout.TILE];
		java.util.Arrays.fill(red, 0xFFC02828);
		int redAccent = Palette.accent(red, Palette.GREEN);
		expect(redAccent != Palette.GREEN, "a saturated field yields its own colour");
		expect(redOf(redAccent) > greenOf(redAccent) + 60,
			"the accent keeps the cover's hue instead of averaging to a neutral");
		expect(alphaOf(redAccent) == 0xFF, "an accent is always opaque, got alpha " + alphaOf(redAccent));
		int[] grey = new int[HudLayout.TILE * HudLayout.TILE];
		java.util.Arrays.fill(grey, 0xFF888888);
		expect(Palette.accent(grey, Palette.GREEN) != Palette.GREEN, "a grey field still gives an accent");
		int[] dark = new int[HudLayout.TILE * HudLayout.TILE];
		java.util.Arrays.fill(dark, 0xFF0A0A0A);
		expect(Palette.accent(dark, Palette.GREEN) == Palette.GREEN, "a black field falls back to the brand");
		int[] mixed = new int[HudLayout.TILE * HudLayout.TILE];
		java.util.Arrays.fill(mixed, 0xFF101010);
		mixed[4] = 0xFF303030;
		expect(Palette.accent(mixed, Palette.GREEN) == Palette.GREEN, "one stray pixel is not a palette");

		int[] speck = new int[HudLayout.TILE * HudLayout.TILE];
		java.util.Arrays.fill(speck, 0xFF101010);
		speck[7] = 0xFFC02828;
		expect(Palette.accent(speck, Palette.GREEN) == Palette.GREEN,
			"one saturated speck in a dark cover is not a palette");
		// Both floors of the vote need teeth on both sides, stated as a fraction of the tile
		// so they survive a change of artwork size: a patch under the bar is JPEG noise and
		// must not repaint the card, and a patch over it is the cover's own colour.
		int[] under = new int[HudLayout.TILE * HudLayout.TILE];
		java.util.Arrays.fill(under, 0xFF101010);
		for (int i = 0; i < under.length / 35; i++) {
			under[i] = 0xFFC02828;
		}

		// 65 of 2304 pixels: under the floor once each pixel is weighted by how much colour
		// it carries, over it if every pixel is counted at full strength. That is exactly the
		// line the mid-tone weight draws, so the bracket has to sit on it.
		expect(Palette.accent(under, Palette.GREEN) == Palette.GREEN,
			"a coloured patch under the voting floor is still noise");
		int[] twoTone = new int[HudLayout.TILE * HudLayout.TILE];
		java.util.Arrays.fill(twoTone, 0xFF101010);
		for (int i = 0; i < twoTone.length / 25; i++) {
			twoTone[i] = 0xFFC02828;
		}

		expect(Palette.accent(twoTone, Palette.GREEN) != Palette.GREEN,
			"a cover with a real coloured region is read as coloured");
		// The reason the average was replaced. Three parts red, one part blue: the mean of
		// those pixels is a purple-brown that names neither half, and the card used to paint
		// it. A vote keeps the majority hue whole.
		int[] twoHue = new int[HudLayout.TILE * HudLayout.TILE];
		java.util.Arrays.fill(twoHue, 0xFF101010);
		for (int i = 0; i < twoHue.length / 9; i++) {
			twoHue[i] = 0xFFC02828;
		}

		for (int i = twoHue.length / 9; i < twoHue.length / 9 + twoHue.length / 27; i++) {
			twoHue[i] = 0xFF2828C0;
		}

		int voted = Palette.accent(twoHue, Palette.GREEN);
		expect(redOf(voted) - blueOf(voted) > 100,
			"a two-hue cover is tinted by its dominant hue, not by the muddy mean of both, got "
				+ Integer.toHexString(voted));
		// A dark cover still has a hue, and losing the brightness is not the same as having
		// no colour at all. The old rung required max > 60 and threw these away outright.
		int[] deep = new int[HudLayout.TILE * HudLayout.TILE];
		java.util.Arrays.fill(deep, 0xFF2A0A3A);
		int deepAccent = Palette.accent(deep, Palette.GREEN);
		expect(deepAccent != Palette.GREEN, "a dark cover keeps its own hue instead of falling back");
		expect(redOf(deepAccent) > greenOf(deepAccent) + 60 && blueOf(deepAccent) > greenOf(deepAccent) + 60,
			"the dark hue is lifted to read, not merely averaged, got " + Integer.toHexString(deepAccent));

		// Spotify hands back a 300px thumbnail at one flat ~78% alpha, so the discard
		// threshold has to sit below that and above a watermark's faint edge. Bracket it.
		int[] wash = new int[HudLayout.TILE * HudLayout.TILE];
		java.util.Arrays.fill(wash, 0xC7C02828);
		expect(Palette.accent(wash, Palette.GREEN) != Palette.GREEN,
			"a translucent cover still votes for its own colour");
		int[] ghost = new int[HudLayout.TILE * HudLayout.TILE];
		java.util.Arrays.fill(ghost, 0x40C02828);
		expect(Palette.accent(ghost, Palette.GREEN) == Palette.GREEN,
			"a barely-there field is not an accent");
		expect(Palette.readable(0xFF101010) != 0xFF101010, "a too-dark accent is lifted");
		expect(Palette.scale(0xFF808080, 0.5) == 0xFF404040, "scale halves the channels, keeps the alpha");
		expect(Palette.mix(0xFF112233, 0xFF99AABB, 0.0) == 0xFF112233, "mix at zero is the first colour");
		expect(Palette.mix(0xFF112233, 0xFF99AABB, 1.0) == 0xFF99AABB, "mix at one is the second");
		expect(Palette.withAlpha(0xFF112233, 0x40) == 0x40112233, "withAlpha replaces only the alpha");

		// The answers above cannot tell a vote from an average: a one-colour cover is
		// answered identically by both. This is the shape that makes the two differ, so the
		// shape itself is pinned - a future edit that flattens it back to one bucket has to
		// pass the two-hue cover first.
		String hues = codeOnly(CLIENT_DIR + "Palette.java");
		expect(hues.contains("HUES = 12"), "the hue wheel is cut into twelve slices");
		expect(java.util.regex.Pattern.compile("votes\\[bucket\\] \\+= strength").matcher(hues).find(),
			"each pixel votes for its own bucket rather than one shared sum");
		expect(hues.contains("tint(voteRed[best], voteGreen[best], voteBlue[best], votes[best])"),
			"the accent is the winning bucket's mean, so a minority hue cannot muddy it");
		expect(java.util.regex.Pattern.compile("for \\(int bucket = 1; bucket < HUES; bucket\\+\\+\\)")
			.matcher(hues).find(),
			"the winner is chosen by comparing every bucket rather than the first one found");
		// A wrong corner of the wheel is invisible to a one-colour cover - the bucket still
		// holds only that colour - so the formula itself is pinned, not only its answers.
		expect(hues.contains("if (max == red) {") && hues.contains("} else if (max == green) {"),
			"each third of the wheel is measured from its own corner, which is what keeps red out of the blue buckets");
		expect(hues.contains("+ 2.0") && hues.contains("+ 4.0") && hues.contains("green < blue ? 6.0 : 0.0"),
			"the three segments are offset by a third and two thirds of the wheel, and a negative hue is folded forward");

		// --- theme: where the card's colour comes from ---
		expect(Palette.themed(Palette.THEME_AUTO, 0xFF802020, Palette.GREEN) == 0xFF802020,
			"the automatic theme keeps the colour sampled from the cover");
		expect(Palette.themed(Palette.THEME_AUTO, 0, Palette.GREEN) == Palette.GREEN,
			"with no cover to read, the automatic theme falls back to the brand");
		expect(Palette.themed(Palette.THEME_GREEN, 0xFF802020, Palette.GREY) == Palette.GREEN,
			"the green theme ignores the cover and the fallback");
		expect(Palette.themed(Palette.THEME_WHITE, 0xFF802020, Palette.GREY) == Palette.WHITE,
			"the white theme ignores the cover and the fallback");
		expect(Palette.themed("nonsense", 0xFF802020, Palette.GREY) == 0xFF802020,
			"an unrecognised theme behaves as automatic rather than blanking the card");

		// --- the screens: geometry cannot see these, but the game can crash on them ---
		// The game extracts a screen's background before it calls the override, so a screen
		// that extracts one itself paints it twice in a frame - the same class of bug as the
		// second blur that took the game down on the build this mod shipped from.
		for (String screen : new String[] {"LayoutScreen.java", "SettingsScreen.java", "KeyBindScreen.java"}) {
			String code = codeOnly(CLIENT_DIR + screen);
			expect(!code.contains("extractBlurredBackground(") && !code.contains("extractBackground(")
				&& !code.contains("extractMenuBackground("),
				screen + " asks for no background of its own, which is what took the game down last build");
			expect(code.contains("super.extractRenderState("), screen + " still draws the widgets it adds");
		}

		// The editor owns the mouse for the drag, so it has to hand back every click the
		// card did not take - the press happens in mouseClicked, not in the widget itself.
		String editor = codeOnly(CLIENT_DIR + "LayoutScreen.java");
		expect(editor.contains("return super.mouseClicked(event, doubleClick);"),
			"a click the card refused still reaches the widgets under it");

		// --- reaching these screens: Mod Menu opens the settings, the settings open the keys ---
		// Whitespace is removed because the manifest is pretty-printed and an entrypoint
		// spread over four lines would otherwise never match a single-string check.
		String flat = read("src/main/resources/fabric.mod.json").replaceAll("\\s", "");
		expect(flat.contains("\"name\":\"MineTunes\""), "the mod list shows MineTunes");
		expect(JsonLine.parse(read(CLIENT_LANG)).string("musicdeck.settings.title", "").equals("MineTunes")
			&& JsonLine.parse(read(CLIENT_LANG)).string("key.category.musicdeck.main", "").equals("MineTunes"),
			"the settings and Controls category show MineTunes without changing their saved identifiers");
		expect(flat.contains("\"modmenu\":[\"com.musicdeck.client.MusicDeckModMenu\"]"),
			"the manifest registers a Mod Menu entrypoint, or the settings screen has no door");
		int depends = flat.indexOf("\"depends\":{");
		int suggests = flat.indexOf("\"suggests\"");
		expect(depends >= 0 && suggests > depends, "the manifest has both a required block and an optional one");
		expect(!flat.substring(depends, suggests).contains("modmenu"),
			"Mod Menu is not a hard dependency: without it the mod must still load, not refuse the whole game");
		expect(flat.contains("\"suggests\":{\"modmenu\":\"*\"}"),
			"and it is named as optional, so a player can tell the button is missing a mod rather than a feature");

		// --- the picture the mod shows about itself ---
		// The path is read out of the manifest instead of being repeated here, so moving the
		// asset without editing the manifest fails rather than passing on a lucky match.
		java.util.regex.Matcher declaredIcon = java.util.regex.Pattern
			.compile("\"icon\"\\s*:\\s*\"([^\"]+)\"")
			.matcher(read("src/main/resources/fabric.mod.json"));
		boolean hasIcon = declaredIcon.find();
		expect(hasIcon, "the manifest names an icon, or every mod list and page shows the default placeholder");
		String iconPath = hasIcon ? declaredIcon.group(1) : "";
		expect(iconPath.startsWith("assets/musicdeck/"),
			"the icon sits under this mod's own namespace, so a rename cannot leave it pointing at a dead folder");
		java.nio.file.Path iconFile = java.nio.file.Path.of("src/main/resources", iconPath);
		boolean onDisk = !iconPath.isEmpty() && java.nio.file.Files.exists(iconFile);
		expect(onDisk, "the icon the manifest names is actually there, got " + iconPath);
		byte[] png = new byte[0];
		try {
			if (onDisk) {
				png = java.nio.file.Files.readAllBytes(iconFile);
			}
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Cannot read " + iconFile, e);
		}
		expect(png.length > 24 && (png[1] & 0xFF) == 'P' && (png[2] & 0xFF) == 'N'
			&& (png[3] & 0xFF) == 'G' && png[12] == 'I' && png[13] == 'H' && png[14] == 'D' && png[15] == 'R',
			"the icon is a real PNG with a header, not a renamed file of another kind");
		int iconWide = png.length > 24 ? (png[16] & 0xFF) << 24 | (png[17] & 0xFF) << 16
			| (png[18] & 0xFF) << 8 | png[19] & 0xFF : -1;
		int iconTall = png.length > 24 ? (png[20] & 0xFF) << 24 | (png[21] & 0xFF) << 16
			| (png[22] & 0xFF) << 8 | png[23] & 0xFF : -1;
		expect(iconWide == 128 && iconTall == 128,
			"the bundled icon is the compact 128px square, got " + iconWide + "x" + iconTall);
		expect(png.length > 0 && png.length <= 64 * 1024,
			"the icon is small enough to sit in every download of the jar, got " + png.length + " bytes");
		expect(java.nio.file.Files.exists(java.nio.file.Path.of("publish/minetunes-icon.png"))
			&& java.nio.file.Files.exists(java.nio.file.Path.of("publish/minetunes-banner.png")),
			"the full-size page art is kept with the project, so the release page can be rebuilt without regenerating it");

		String gradle = read("build.gradle");
		expect(gradle.contains("compileOnly \"maven.modrinth:modmenu:"),
			"Mod Menu is compile-only, so its classes stay out of the jar while the settings screen still builds");
		expect(!gradle.contains("modImplementation \"maven.modrinth:modmenu")
			&& !gradle.contains("implementation \"maven.modrinth:modmenu"),
			"Mod Menu is not bundled: shipping another mod's jar in yours is a licence problem, not an integration");

		String menu = codeOnly(CLIENT_DIR + "MusicDeckModMenu.java");
		expect(menu.contains("implements ModMenuApi"),
			"the entrypoint class implements the interface Mod Menu looks for");
		expect(menu.contains("getModConfigScreenFactory"),
			"and answers the one method that hands over a config screen");
		expect(menu.contains(", parent)"),
			"Mod Menu's own screen is passed down as the parent, so Done returns to the mod list");
		expect(menu.contains("client == null") && menu.contains("MediaConfig.load()"),
			"the screen still opens if Mod Menu asks before the client initializer has run");

		String settings = codeOnly(CLIENT_DIR + "SettingsScreen.java");
		expect(settings.contains("SettingsScreen(MediaConfig config, Screen back)"),
			"the settings screen can take a parent, which is how anything that opens it gets a way back");
		expect(settings.contains("Platform.open(this.minecraft, back)"),
			"Done returns to whatever opened the screen instead of dropping the player into the world");
		expect(settings.contains("new KeyBindScreen(this)"),
			"the keybind list is reachable from the settings, which is the point of having a settings screen");
		expect(settings.contains("new LayoutScreen(config, this)"),
			"and the placement editor returns here rather than closing to nothing");

		// --- the mod's own rebinding screen ---
		String binds = codeOnly(CLIENT_DIR + "KeyBindScreen.java");
		expect(binds.contains("MusicDeckClient.instance().keyBinds()"),
			"the rows come from this mod's own list, not from the game's whole binding array");
		expect(count(binds, "mapping.setKey(") == 2,
			"a click writes the key and a right click resets it, so setKey appears twice, got "
				+ count(binds, "mapping.setKey("));
		expect(count(binds, "options.save()") == 2,
			"every write is followed by a save, or the binding is lost the next time the game starts");
		expect(binds.contains("mapping.getDefaultKey()"),
			"right click goes back to the shipped default rather than to unbound");
		expect(binds.contains("Platform.KEYS.getOrCreate(")
			&& binds.contains("InputConstants.Type.MOUSE.getOrCreate("),
			"both a key and a mouse button can answer, because the game lets you bind either");
		expect(java.util.regex.Pattern.compile("System\\.currentTimeMillis\\(\\) - armedAt > GRACE_MILLIS")
			.matcher(binds).find(),
			"the click that armed a row is not itself taken as the new binding");
		expect(binds.contains("event.key() != UNKNOWN_KEY"),
			"a key event that carries no key leaves the row armed instead of binding -1");
		expect(binds.contains("return super.mouseClicked(event, doubleClick);"),
			"a press outside a row still reaches the Back button under it");
		expect(!binds.contains("rebuildWidgets("),
			"no widget list is rebuilt while a click is still being sent, which is a ConcurrentModificationException");
		expect(binds.contains(".setMessage(valueText("),
			"an armed row repaints from the render pass, which is the safe way to show it is listening");
		expect(java.util.regex.Pattern.compile("isEscape\\(\\)\\)\\s*\\{\\s*capturing = -1;")
			.matcher(binds).find(),
			"ESC gives up the capture rather than closing the screen on a half-finished rebinding");
		expect(binds.contains("minecraft.options.keyMappings") && binds.contains("(also on "),
			"a key that already belongs to something else says so, because two bindings on one key is a silent loss");
		expect(java.util.regex.Pattern.compile("isPauseScreen\\(\\)[\\s\\S]{0,60}?return false;")
			.matcher(binds).find(),
			"a world keeps running while its music is being rebound");

		// Every binding the mod registers has to be on that screen: one that is not is a
		// key the player can never change, and the array is easy to forget a row in.
		String client = codeOnly(CLIENT_DIR + "MusicDeckClient.java");
		int listAt = client.indexOf("public KeyMapping[] keyBinds()");
		int listEnd = listAt < 0 ? -1 : client.indexOf("}", listAt);
		String listing = listAt < 0 || listEnd < 0 ? "" : client.substring(listAt, listEnd);
		expect(!listing.isEmpty(), "the client exposes the mod's own bindings as a list");
		java.util.regex.Matcher registered = java.util.regex.Pattern
			.compile("(\\w+) = KeyMappingHelper\\.registerKeyMapping\\(new KeyMapping\\(\\s*\"(key\\.[^\"]+)\"")
			.matcher(client);
		int bound = 0;
		while (registered.find()) {
			bound++;
			expect(java.util.regex.Pattern.compile("\\b" + registered.group(1) + "\\b")
				.matcher(listing).find(),
				registered.group(2) + " is registered but not on the keybind screen, so it stays stuck on its default");
		}

		expect(bound >= 6, "the registration scan found the keys it should, got " + bound);
		expect(count(listing, ",") + 1 == bound,
			"the screen has one row per registered binding, got " + (count(listing, ",") + 1)
				+ " rows for " + bound + " keys");
		expect(client.contains("new SettingsScreen(config, Platform.screen(minecraft))"),
			"the settings key opens on top of whatever is already open, so Done goes back there");

		// --- surviving the rename: the old file and the old theme name ---
		String config = codeOnly(CLIENT_DIR + "MediaConfig.java");
		expect(config.contains("Files.exists(file()) ? file() : legacyFile()"),
			"a settings file written under the old name is still read, or the rename throws away a placed panel");
		expect(config.contains("resolve(\"spotify.properties\")")
			&& config.contains("\"spotify\".equals(value)"),
			"the old file name and the old theme value are both understood as the settings they became");
		expect(count(config, "resolve(\"musicdeck.properties\")") == 1,
			"only the new file is ever written, got " + count(config, "resolve(\"musicdeck.properties\")")
				+ " writers");

		// The rename was a sweep over every string in the tree, and one of the things it
		// swept was the literal this test compares against: a brand check that can never be
		// true prints no warning and shows a grey dot forever, so it belongs here.
		String session = codeOnly(CLIENT_DIR + "NowPlaying.java");
		expect(session.contains("contains(\"spotify\")"),
			"the session-owner test asks Windows for the player's real name, not for a theme constant");
		String overlay = codeOnly(CLIENT_DIR + "NowPlayingHud.java");
		expect(overlay.contains("playing.spotify()"),
			"the card only paints the brand colour when the session really belongs to that player");
		expect(read("src/main/java/com/musicdeck/MusicDeck.java").contains("MOD_ID = \"musicdeck\""),
			"the mod id, and therefore every lang key and asset path, is the new name");
		expect(!flat.contains("\"id\":\"spotifybridge"), "and the manifest agrees");

		// Cutting a line to the box is the behaviour the player swapped for a slide, so it
		// must not creep back in through some helper that looked handy.
		String layout = codeOnly(CLIENT_DIR + "HudLayout.java");
		expect(!layout.contains("truncate("), "no line is cut to fit the box any more, it slides");
		expect(layout.contains("canvas.clip(") && layout.contains("canvas.unclip()"),
			"the slide is bounded by a clip rather than painted over the panel border");

		// "Is the editor open?" decides whether the buttons answer, whether the outline draws
		// and whether a placeholder track stands in. It has to be asked of the game, never
		// remembered: setScreen calls the no-op Screen.removed(), so a flag cleared in
		// onClose() survives the editor and leaves the card inert for the rest of the session.
		String hud = codeOnly(CLIENT_DIR + "NowPlayingHud.java");
		expect(hud.contains("Platform.screen(Minecraft.getInstance()) instanceof LayoutScreen"),
			"the card asks the game whether the editor is open instead of remembering it");
		expect(!hud.contains("setLayout(") && !hud.contains("boolean layout"),
			"no sticky editing flag can outlive the editor and kill the transport buttons");
		expect(editor.contains("public void removed()"),
			"the editor saves on the way out through the hook setScreen actually calls");

		// The open screen and the keyboard type moved between 26.x targets, so both live behind
		// Platform. Each target's copy has to read the live game and name the constant its own
		// version actually has: a shim that cached the screen would bring back the stuck editor
		// this whole section exists to prevent, on one target only, where no other gate looks.
		for (String target : new String[] {"26.1.2", "26.2", "26.3"}) {
			String shim = codeOnly("src/client/versions/" + target + "/java/com/musicdeck/client/Platform.java");
			boolean readsLive = target.equals("26.1.2")
				? shim.contains("return minecraft.screen;")
				: shim.contains("return minecraft.gui.screen();");
			expect(readsLive, target + "'s shim asks the game for the open screen instead of remembering one");
			boolean opens = target.equals("26.1.2")
				? shim.contains("minecraft.setScreen(screen);")
				: shim.contains("minecraft.gui.setScreen(screen);");
			expect(opens, target + "'s shim opens screens through the game's own setter");
			String key = target.equals("26.3") ? "Type.KEYBOARD" : "Type.KEYSYM";
			expect(shim.contains(key), target + "'s shim names the keyboard constant its own version has");
		}

		// The slide only exists if the caller hands HudLayout.draw a clock that advances with
		// the track. Every geometry gate still passes against a frozen one, and a hard-coded
		// zero is exactly what a half-wired call site looks like from the outside.
		expect(java.util.regex.Pattern.compile("HudLayout\\.draw\\([^;]*changedAt[^;]*\\)")
			.matcher(hud).find(),
			"the card is drawn with the elapsed track clock, so the slide actually moves");

		// The recorder only records the clip it is asked for, so a canvas that accepts the call
		// and does nothing still passes every geometry gate - while the game paints the sliding
		// glyphs out across the panel border.
		expect(hud.contains("graphics.enableScissor(") && hud.contains("graphics.disableScissor()"),
			"the clip the layout asks for is the game's scissor, not a no-op");

		// --- the artwork tile: PowerShell writes it, Java reads it, and nobody agrees out loud ---
		// The two halves of this pipeline live in different languages and never talk, so the
		// size, the retry timing and the failure behaviour are all decided twice. Every gate
		// below is a cross-file agreement that geometry cannot see and the game only shows as
		// a missing cover.
		String bridge = scriptOnly("src/main/resources/musicdeck/smtc-bridge.ps1");
		java.util.regex.Matcher tileAt = java.util.regex.Pattern
			.compile("\\$script:ArtSize = (\\d+)").matcher(bridge);
		expect(tileAt.find(), "the helper declares the tile size it writes");
		expect(Integer.parseInt(tileAt.group(1)) == HudLayout.TILE,
			"the helper writes the tile at exactly the edge the card blits, got " + tileAt.group(1)
				+ " against " + HudLayout.TILE);

		java.util.regex.Matcher waits = java.util.regex.Pattern
			.compile("\\$script:ArtBackoff = @\\(([^)]*)\\)").matcher(bridge);
		expect(waits.find(), "the helper has a retry table rather than one flat wait");
		String[] parts = waits.group(1).split(",");
		double[] backoff = new double[parts.length];
		for (int i = 0; i < parts.length; i++) {
			backoff[i] = Double.parseDouble(parts[i].trim());
		}

		expect(backoff.length > 1, "a table with one entry is just the old five-second wait again");
		expect(backoff[0] == 0.0, "the first try of a new track is immediate, got " + backoff[0]);
		expect(backoff[1] <= 1.0, "a failed fetch comes back within a second, got " + backoff[1]
			+ " - Spotify publishes the title before the thumbnail, so the first try of nearly every"
			+ " song is expected to fail and that gap is the whole of the missing-cover complaint");
		expect(backoff[backoff.length - 1] <= 8.0,
			"the table still settles down for tracks that have no art, got " + backoff[backoff.length - 1]);

		int at = bridge.indexOf("function Update-Art");
		int after = bridge.indexOf("\nfunction ", at + 1);
		String update = at < 0 ? "" : bridge.substring(at, after < 0 ? bridge.length() : after);
		expect(!update.isEmpty(), "the helper defines Update-Art, the only thing that can write a tile");
		expect(update.contains("$script:ArtRetry = 0"),
			"a track change resets the retry counter, or the second song inherits the first one's wait");
		expect(update.contains("$script:ArtMissing = $true"),
			"a track change forgets the previous tile, so the new cover is fetched not reused");
		expect(update.contains("$script:ArtTriedAt = [DateTime]::MinValue"),
			"a track change forgets when the last attempt happened too, or a new song waits out the"
				+ " backoff the old one earned");
		expect(java.util.regex.Pattern.compile("ArtBackoff\\[.*ArtRetry")
			.matcher(update).find(),
			"the wait is read from that table, not hard-coded in the fetch");
		expect(count(bridge, "Remove-Art") == 2,
			"the tile is cleared in exactly one place, the fetch failure - a second caller is the"
				+ " stutter branch that used to turn one bad poll into a missing cover, got "
				+ count(bridge, "Remove-Art"));

		// An unbounded Wait here parks the poll loop, and a loop that is not polling is a card
		// that froze rather than a card that is short a picture.
		expect(bridge.contains("if (-not $task.Wait($TimeoutMs))"),
			"the async await reports a timeout instead of waiting forever");
		for (String awaited : new String[] {"TryGetMediaPropertiesAsync", "OpenReadAsync"}) {
			java.util.regex.Matcher deadline = java.util.regex.Pattern
				.compile(awaited + "[^\\n]*?\\)\\s+(\\d{3,6})\\s*$", java.util.regex.Pattern.MULTILINE)
				.matcher(bridge);
			expect(deadline.find(), awaited + " is awaited with a deadline, not an open-ended wait");
			int millis = Integer.parseInt(deadline.group(1));
			expect(millis >= 1000 && millis <= 8000,
				awaited + " gets " + millis + "ms - long enough for a slow disk, short enough to poll again");
		}

		String art = codeOnly(CLIENT_DIR + "AlbumArt.java");
		expect(java.util.regex.Pattern.compile("getWidth\\(\\) != HudLayout\\.TILE[\\s\\S]{0,160}?drop\\(\\);")
			.matcher(art).find(),
			"refusing a wrong-sized tile forgets the last good one, or the old cover keeps painting as the new track's");
		expect(java.util.regex.Pattern.compile("private void drop\\(\\)[\\s\\S]{0,200}?accent = 0;")
			.matcher(art).find(),
			"a card with no tile is tinted by the player colour, not by the album that came before it");

		// --- every word the mod shows a player has to be in the language file ---
		String lang = read(CLIENT_LANG);
		int named = 0;
		java.io.File[] sources = new java.io.File(CLIENT_DIR).listFiles();
		expect(sources != null && sources.length > 8, "the client sources are on disk to scan");
		for (java.io.File file : sources == null ? new java.io.File[0] : sources) {
			for (String key : shownKeys(CLIENT_DIR + file.getName())) {
				named++;
				expect(lang.contains("\"" + key + "\":"),
					file.getName() + " shows " + key + ", which the language file defines");
			}
		}

		// A scan that matched nothing would pass every gate above, so prove it can count.
		expect(named > 12, "the language scan found the keys it should, got " + named);
		expect(lang.contains("\"key.category.musicdeck.main\":"),
			"the Controls section has a heading, which the game builds from the category id");
		for (String value : new String[] {"musicdeck.art.smooth", "musicdeck.art.pixel",
			"musicdeck.theme.auto", "musicdeck.theme.green", "musicdeck.theme.white"}) {
			expect(lang.contains("\"" + value + "\":"),
				value + " is spelled out, since the source only builds it by concatenation");
		}

		// --- decks: a look somebody else posted is data, and data gets clamped ---
		NowPlaying deckTrack = playing("Playing", "Short", "Ab", "Cd", 1.0, 100.0);
		DeckSkin shipped = DeckSkin.BUILT_IN;
		expect(shipped.tile() == HudLayout.TILE && shipped.pad() == HudLayout.PAD && shipped.gap() == HudLayout.GAP
			&& shipped.minWidth() == HudLayout.MIN_CONTENT && shipped.maxWidth() == HudLayout.MAX_CONTENT
			&& alphaOf(shipped.panel()) == 0xD8,
			"the built-in deck holds the shipped card's own numbers, so a missing or unreadable file draws "
			+ "what has always drawn");
		expect(shipped.detail() && shipped.time() && shipped.progress() && shipped.buttons() && shipped.border(),
			"the built-in deck has every part switched on");
		expect(!shipped.pinsAccent(), "the built-in deck still takes its colour from the cover");

		expect(gateDeck("{\"tile\":9999}").tile() == DeckSkin.MAX_TILE,
			"a tile asked for bigger than the mod will draw is clamped on the way in, not on the way out");
		expect(gateDeck("{\"tile\":-20}").tile() == 0, "a negative tile is no tile, not a crash");
		expect(gateDeck("{\"tile\":\"large\"}").tile() == HudLayout.TILE,
			"a number spelled as a word costs its author the default, nothing else");
		expect(gateDeck("{\"pad\":99}").pad() == 10 && gateDeck("{\"pad\":1}").pad() == 2,
			"the padding has a floor and a ceiling, so a deck cannot eat its own text or spill off the card");
		expect(gateDeck("{\"gap\":0}").gap() == 2 && gateDeck("{\"gap\":50}").gap() == 12,
			"the gap between sleeve and words stays inside the readable band");
		expect(gateDeck("{\"minWidth\":4}").minWidth() == 20
			&& gateDeck("{\"maxWidth\":9999}").maxWidth() == DeckSkin.MAX_WIDTH,
			"neither end of the width band can be asked past the card the game can hold");
		expect(DeckSkin.MAX_WIDTH <= 320,
			"the ceiling the parser enforces is at most half a 640-wide window, got " + DeckSkin.MAX_WIDTH
				+ ", because a ceiling is only worth what the window underneath it can hold");
		DeckSkin flipped = gateDeck("{\"minWidth\":200,\"maxWidth\":40}");
		expect(flipped.maxWidth() >= flipped.minWidth(),
			"a deck that names a maximum below its minimum still gets a width band rather than a negative column");
		expect(alphaOf(gateDeck("{\"opacity\":0}").panel()) == 38,
			"a fully transparent deck is clamped to 15%, or the words end up painted on nothing");
		expect(alphaOf(gateDeck("{\"opacity\":9}").panel()) == 255, "an opacity above one is solid, not a new number");
		expect(gateDeck("{\"not_a_knob\":7}").tile() == HudLayout.TILE,
			"a key this build does not know is ignored, so a deck written for a newer mod still draws");
		expect(gateDeck("{\"layout\":\"stack\"}").stacked(), "a deck can put the sleeve above the words");
		expect(!gateDeck("{\"layout\":\"diagonal\"}").stacked()
			&& DeckSkin.ROW.equals(gateDeck("{\"layout\":\"diagonal\"}").layout()),
			"a layout nobody invented falls back to the shipped one instead of drawing nothing");
		expect(gateDeck("{\"name\":\"Neon Sleeve\"}").name().equals("Neon Sleeve"),
			"a deck signs itself with the name its author chose");
		expect(gateDeck("{}").name().equals("gate.json"),
			"a deck that does not name itself is called by its file, so the picker never shows a blank");
		expect(!gateDeck("{\"progress\":\"false\"}").progress(),
			"an off switch reads as off even when the author quoted the word");

		DeckSkin pinnedDeck = gateDeck("{\"accent\":\"#1DB954\"}");
		expect(pinnedDeck.pinsAccent(), "a deck that names a colour owns it and stops sampling the cover");
		int pin = pinnedDeck.accentOr(0);
		expect(redOf(pin) == 0x1D && greenOf(pin) == 0xB9 && blueOf(pin) == 0x54 && alphaOf(pin) == 0xFF,
			"the six-digit colour arrives as the four channels it was written as, got " + Integer.toHexString(pin));
		expect(pin < 0, "an opaque ARGB int is negative - the sign is a real channel, which is why a "
			+ "failure cannot be a number here");
		expect(!gateDeck("{\"accent\":\"spotify green\"}").pinsAccent(),
			"a colour spelled wrong means no colour, not a random one");
		expect(gateDeck("{\"accent\":\"nonsense\"}").accentOr(0x112233) == 0x112233,
			"a bad accent falls back to whatever the caller would have used, so the cover still tints the card");
		DeckSkin shorthand = gateDeck("{\"accent\":\"#0f8\"}");
		expect(redOf(shorthand.accentOr(0)) == 0x00 && greenOf(shorthand.accentOr(0)) == 0xFF
			&& blueOf(shorthand.accentOr(0)) == 0x88,
			"the three-digit shorthand doubles its digits the way every other web colour does");
		expect(gateDeck("{\"accent\":\"1DB954\"}").pinsAccent(), "the leading hash is optional");
		DeckSkin panelled = gateDeck("{\"panel\":\"#0B1512\"}");
		expect(redOf(panelled.panel()) == 0x0B && greenOf(panelled.panel()) == 0x15
			&& blueOf(panelled.panel()) == 0x12,
			"a deck that names its panel gets that panel: an ARGB int is negative, so reading this with a "
			+ "sign test silently dropped every community colour and drew the built-in one instead");
		expect(alphaOf(panelled.panel()) == alphaOf(shipped.panel()),
			"naming a colour does not blank the panel, only the opacity knob does");
		DeckSkin carry = gateDeck("{\"panel\":\"#40FF0000\"}");
		expect(alphaOf(carry.panel()) == 0x40 && redOf(carry.panel()) == 0xFF,
			"an eight-digit colour carries its own alpha and means it");
		expect(alphaOf(gateDeck("{\"panel\":\"#40FF0000\",\"opacity\":0.5}").panel()) == 128,
			"an explicit opacity outranks the alpha carried inside the colour");

		DeckSkin noArt = gateDeck("{\"tile\":0}");
		Recorder textOnly = deckPaint(noArt, deckTrack, true, Palette.GREEN, 320, 200, HudLayout.Input.INERT);
		int[] textOnlyBox = bounds(textOnly);
		expect(textOnly.kind("art").isEmpty(),
			"tile 0 drops the artwork even when the bridge has a cover ready to hand over");
		expect(textOnly.kind("text").get(0).x1() == textOnlyBox[0] + noArt.pad(),
			"with nothing to its left the title moves into the padding instead of leaving a hole where the sleeve was");
		DeckSkin sleeve = gateDeck("{\"layout\":\"stack\"}");
		HudLayout.Frame sleeveFrame = deckFrame(sleeve, deckTrack, 320, 200);
		Recorder stacked = deckPaint(sleeve, deckTrack, true, Palette.GREEN, 320, 200, HudLayout.Input.INERT);
		Op tilePainted = stacked.kind("art").get(0);
		expect(stacked.kind("text").get(0).y1() >= tilePainted.y1() + sleeve.tile() + sleeve.gap(),
			"a stacked deck puts its words under the sleeve, not across it");
		expect(stacked.kind("text").get(0).x1() == bounds(stacked)[0] + sleeve.pad(),
			"a stacked deck starts its lines at the left edge of the sleeve");
		expect(sleeveFrame.parts().column() == sleeveFrame.width() - 2 * sleeve.pad(),
			"the stacked text gets the full width of the card, which is the point of stacking");
		HudLayout.Frame rowFrame = deckFrame(shipped, deckTrack, 320, 200);
		expect(rowFrame.parts().textX() == rowFrame.parts().tileX() + shipped.tile() + shipped.gap(),
			"the shipped card keeps its text one gap right of the artwork");

		DeckSkin noBar = gateDeck("{\"progress\":false}");
		java.util.function.Predicate<Op> couldBeATrack = op -> op.y2() - op.y1() == 2 && op.x2() - op.x1() > 8;
		expect(deckPaint(shipped, deckTrack, false, Palette.GREEN, 320, 200, HudLayout.Input.INERT)
			.kind("fill").stream().anyMatch(couldBeATrack),
			"the shipped card paints a track wider than any glyph in it");
		expect(deckPaint(noBar, deckTrack, false, Palette.GREEN, 320, 200, HudLayout.Input.INERT)
			.kind("fill").stream().noneMatch(couldBeATrack),
			"a deck with the bar switched off paints nothing that could be mistaken for one");
		expect(deckFrame(noBar, deckTrack, 320, 200).height() + 5 == rowFrame.height(),
			"switching off the progress bar takes off exactly its gap and its two pixels");

		DeckSkin noRow = gateDeck("{\"buttons\":false}");
		HudLayout.Frame noRowFrame = deckFrame(noRow, deckTrack, 320, 200);
		expect(noRowFrame.buttons().isEmpty(), "a deck with buttons off has no click targets at all");
		HudLayout.Rect playWas = rowFrame.buttons().get(HudLayout.PLAY);
		expect(noRowFrame.buttonAt(playWas.x() + 1, playWas.y() + 1) == -1,
			"the mouse cannot hit a button the painter never drew, which is the whole reason the frame is measured first");
		expect(noRowFrame.height() + 14 == rowFrame.height(),
			"dropping the control row takes off exactly its gap and its buttons");

		DeckSkin edgeless = gateDeck("{\"border\":false}");
		HudLayout.Frame flatFrame = deckFrame(edgeless, deckTrack, 320, 200);
		Recorder flatPaint = deckPaint(edgeless, deckTrack, true, Palette.GREEN, 320, 200, HudLayout.Input.INERT);
		Recorder shippedPaint = deckPaint(shipped, deckTrack, true, Palette.GREEN, 320, 200, HudLayout.Input.INERT);
		Op firstPainted = shippedPaint.kind("fill").get(0);
		Op edgelessPainted = flatPaint.kind("fill").get(0);
		expect(firstPainted.colour() == Palette.scale(Palette.GREEN, 0.55),
			"the shipped card paints its accent edge first, one pixel outside the panel");
		expect(edgelessPainted.colour() == edgeless.panel()
			&& edgelessPainted.x2() - edgelessPainted.x1() == flatFrame.width(),
			"a borderless deck opens with its own panel across the full width, because there is no outline to "
			+ "lay underneath it");
		expect(flatPaint.kind("fill").stream().noneMatch(op -> alphaOf(op.colour()) == 120),
			"no border means no ring around the sleeve");
		expect(shippedPaint.kind("fill").stream().anyMatch(op -> alphaOf(op.colour()) == 120),
			"the shipped card still rings its sleeve");
		expect(flatPaint.kind("fill").stream().noneMatch(op -> alphaOf(op.colour()) == 26
			&& op.y2() - op.y1() == 1 && op.x1() == flatFrame.x() + 2),
			"a flat deck drops the top highlight too, because that is part of the outline and not the panel");

		DeckSkin oneLine = gateDeck("{\"tile\":0,\"detail\":false,\"time\":false}");
		expect(deckFrame(oneLine, deckTrack, 320, 200).height() + 9
			== deckFrame(noArt, deckTrack, 320, 200).height(),
			"a text-only deck that hides both lines shrinks by exactly one line - the shipped card "
			+ "cannot show this, its sleeve is taller than both lines together");
		expect(deckFrame(gateDeck("{\"tile\":0,\"detail\":false}"), deckTrack, 320, 200).height()
			== deckFrame(noArt, deckTrack, 320, 200).height(),
			"hiding the detail line keeps the second row, because that is where the clock is drawn - "
			+ "shrinking for a line that is gone while the row stays is how the bar lands on the clock");
		expect(deckPaint(gateDeck("{\"detail\":false}"), deckTrack, false, Palette.GREEN, 320, 200,
			HudLayout.Input.INERT).kind("text").size() == 2,
			"with the detail line off the card paints its title and its clock and nothing else");
		expect(deckPaint(gateDeck("{\"time\":false}"), deckTrack, false, Palette.GREEN, 320, 200,
			HudLayout.Input.INERT).kind("text").size() == 2,
			"with the clock off the card paints its two lines and nothing at the right edge");
		expect(deckFrame(gateDeck("{\"time\":false}"), deckTrack, 320, 200).time().isEmpty(),
			"a deck that hides the clock takes its width out of the column as well, or the title stops short for nothing");

		// The invariant the first deck crop broke: a deck that borrows the sleeve's height for
		// its text ran its last line straight through the progress bar.
		for (DeckSkin snug : new DeckSkin[] {shipped, noArt, sleeve, oneLine,
			gateDeck("{\"tile\":0,\"detail\":false}"), gateDeck("{\"tile\":12}"),
			gateDeck("{\"tile\":0,\"layout\":\"stack\"}")}) {
			HudLayout.Frame held = deckFrame(snug, deckTrack, 320, 200);
			int textBottom = deckPaint(snug, deckTrack, true, Palette.GREEN, 320, 200, HudLayout.Input.INERT)
				.kind("text").stream().mapToInt(Op::y2).max().orElse(held.y());
			expect(!snug.progress() || held.parts().barY() >= textBottom + 3,
				"the tile=" + snug.tile() + " layout=" + snug.layout() + " detail=" + snug.detail()
				+ " deck keeps three pixels between its last line and its bar, got "
				+ (held.parts().barY() - textBottom));
		}

		// The deck knobs have to reach the painter: a second copy of the arithmetic is what
		// lets a deck set a tile the card then ignores.
		String measured = codeOnly(CLIENT_DIR + "HudLayout.java");
		int atFrame = measured.indexOf("public static Frame frame(");
		// dim() stopped being private when the lyrics panel started dimming with the card,
		// so the anchor is the signature and not the access modifier.
		int afterDraw = measured.indexOf("static int dim(int panel)");
		expect(atFrame >= 0 && afterDraw > atFrame, "the gate can find frame() and draw() to read them");
		expect(!java.util.regex.Pattern.compile("\\b(TILE|PAD|GAP|MIN_CONTENT|MAX_CONTENT)\\b")
			.matcher(measured.substring(atFrame, afterDraw)).find(),
			"frame() and draw() read their sizes off the deck, so nothing can paint a tile the mouse did not measure");

		// --- the folder: what a player drops in has to load, and has to load safely ---
		try {
			java.nio.file.Path deckDir = java.nio.file.Files.createTempDirectory("musicdeck-decks");
			expect(DeckLibrary.seedMissing(deckDir) == 3,
				"an empty folder is filled with the three example decks, so the feature is discoverable without a forum");
			expect(DeckLibrary.seedMissing(deckDir) == 0,
				"seeding writes only what is missing, so an edited deck survives updating the mod");
			expect(DeckLibrary.load(deckDir).size() == 4, "the folder offers its three examples plus the shipped card");
			expect(DeckLibrary.load(deckDir).get(0).equals(DeckSkin.BUILT_IN),
				"the built-in card is first in the picker, whichever way the names sort");
			expect(DeckLibrary.resolve(deckDir, "minimal").tile() == 0,
				"minimal.json really is the text-only card its file promises");
			expect(DeckLibrary.resolve(deckDir, "MINIMAL").tile() == 0,
				"a deck name is matched without regard to case, because nobody remembers how they typed it");
			expect(DeckLibrary.resolve(deckDir, "Big Sleeve").stacked(),
				"a player can type the name the deck signs itself with, spaces and all");
			expect(!DeckLibrary.resolve(deckDir, "flat-green").border()
				&& !DeckLibrary.resolve(deckDir, "flat-green").progress(),
				"flat-green really is the flat card its file promises");
			expect(DeckLibrary.resolve(deckDir, "flat-green").pinsAccent()
				&& DeckLibrary.resolve(deckDir, "flat-green").accentOr(0) == Palette.GREEN,
				"flat-green owns its colour, which is the one thing a community deck asks for most");
			for (String exampleName : new String[] {"minimal", "big-sleeve", "flat-green"}) {
				DeckSkin exampleDeck = DeckLibrary.resolve(deckDir, exampleName);
				HudLayout.Frame drawn = deckFrame(exampleDeck, deckTrack, 320, 200);
				expect(!exampleDeck.equals(shipped)
					&& (drawn.width() != rowFrame.width() || drawn.height() != rowFrame.height()),
					exampleName + " changes the card it is drawn on, instead of shipping the built-in look under a new name");
			}

			expect(DeckLibrary.find(deckDir, "not-a-deck") == null,
				"the command can say out loud that the name the player typed is not in the folder");
			expect(DeckLibrary.resolve(deckDir, "not-a-deck") == DeckSkin.BUILT_IN,
				"a typo left in the config file draws a plain card, not no card");
			expect(DeckLibrary.find(deckDir, "") == DeckSkin.BUILT_IN, "an empty deck name is the built-in card");

			java.nio.file.Files.writeString(deckDir.resolve("hostile.json"),
				"{\"tile\":9999,\"pad\":99,\"gap\":99,\"minWidth\":1,\"maxWidth\":9999,\"opacity\":0,"
					+ "\"layout\":\"stack\"}");
			DeckSkin hostile = DeckLibrary.resolve(deckDir, "hostile");
			HudLayout.Frame hostileFrame = deckFrame(hostile, deckTrack, 640, 360);
			expect(hostile.tile() == DeckSkin.MAX_TILE && hostile.pad() == 10 && hostile.gap() == 12,
				"a file written to break the mod is clamped before it reaches the layout, got tile "
				+ hostile.tile() + " pad " + hostile.pad());
			expect(hostileFrame.width() <= 640 && hostileFrame.height() <= 360,
				"a hostile deck still fits the screen it is measured against, at " + hostileFrame.width()
				+ "x" + hostileFrame.height());
			NowPlaying deckWall = playing("Playing",
				"yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy", "Ab", "Cd",
				1.0, 100.0);
			HudLayout.Frame wallFrame = deckFrame(hostile, deckWall, 640, 360);
			expect(wallFrame.width() <= 640,
				"a wall of text through a hostile deck still fits the window, got " + wallFrame.width()
					+ " wide - the ceiling has to be a number the screen can hold, not just a constant");
			Recorder hostilePaint = deckPaint(hostile, deckTrack, true, Palette.GREEN, 640, 360,
				HudLayout.Input.INERT);
			expect(hostilePaint.kind("fill").stream().allMatch(op -> op.x1() >= hostileFrame.x() - 1
				&& op.x2() <= hostileFrame.x() + hostileFrame.width() + 1 && op.y1() >= hostileFrame.y() - 1
				&& op.y2() <= hostileFrame.y() + hostileFrame.height() + 1),
				"not one pixel of a clamped deck lands outside its own card");

			java.nio.file.Files.writeString(deckDir.resolve("notes.txt"), "not a deck, not json");
			java.nio.file.Files.writeString(deckDir.resolve("broken.json"), "this is not json at all");
			expect(DeckLibrary.load(deckDir).size() == 6,
				"a text file is ignored but a .json that cannot be read still counts as a deck, got "
				+ DeckLibrary.load(deckDir).size());
			expect(DeckLibrary.resolve(deckDir, "broken").tile() == HudLayout.TILE,
				"a deck file that cannot be parsed falls back to the shipped numbers instead of taking the card down with it");
			int roundTrips = 0;
			for (DeckSkin listed : DeckLibrary.load(deckDir)) {
				String handle = DeckLibrary.handle(listed);
				roundTrips++;
				expect(DeckLibrary.resolve(deckDir, handle).name().equals(listed.name()),
					"the picker offers '" + handle + "' and the command accepts exactly that back");
			}
			expect(roundTrips == 6, "every deck the folder hands back has a handle the command can take, got "
				+ roundTrips);
			for (java.io.File leftover : deckDir.toFile().listFiles()) {
				leftover.delete();
			}
			deckDir.toFile().delete();
		} catch (java.io.IOException e) {
			expect(false, "the deck folder checks could not get a temp directory: " + e);
		}

		// --- the deck reaches the player: config, card, command, settings ---
		String deckConfig = codeOnly(CLIENT_DIR + "MediaConfig.java");
		expect(deckConfig.contains("DeckLibrary.seedMissing("),
			"the decks folder is seeded when the config loads, so a player finds the examples without a terminal");
		expect(deckConfig.contains("DeckLibrary.seedMissing("),
			"the decks folder is seeded when the config loads, so a player finds the examples without a terminal");
		expect(count(deckConfig, "DeckLibrary.resolve(") == 2,
			"the saved name is resolved to a deck on load and on a refresh, once each, not every frame");
		expect(deckConfig.contains("public DeckSkin deckSkin()"),
			"the card is handed a resolved deck rather than being asked to read the folder every frame");
		expect(deckConfig.contains("setProperty(\"deck\"") && deckConfig.contains("getProperty(\"deck\""),
			"the chosen deck is both read back and written, so it survives a restart");
		String deckHud = codeOnly(CLIENT_DIR + "NowPlayingHud.java");
		expect(java.util.regex.Pattern.compile("Options\\([^;]*deckSkin\\(").matcher(deckHud).find(),
			"the card is measured from the deck the player picked, not from the shipped one next to a deck that is only read for its colour");
		expect(deckHud.contains("accentOr("),
			"a deck that pins its colour is asked before the cover is sampled, so the artwork cannot overrule it");
		String deckCommand = codeOnly(CLIENT_DIR + "MusicDeckCommand.java");
		expect(deckCommand.contains("literal(\"deck\")"), "/music deck is the verb a player types for a look");
		expect(deckCommand.contains("suggestDecks") && deckCommand.contains("DeckLibrary"),
			"/music deck offers the names that are actually in the folder");
		String deckScreen = codeOnly(CLIENT_DIR + "SettingsScreen.java");
		expect(deckScreen.contains("\"musicdeck.setting.deck\"") && deckScreen.contains("DeckLibrary.load("),
			"the deck is a row on the settings screen, listing what is in the folder");
		expect(deckScreen.contains("config.chooseDeck("),
			"picking a deck saves it on the spot, the way every other row on that screen does");
		expect(deckScreen.contains("Component.literal("),
			"a deck shows the name its author gave it, not a translation key nobody wrote");

		// Adding a row and forgetting to shift the ones under it puts two widgets on one line,
		// which is invisible until it is shipped, so count the rows instead of trusting the order.
		int keysAt = deckScreen.indexOf("ROW_KEYS = {");
		int keysEnd = deckScreen.indexOf("};", keysAt);
		int declaredRows = keysAt < 0 || keysEnd < 0 ? 0
			: count(deckScreen.substring(keysAt, keysEnd), "\"musicdeck.setting.");
		expect(declaredRows == 9, "the settings screen declares one row per knob, got " + declaredRows);
		java.util.Set<Integer> placed = new java.util.HashSet<>();
		java.util.regex.Matcher rowCall = java.util.regex.Pattern
			.compile("(?:toggle|choice|position)\\((\\d+),").matcher(deckScreen);
		while (rowCall.find()) {
			Integer index = Integer.valueOf(rowCall.group(1));
			expect(!placed.contains(index), "row " + index + " gets one widget, not two on top of each other");
			placed.add(index);
		}
		for (int row = 0; row < declaredRows; row++) {
			expect(placed.contains(row), "row " + row + " of the settings screen has a control on it");
		}
		int screenTop = intLiteral(deckScreen, "TOP");
		int screenStep = intLiteral(deckScreen, "STEP");
		int buttonHigh = intLiteral(deckScreen, "BUTTON_H");
		expect(screenTop + declaredRows * screenStep + 6 + buttonHigh <= 240,
			declaredRows + " rows plus the footer still fit the smallest window the game gives, and this one ends at "
			+ (screenTop + declaredRows * screenStep + 6 + buttonHigh));

		// The clock and the clip have to be read from the same column, or the card rests at the
		// wrong moment and the last letters are never on screen long enough to read.
		NowPlaying slides = playing("Playing", "abcdefghijklmnopqrstuvwxyzab", "Ab", "Cd", 1.0, 100.0);
		Recorder clocked = deckPaint(shipped, slides, true, Palette.GREEN, 640, 360, HudLayout.Input.INERT);
		int clippedOverhang = 0;
		for (Op run : clocked.kind("text")) {
			Op held = clocked.clipAround(run);
			if (held != null) {
				clippedOverhang = Math.max(clippedOverhang, clocked.textWidth(run.text()) - (held.x2() - held.x1()));
			}
		}

		expect(clippedOverhang > 0, "this card's title does overflow the column it was clipped to");
		expect(HudLayout.travelSeconds(clocked, deckFrame(shipped, slides, 640, 360))
			== HudLayout.travelSeconds(clippedOverhang),
			"the card's clock is timed from the very column the painter clipped to, got "
			+ HudLayout.travelSeconds(clocked, deckFrame(shipped, slides, 640, 360)) + "s against "
			+ HudLayout.travelSeconds(clippedOverhang) + "s");

		// --- a card parked past the middle of the screen turns to face the corner it sits in ---
		HudLayout.Frame stay = placeFrame(shipped, deckTrack, 6, 640, 360);
		HudLayout.Frame turn = placeFrame(shipped, deckTrack, 634, 640, 360);
		expect(!stay.mirror() && turn.mirror(),
			"a card at the left of the screen keeps its sleeve on the left, and one dragged to the right edge turns");
		expect(turn.width() == stay.width() && turn.height() == stay.height(),
			"turning around resizes nothing: it moves what is inside the panel, it does not grow the panel");
		expect(turn.parts().column() == stay.parts().column(),
			"the text column comes across the same width, so a long line slides the same distance either side");
		expect(stay.parts().tileX() == stay.x() + shipped.pad(),
			"the left-parked sleeve sits at the left pad, exactly where it always sat");
		expect(turn.parts().tileX() + shipped.tile() == turn.x() + turn.width() - shipped.pad(),
			"the right-parked sleeve ends flush against the edge the card was parked on");
		expect(turn.parts().textX() == turn.x() + shipped.pad(),
			"the text takes the side the sleeve left");
		expect(turn.parts().tileX() - shipped.gap() - turn.parts().textX() == turn.parts().column(),
			"the column stops where the sleeve begins, with the deck's own gap between the two");

		Recorder stayed = placePaint(shipped, deckTrack, true, Palette.GREEN, 6, 640, 360, HudLayout.Input.INERT);
		Recorder turnedPaint = placePaint(shipped, deckTrack, true, Palette.GREEN, 634, 640, 360,
			HudLayout.Input.INERT);
		expect(stayed.kind("art").get(0).x1() == stay.parts().tileX()
			&& turnedPaint.kind("art").get(0).x1() == turn.parts().tileX(),
			"the painter blits the cover where the measured frame puts it, on either side");
		List<Op> halo = turnedPaint.kind("fill").stream().filter(op -> alphaOf(op.colour()) == 120).toList();
		expect(halo.size() == 4 && halo.stream().mapToInt(Op::x1).min().orElse(-1) == turn.parts().tileX() - 1
			&& halo.stream().mapToInt(Op::x2).max().orElse(-1) == turn.parts().tileX() + shipped.tile() + 1,
			"the accent ring closes around the sleeve on whichever side of the panel it sits, got " + halo.size()
				+ " strokes");
		HudLayout.Rect lastButton = turn.buttons().get(HudLayout.BUTTONS - 1);
		expect(Math.abs(stay.buttons().get(0).x() - stay.x() - (turn.x() + turn.width() - lastButton.x() - lastButton.w()))
			<= 1,
			"the control row stays centred under the panel, so turning the card does not drag its buttons off an edge");
		expect(bar(stayed).get(0).x2() - bar(stayed).get(0).x1() == bar(turnedPaint).get(0).x2()
			- bar(turnedPaint).get(0).x1(),
			"and the progress track is the same length on both, because the bar spans the panel rather than the text");
		Op stayClock = stayed.kind("text").get(2);
		Op flipClock = turnedPaint.kind("text").get(2);
		expect(stayClock.x1() + stayed.textWidth(stayClock.text()) == stay.x() + stay.width() - shipped.pad(),
			"a left-parked card ends its clock at the panel's right pad, as it always did");
		expect(flipClock.x1() + turnedPaint.textWidth(flipClock.text()) == turn.parts().tileX() - shipped.gap(),
			"a right-parked one brings the clock in against the sleeve instead of hiding it underneath the cover");
		expect(turnedPaint.kind("text").get(0).x1() == turn.parts().textX()
			&& turnedPaint.kind("text").get(1).x1() == turn.parts().textX(),
			"both lines still start at the near edge of the column, so mirroring never right-aligns text mid-slide");

		int half = (640 - turn.width()) / 2;
		expect(!placeFrame(shipped, deckTrack, half, 640, 360).mirror(),
			"a card whose own middle is exactly on the middle of the screen has not turned yet");
		expect(placeFrame(shipped, deckTrack, half + 1, 640, 360).mirror(),
			"one pixel of drag past the middle and it has, so the line is the screen's centre, not a fixed x");

		// The line has to be tested where it actually falls, not where a rounded division guesses
		// it: the screen below is built from the card's own measured width, so the card lands with
		// its centre on the screen's centre to the pixel. Exactly on the line there is no side to be
		// on, and a frame that turns the card there would flip a perfectly centred HUD the moment
		// the player let go of it.
		HudLayout.Frame square = placeFrame(shipped, deckTrack, 7, 900, 360);
		int lineScreen = 2 * 7 + square.width();
		HudLayout.Frame onLine = placeFrame(shipped, deckTrack, 7, lineScreen, 360);
		expect(onLine.width() == square.width() && 2 * onLine.x() + onLine.width() == lineScreen,
			"the dead-centre fixture really lands on the middle line, got x=" + onLine.x()
				+ " width=" + onLine.width() + " of a " + lineScreen + "-wide screen");
		expect(!onLine.mirror(), "a card standing exactly in the middle keeps its sleeve on the left");
		expect(placeFrame(shipped, deckTrack, 8, lineScreen, 360).mirror(),
			"and one pixel of drag past that line is enough to turn it");
		HudLayout.Frame full = placeFrame(shipped, deckTrack, 6, 100, 360);
		expect(!full.mirror() && full.parts().tileX() == full.x() + shipped.pad(),
			"a card wider than the screen keeps its sleeve on the left: parked flush, it has no side to be on");

		for (DeckSkin snug : List.of(shipped, gateDeck("{\"layout\":\"stack\"}"), gateDeck("{\"tile\":0}"),
			gateDeck("{\"buttons\":false}"), gateDeck("{\"border\":false,\"progress\":false}"),
			gateDeck("{\"detail\":false}"), gateDeck("{\"time\":false}"),
			gateDeck("{\"tile\":64,\"maxWidth\":260,\"layout\":\"stack\"}"))) {
			HudLayout.Frame parked = placeFrame(snug, deckTrack, 634, 640, 360);
			expect(parked.parts().tileX() >= parked.x() + snug.pad()
				&& parked.parts().tileX() + snug.tile() <= parked.x() + parked.width() - snug.pad(),
				"mirrored, the deck " + snug.name() + " still keeps its sleeve inside its own panel");
			expect(parked.parts().textX() >= parked.x() + snug.pad()
				&& parked.parts().textX() + parked.parts().column() <= parked.x() + parked.width() - snug.pad(),
				"mirrored, the deck " + snug.name() + " still keeps its text inside its own panel");
		}

		DeckSkin words = gateDeck("{\"tile\":0}");
		HudLayout.Frame wordsLeft = placeFrame(words, deckTrack, 6, 640, 360);
		HudLayout.Frame wordsRight = placeFrame(words, deckTrack, 634, 640, 360);
		expect(wordsRight.parts().textX() - wordsRight.x() == wordsLeft.parts().textX() - wordsLeft.x()
			&& wordsRight.parts().column() == wordsLeft.parts().column(),
			"a card with no sleeve has nothing to swap, so its text sits exactly where it sat on the other side");
		DeckSkin tall = gateDeck("{\"layout\":\"stack\",\"tile\":64}");
		HudLayout.Frame tallRight = placeFrame(tall, deckTrack, 634, 640, 360);
		expect(tallRight.parts().tileX() + tall.tile() == tallRight.x() + tallRight.width() - tall.pad()
			&& tallRight.parts().textX() == tallRight.x() + tall.pad(),
			"a stacked deck keeps its text at the left and takes the sleeve round to the edge it is parked at");
		expect(tallRight.parts().column() == placeFrame(tall, deckTrack, 6, 640, 360).parts().column(),
			"and its column stays the full inner width, because on a stacked card the sleeve is above the text, not beside it");
		Recorder turnedLive = placePaint(shipped, deckTrack, true, Palette.GREEN, 634, 640, 360,
			new HudLayout.Input("", -1, -1, true, false));
		int painted = 0;
		for (HudLayout.Rect hit : turn.buttons()) {
			if (face(turnedLive, hit) != null) {
				painted++;
			}
		}

		expect(painted == HudLayout.BUTTONS, "a mirrored card paints all four buttons under their own click "
			+ "targets, got " + painted);

		// The slide has to agree with the clip on either side, or a right-parked card rests its
		// title at the wrong moment or runs it straight through the cover.
		HudLayout.Frame turnedSlides = placeFrame(shipped, slides, 634, 640, 360);
		Recorder turnedRun = placePaint(shipped, slides, true, Palette.GREEN, 634, 640, 360, HudLayout.Input.INERT);
		expect(HudLayout.travelSeconds(turnedRun, turnedSlides)
			== HudLayout.travelSeconds(clocked, deckFrame(shipped, slides, 640, 360)),
			"the same title takes the same time to run out on either side, because both are timed from one column");
		Op turnedClip = turnedRun.clipAround(turnedRun.kind("text").get(0));
		expect(turnedClip != null && turnedClip.x2() <= turnedSlides.parts().tileX(),
			"and a mirrored card clips its sliding title at the sleeve rather than across it");

		String layoutCode = codeOnly(CLIENT_DIR + "HudLayout.java");
		expect(count(layoutCode, "parts.textX() + parts.column() - canvas.textWidth(frame.time())") == 1,
			"the clock is placed from the column the frame measured, never from the panel's own pad");
		expect(!layoutCode.contains("frame.mirror()"),
			"the painter takes no side of its own: every box it draws is already mirrored in the frame");
		// Since the words are placed against the same screen as the card, the window is read
		// once a frame and both are sized from that one read. The exact declaration is pinned
		// as well as the counts, because a swapped pair reads each name once and still asks
		// the wrong side of the screen where the card is.
		expect(count(hud, "graphics.guiWidth()") == 1 && count(hud, "graphics.guiHeight()") == 1
			&& hud.contains("int guiWidth = graphics.guiWidth(), guiHeight = graphics.guiHeight();")
			&& count(hud, "getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight()") == 1,
			"the card and the words under it are measured against one live read of the screen, which is what "
				+ "tells the card which side it is on");

		// --- lyrics: the file, the match and the panel ---
		// Spotify draws its own lyrics inside its window and hands no part of them to another
		// process, so this feature's only source is a file the player supplies. Everything
		// below is text and geometry, which is what makes it checkable with no song playing.
		Lyrics lySample = Lyrics.parse("""
			[ti:Trust]
			[ar:Brent Faiyaz]
			[00:10.50]First line
			[00:14.00]Second line
			[00:18.25]Third line
			""");
		expect(lySample.lines().size() == 3, "three timed lines, got " + lySample.lines().size());
		expect(Math.abs(lySample.at(0).at() - 10.5) < 1e-9,
			"hundredths are a decimal of a second, got " + lySample.at(0).at());
		expect(lySample.at(0).text().equals("First line"), "the stamp is not part of the words");
		expect(Math.abs(Lyrics.parse("[00:01.5]x").at(0).at() - 1.5) < 1e-9,
			".5 is half a second, not five hundredths");
		expect(Math.abs(Lyrics.parse("[00:01.500]x").at(0).at() - 1.5) < 1e-9,
			"thousandths are read as a decimal too");
		expect(Lyrics.parse("[00:01.00]a\r\n[00:02.00]b").lines().size() == 2,
			"a file saved with Windows line endings parses like one saved with Unix ones");
		expect(Lyrics.parse("\uFEFF[00:01.00]a").lines().size() == 1,
			"a byte-order mark at the top does not eat the first line");
		expect(Math.abs(Lyrics.parse("[offset:500]\n[00:10.00]a").at(0).at() - 9.5) < 1e-9,
			"a positive offset brings the words forward");
		expect(Math.abs(Lyrics.parse("[00:10.00]a\n[offset:-500]").at(0).at() - 10.5) < 1e-9,
			"a negative offset pushes them back, and one written at the bottom still covers the top");
		expect(Math.abs(Lyrics.parse("[offset:abc][00:10.00]a").at(0).at() - 10.0) < 1e-9,
			"an unreadable offset shifts nothing");
		expect(Lyrics.parse("[offset:20000][00:01.00]a").at(0).at() == 0.0,
			"an offset longer than the song starts it at zero rather than before it");
		Lyrics lyChorus = Lyrics.parse("[00:01.00][00:05.00]Chorus");
		expect(lyChorus.lines().size() == 2 && lyChorus.at(1).text().equals("Chorus"),
			"a line with two stamps is the same words at both times");
		// Two stamps side by side, which is what a held note looks like: removing them
		// without leaving a space glues the words together.
		Lyrics lyWordTimed = Lyrics.parse("[00:18.00]<00:18.89>We're <00:19.25><00:19.80>no strangers");
		expect(lyWordTimed.at(0).text().equals("We're no strangers"),
			"an enhanced file's word stamps are spacing, not words, got " + lyWordTimed.at(0).text());
		expect(Lyrics.parse("[00:01.00]2 < 3 and <not a stamp>").at(0).text().equals("2 < 3 and <not a stamp>"),
			"and a line that really does contain an angle bracket keeps it");
		Lyrics lyGap = Lyrics.parse("[00:01.00]a\n[00:03.00]\n[00:05.00]b");
		expect(lyGap.lines().size() == 3 && lyGap.at(1).text().isEmpty(),
			"a timed line with no words is kept as a gap rather than dropped");
		expect(Lyrics.parse("[ti:x]\n[ar:y]\nplain words\n").lines().isEmpty(),
			"a file of tags and prose has no timed lyric in it");
		expect(Lyrics.parse("[00:09.00]late\n[00:02.00]early").at(0).text().equals("early"),
			"the lines are sorted, so a file written out of order cannot rewind the song");
		Lyrics lyQuotes = Lyrics.parse("[00:01.00]Don\u2019t \u201Crun\u201D \u2014 wait\u2026");
		expect(lyQuotes.at(0).text().equals("Don't \"run\" - wait..."),
			"the curly quotes a lyric site writes are folded to glyphs the game's font has, got "
				+ lyQuotes.at(0).text());
		expect(lySample.indexAt(5.0) == -1, "before the first line nothing is being sung");
		expect(lySample.indexAt(10.5) == 0 && lySample.indexAt(13.999) == 0,
			"a line owns the time from its stamp to the next one");
		expect(lySample.indexAt(14.0) == 1, "the next line starts exactly on its stamp");
		expect(lySample.indexAt(999.0) == 2, "after the last stamp the last line stays up");
		expect(Lyrics.EMPTY.indexAt(1.0) == -1 && Lyrics.EMPTY.at(0) == null && !Lyrics.EMPTY.present(),
			"no lyrics is no line and no panel, not a crash");

		expect(LyricsLibrary.key("Brent Faiyaz - Trust").equals("brent faiyaz trust"),
			"a file name is compared with its case and punctuation thrown away");
		expect(LyricsLibrary.key(" brent_faiyaz__TRUST ").equals(LyricsLibrary.key("Brent Faiyaz - Trust")),
			"underscores and doubled spaces do not change which song a file is for");
		List<String> lyNames = LyricsLibrary.candidates("Brent Faiyaz", "Trust");
		expect(lyNames.get(0).equals("brent faiyaz trust") && lyNames.get(1).equals("trust"),
			"the artist and title together are tried before the title alone, so another artist's "
				+ "\"Trust\" cannot win, got " + lyNames);
		expect(LyricsLibrary.candidates("Taylor Swift", "Welcome To New York (From The Vault)")
			.contains("taylor swift welcome to new york"),
			"a title with a bracketed aside also matches a file named without the aside");

		java.nio.file.Path lyFolder = java.nio.file.Files.createTempDirectory("minetunes-lyrics");
		java.nio.file.Files.writeString(lyFolder.resolve("Brent Faiyaz - Trust.lrc"), "[00:01.00]a");
		expect(LyricsLibrary.find(lyFolder, "Brent Faiyaz", "Trust") != null,
			"a file named after the track is found");
		java.nio.file.Path lyLoose = java.nio.file.Files.createTempDirectory("minetunes-loose");
		java.nio.file.Files.writeString(lyLoose.resolve("brent_faiyaz - TRUST.lrc"), "[00:01.00]a");
		expect(LyricsLibrary.find(lyLoose, "Brent Faiyaz", "Trust") != null,
			"and so is one spelled with different punctuation and case");
		java.nio.file.Path lyTitle = java.nio.file.Files.createTempDirectory("minetunes-title");
		java.nio.file.Files.writeString(lyTitle.resolve("Trust.lrc"), "[00:01.00]a");
		expect(LyricsLibrary.find(lyTitle, "Brent Faiyaz", "Trust") != null,
			"a file named after the title alone is the last resort, for a song with no artist in its name");
		expect(LyricsLibrary.find(lyTitle, "Brent Faiyaz", "Trustfall") == null,
			"a different song in the same folder is not read as this one");
		expect(LyricsLibrary.find(lyFolder.resolve("missing"), "Brent Faiyaz", "Trust") == null
			&& LyricsLibrary.find(null, "a", "b") == null,
			"a folder that is not there is no lyrics rather than a crash");
		expect(!LyricsLibrary.read(lyFolder.resolve("absent.lrc")).present(),
			"a file that cannot be read is no lyrics");

		java.nio.file.Path lyUtf8 = java.nio.file.Files.createTempDirectory("minetunes-utf8");
		java.nio.file.Files.writeString(lyUtf8.resolve("A - T.lrc"), "[00:01.00]Caf\u00e9");
		expect(LyricsLibrary.read(lyUtf8.resolve("A - T.lrc")).at(0).text().equals("Caf\u00e9"),
			"a UTF-8 file keeps its accented letters");
		java.nio.file.Path lyLegacy = java.nio.file.Files.createTempDirectory("minetunes-legacy");
		byte[] lyLatin1 = {0x5B, 0x30, 0x30, 0x3A, 0x30, 0x31, 0x2E, 0x30, 0x30, 0x5D, (byte) 0xE9};
		java.nio.file.Files.write(lyLegacy.resolve("A - T.lrc"), lyLatin1);
		expect(LyricsLibrary.read(lyLegacy.resolve("A - T.lrc")).at(0).text().equals("\u00e9"),
			"a file saved in the old Windows encoding reads as accents instead of unreadable bytes");

		java.nio.file.Path lySeed = java.nio.file.Files.createTempDirectory("minetunes-seed");
		expect(LyricsLibrary.seedMissing(lySeed) == 1, "an empty folder gets the example file");
		expect(LyricsLibrary.seedMissing(lySeed) == 0, "and a folder that already has it is left alone");
		expect(LyricsLibrary.read(lySeed.resolve("example.lrc")).lines().size() == 4,
			"the example really is a parseable file, not a note about one");
		expect(LyricsLibrary.find(lySeed, "Brent Faiyaz", "Trust") == null,
			"and it matches no real track, so seeding never draws words over a song");

		// A fetcher that counts instead of throwing: a broken off-switch must fail a check
		// that prints FAIL, not take the whole suite down before the later gates run.
		CountingFetcher lyLocal = new CountingFetcher();
		LyricsCache lyCache = new LyricsCache(lyLocal);
		java.nio.file.Path lyCacheDir = java.nio.file.Files.createTempDirectory("minetunes-cache");
		java.nio.file.Path lyCacheFile = lyCacheDir.resolve("A - T.lrc");
		java.nio.file.Files.writeString(lyCacheFile, "[00:01.00]one");
		expect(lyCache.of(lyCacheDir, "A", "T", false, 0.0).lines().size() == 1 && lyCache.file() != null,
			"the cache resolves the track it was handed");
		expect(!lyCache.of(lyCacheDir, "B", "T", false, 0.0).present() && lyCache.file() == null,
			"another artist with the same title is given no words at all");
		expect(lyCache.of(lyCacheDir, "A", "T", false, 0.0).lines().size() == 1,
			"and coming back to the first track finds its file again");
		java.nio.file.Files.writeString(lyCacheFile, "[00:01.00]one\n[00:02.00]two");
		expect(lyCache.of(lyCacheDir, "A", "T", false, 0.0).lines().size() == 1,
			"the file is not re-read on every frame, which is what keeps the card's cost flat");
		Thread.sleep(1100L);
		expect(lyCache.of(lyCacheDir, "A", "T", false, 0.0).lines().size() == 2,
			"but a file the player edits is picked up while the track is still playing");
		expect(lyLocal.calls == 0,
			"and a folder that answers every frame opens no lookup at all, got " + lyLocal.calls);

		NowPlaying lyTrack = playing("Playing", "Trust", "Brent Faiyaz", "Bj\u00f6rk", 12.0, 158.4);
		HudLayout.Frame lyCard = frame(lyTrack, 640, 360);
		Recorder lyRuler = new Recorder();
		LyricsLayout.Frame lyPanel = LyricsLayout.frame(lyRuler, lySample, 12.0, lyCard, 640, 360);
		expect(lyPanel.y() == lyCard.y() + lyCard.height() + LyricsLayout.GAP,
			"the panel sits one gap under the card when the screen has room, got " + lyPanel.y());
		expect(lyPanel.x() == lyCard.x() && lyPanel.width() >= lyCard.width(),
			"it starts on the card's edge and is never narrower than the card");
		expect(lyPanel.height() == LyricsLayout.height(),
			"and is exactly tall enough for three lines");
		expect(lyPanel.current() == 0 && lyPanel.first() == 0,
			"at 12s the first line is current and the window starts there");

		StringBuilder lyMany = new StringBuilder();
		for (int index = 0; index < 10; index++) {
			lyMany.append(String.format("[00:%02d.00]Line %d%n", index * 2 + 2, index + 1));
		}

		Lyrics lyTen = Lyrics.parse(lyMany.toString());
		LyricsLayout.Frame lyMid = LyricsLayout.frame(lyRuler, lyTen, 12.0, lyCard, 640, 360);
		expect(lyMid.current() == 5 && lyMid.first() == 4,
			"the line being sung stays in the middle of the window, got " + lyMid.current() + "/" + lyMid.first());
		LyricsLayout.Frame lyEnd = LyricsLayout.frame(lyRuler, lyTen, 60.0, lyCard, 640, 360);
		expect(lyEnd.current() == 9 && lyEnd.first() == 7,
			"at the end the window stops sliding and fills the panel, got " + lyEnd.first());
		LyricsLayout.Frame lyStart = LyricsLayout.frame(lyRuler, lyTen, 0.0, lyCard, 640, 360);
		expect(lyStart.current() == -1 && lyStart.first() == 0,
			"before the first stamp nothing is highlighted, but the words still show");

		Lyrics lyDrift = Lyrics.parse("[00:01.00]a\n[00:02.00]b\n[00:03.00]c\n[00:04.00]d\n[00:05.00]e\n"
			+ "[00:06.00]a much longer line than anything on screen right now\n[00:07.00]g");
		LyricsLayout.Frame lySteady = LyricsLayout.frame(lyRuler, lyDrift, 2.0, lyCard, 640, 360);
		expect(lySteady.width() > lyCard.width(),
			"the width comes from the widest line in the file rather than the three on screen, so the panel "
				+ "does not breathe as the song moves, got " + lySteady.width());

		Lyrics lyWide = Lyrics.parse("[00:01.00]" + "x".repeat(120));
		LyricsLayout.Frame lyCapped = LyricsLayout.frame(lyRuler, lyWide, 2.0, lyCard, 640, 360);
		expect(lyCapped.width() == LyricsLayout.MAX_WIDTH,
			"a line wider than any panel is capped, got " + lyCapped.width());
		LyricsLayout.Frame lyNarrow = LyricsLayout.frame(lyRuler, lyWide, 2.0, lyCard, 200, 360);
		expect(lyNarrow.width() <= 200, "and the panel never leaves the screen it is drawn on");

		Recorder lyFull = new Recorder();
		LyricsLayout.draw(lyFull, lyPanel, Palette.GREEN, true);
		List<Op> lyLines = lyFull.kind("text");
		expect(lyLines.size() == 3, "all three lines are drawn, got " + lyLines.size());
		expect(lyLines.get(0).y1() == lyPanel.y() + LyricsLayout.PAD
			&& lyLines.get(1).y1() == lyLines.get(0).y1() + LyricsLayout.LINE + LyricsLayout.LINE_GAP,
			"the rows are one line and one gap apart");
		expect(lyLines.get(0).colour() == Palette.WHITE,
			"the line being sung is the brightest ink on the panel");
		expect(lyLines.get(1).colour() != lyLines.get(0).colour()
			&& lyLines.get(2).colour() == lyLines.get(1).colour(),
			"the two neighbours share a dimmer ink, so the current line is the only one lit");
		expect(lyLines.get(0).text().equals("First line") && lyLines.get(1).text().equals("Second line"),
			"the window shows the line the clock is on and the one after it");
		for (Op op : lyLines) {
			expect(op.x1() >= lyPanel.x() + LyricsLayout.PAD
				&& op.x2() <= lyPanel.x() + lyPanel.width() - LyricsLayout.PAD,
				"every line stays inside the panel's side padding");
			expect(op.y1() >= lyPanel.y() + LyricsLayout.PAD
				&& op.y2() <= lyPanel.y() + lyPanel.height() - LyricsLayout.PAD + 1,
				"every line stays inside its top and bottom padding");
			expect(Math.abs((op.x1() - lyPanel.x()) - (lyPanel.x() + lyPanel.width() - op.x2())) <= 1,
				"each line is centred in the panel");
		}

		Recorder lyCut = new Recorder();
		LyricsLayout.draw(lyCut, lyCapped, Palette.GREEN, true);
		List<Op> lyCutLines = lyCut.kind("text");
		expect(lyCutLines.size() == 1 && lyCutLines.get(0).text().endsWith("...")
			&& lyRuler.textWidth(lyCutLines.get(0).text()) <= lyCapped.width() - 2 * LyricsLayout.PAD,
			"a line too wide for the capped panel is cut with dots rather than written over the border, got \""
				+ (lyCutLines.isEmpty() ? "" : lyCutLines.get(0).text()) + "\"");

		LyricsLayout.Frame lyHole = LyricsLayout.frame(lyRuler, lyGap, 3.5, lyCard, 640, 360);
		Recorder lyHoleDraw = new Recorder();
		LyricsLayout.draw(lyHoleDraw, lyHole, Palette.GREEN, true);
		expect(lyHoleDraw.kind("text").size() == 2,
			"a timed gap draws nothing in its row, so the previous line is not left standing through it");

		Recorder lyPausedDraw = new Recorder();
		LyricsLayout.draw(lyPausedDraw, lyPanel, Palette.GREEN, false);
		expect(lyPausedDraw.kind("fill").size() == lyFull.kind("fill").size(),
			"a paused lyric panel keeps its shape");
		Op lyLiveBand = lyricBand(lyFull, lyPanel);
		Op lyPausedBand = lyricBand(lyPausedDraw, lyPanel);
		expect(alphaOf(lyPausedBand.colour()) < alphaOf(lyLiveBand.colour()),
			"a paused panel dims with the card instead of staying lit");
		expect((lyPausedBand.colour() & 0xFFFFFF) == (lyLiveBand.colour() & 0xFFFFFF),
			"and dimming is a multiplier, not a different colour");

		HudLayout.Frame lyLow = placeFrame(DeckSkin.BUILT_IN, lyTrack, 200, 640, 300);
		LyricsLayout.Frame lyFlipped = LyricsLayout.frame(lyRuler, lySample, 12.0, lyLow, 640, 300);
		expect(lyFlipped.y() + lyFlipped.height() == lyLow.y() - LyricsLayout.GAP,
			"a card near the bottom of the screen gets its lyrics above it, not off the edge");
		HudLayout.Frame lyTight = placeFrame(DeckSkin.BUILT_IN, lyTrack, 20, 640, 120);
		LyricsLayout.Frame lySqueezed = LyricsLayout.frame(lyRuler, lySample, 12.0, lyTight, 640, 120);
		expect(lyTight.y() + lyTight.height() + LyricsLayout.GAP + LyricsLayout.height() > 120
			&& lyTight.y() - LyricsLayout.GAP - LyricsLayout.height() < 0,
			"the fixture card leaves room on neither side, so the panel has to cover it");
		expect(lySqueezed.y() >= 0 && lySqueezed.y() + lySqueezed.height() <= 120,
			"a window too short for both still keeps the panel on screen, got " + lySqueezed.y());
		// With room on neither side the panel must cover something, so it covers the emptier
		// half. Two cards a line apart on one screen put it at opposite ends, which is what
		// says the choice is made from the room rather than from a fixed side.
		expect(lySqueezed.y() == 120 - lySqueezed.height(),
			"a squeezed panel takes the side with more room, here the bottom, got " + lySqueezed.y());
		HudLayout.Frame lyHigh = placeFrame(DeckSkin.BUILT_IN, lyTrack, 28, 640, 120);
		LyricsLayout.Frame lyOverlap = LyricsLayout.frame(lyRuler, lySample, 12.0, lyHigh, 640, 120);
		expect(lyHigh.y() > 120 - (lyHigh.y() + lyHigh.height())
			&& lyOverlap.y() == 0,
			"and the other end when that is the emptier one, got " + lyOverlap.y());
		HudLayout.Frame lyRight = placeFrame(DeckSkin.BUILT_IN, lyTrack, 634, 640, 360);
		LyricsLayout.Frame lyRightPanel = LyricsLayout.frame(lyRuler, lySample, 12.0, lyRight, 640, 360);
		expect(lyRight.mirror(), "the fixture card really is parked against the right edge");
		expect(lyRightPanel.x() + lyRightPanel.width() == lyRight.x() + lyRight.width()
			&& lyRightPanel.x() >= 0 && lyRightPanel.x() + lyRightPanel.width() <= 640,
			"a right-parked card's lyrics align to the same edge the card reads from");

		// A narrow panel is the same width as the card, so it says nothing about which edge
		// the two were aligned on. This one is wider than the card and the card is not flush
		// with the screen, so aligning to the left edge instead puts the panel off the
		// screen's edge rather than the card's.
		Lyrics lyLong = Lyrics.parse("[00:01.00]wide words that push this panel past the card");
		HudLayout.Frame lyParked = placeFrame(DeckSkin.BUILT_IN, lyTrack, 440, 640, 360);
		LyricsLayout.Frame lyOverhang = LyricsLayout.frame(lyRuler, lyLong, 12.0, lyParked, 640, 360);
		expect(lyParked.mirror() && lyOverhang.width() > lyParked.width(),
			"the fixture widens the panel past a card that is not flush with the screen, got "
				+ lyOverhang.width() + " against " + lyParked.width());
		expect(lyOverhang.x() < lyParked.x()
			&& lyOverhang.x() + lyOverhang.width() == lyParked.x() + lyParked.width(),
			"a right-parked card's words grow away from the screen edge, so they end where the card ends");

		// --- the words are their own object: wherever the player drops them, however big ---
		LyricsLayout.Frame lyHandX = LyricsLayout.frame(lyRuler, lySample, 12.0, lyCard,
			new LyricsLayout.Options(120, LyricsLayout.AUTO, LyricsLayout.AUTO, LyricsLayout.AUTO,
				LyricsLayout.LINES), 640, 360);
		expect(lyHandX.x() == 120,
			"a panel dragged sideways is drawn where it was dropped, got " + lyHandX.x());
		LyricsLayout.Frame lyHandY = LyricsLayout.frame(lyRuler, lySample, 12.0, lyCard,
			new LyricsLayout.Options(LyricsLayout.AUTO, 40, LyricsLayout.AUTO, LyricsLayout.AUTO,
				LyricsLayout.LINES), 640, 360);
		expect(lyHandY.y() == 40,
			"and a panel dragged up is no longer under the card, got " + lyHandY.y());

		LyricsLayout.Frame lySized = LyricsLayout.frame(lyRuler, lySample, 12.0, lyCard,
			new LyricsLayout.Options(120, 40, 200, 75, LyricsLayout.LINES), 640, 360);
		expect(lySized.width() == 200,
			"a width the player set is the width that is drawn, got " + lySized.width());
		expect(lySized.height() == 75 && lySized.rows() == 6,
			"a taller box buys more rows rather than stretching three, got " + lySized.rows()
				+ " in " + lySized.height());
		int lyTopSlack = lySized.lineY(0) - lySized.y();
		int lyBottomSlack = lySized.y() + lySized.height() - (lySized.lineY(lySized.rows() - 1) + LyricsLayout.LINE);
		expect(Math.abs(lyTopSlack - lyBottomSlack) <= 1,
			"the rows are centred in a taller box, got " + lyTopSlack + " above and " + lyBottomSlack + " below");
		LyricsLayout.Frame lyMore = LyricsLayout.frame(lyRuler, lySample, 12.0, lyCard,
			new LyricsLayout.Options(120, 40, 200, LyricsLayout.AUTO, 5), 640, 360);
		expect(lyMore.rows() == 5 && lyMore.height() == LyricsLayout.height(5),
			"the line count is the tallest window an automatic height gives, got " + lyMore.rows());

		LyricsLayout.Frame lyCrammed = LyricsLayout.frame(lyRuler, lySample, 12.0, lyCard,
			new LyricsLayout.Options(600, 340, 200, 100, LyricsLayout.LINES), 640, 360);
		expect(lyCrammed.x() == 640 - lyCrammed.width() && lyCrammed.x() + lyCrammed.width() == 640,
			"a box dragged past the right edge is pulled back on screen, got " + lyCrammed.x());
		expect(lyCrammed.y() == 360 - lyCrammed.height() && lyCrammed.y() + lyCrammed.height() == 360,
			"and one dragged past the bottom is pulled up, got " + lyCrammed.y());

		// The grip is a square in the corner: it shrinks with a box smaller than itself, so a
		// one-line panel still has a whole corner to grab, and only that corner resizes.
		LyricsLayout.Frame lyTiny = LyricsLayout.frame(lyRuler, lySample, 12.0, lyCard,
			new LyricsLayout.Options(0, 0, LyricsLayout.MIN_WIDTH, 5, LyricsLayout.LINES), 640, 360);
		expect(lyTiny.grip().w() == 5 && lyTiny.grip().h() == 5,
			"the grip shrinks to fit a box smaller than it is, got " + lyTiny.grip().w());
		expect(lyTiny.grip().x() + lyTiny.grip().w() == lyTiny.x() + lyTiny.width()
			&& lyTiny.grip().y() + lyTiny.grip().h() == lyTiny.y() + lyTiny.height(),
			"and sits in the bottom-right corner, the one the mouse drags");
		expect(!lyTiny.gripHolds(lyTiny.x() + lyTiny.width() / 2.0, lyTiny.y() + lyTiny.height() / 2.0)
			&& lyTiny.gripHolds(lyTiny.x() + lyTiny.width() - 1, lyTiny.y() + lyTiny.height() - 1),
			"only the corner resizes: the middle of the box is a drag");

		// --- the panel's own custom UI: band, alpha, and the editor's marks ---
		Recorder lyNoBand = new Recorder();
		LyricsLayout.draw(lyNoBand, lyPanel, Palette.GREEN, true, new LyricsLayout.Style(false, 0.0, false));
		boolean lyBandDrawn = lyNoBand.kind("fill").stream()
			.anyMatch(op -> op.x1() == lyPanel.x() + 1 && op.y1() == lyPanel.y() + 2);
		expect(!lyBandDrawn && lyNoBand.kind("text").size() == 3,
			"a panel switched off keeps the words and drops the band, got "
				+ lyNoBand.kind("text").size() + " lines");
		Recorder lyHalf = new Recorder();
		LyricsLayout.draw(lyHalf, lyPanel, Palette.GREEN, true, new LyricsLayout.Style(true, 0.5, false));
		expect(alphaOf(lyricBand(lyHalf, lyPanel).colour()) == 128,
			"an opacity set by hand is the alpha that is drawn, got "
				+ alphaOf(lyricBand(lyHalf, lyPanel).colour()));
		Recorder lyFaint = new Recorder();
		LyricsLayout.draw(lyFaint, lyPanel, Palette.GREEN, true, new LyricsLayout.Style(true, 0.01, false));
		expect(alphaOf(lyricBand(lyFaint, lyPanel).colour()) == Math.round(255 * LyricsLayout.MIN_OPACITY),
			"and never below the floor that keeps the words on their band, got "
				+ alphaOf(lyricBand(lyFaint, lyPanel).colour()));
		Recorder lyEdit = new Recorder();
		LyricsLayout.draw(lyEdit, lyPanel, Palette.GREEN, true, new LyricsLayout.Style(true, 0.0, true));
		long lyDots = lyEdit.kind("fill").stream()
			.filter(op -> op.x2() - op.x1() == 2 && op.y2() - op.y1() == 2)
			.count();
		expect(lyDots == 3, "the editor draws the three grip dots the mouse grabs, got " + lyDots);
		long lyPlainDots = lyFull.kind("fill").stream()
			.filter(op -> op.x2() - op.x1() == 2 && op.y2() - op.y1() == 2)
			.count();
		expect(lyPlainDots == 0, "and an ordinary panel carries no editor marks, got " + lyPlainDots);

		Ink lyInk = new Ink();
		LyricsLayout.draw(lyInk, lyPanel, Palette.GREEN, true, new LyricsLayout.Style(true, 0.0, false));
		expect(lyInk.shadows.size() == 3 && lyInk.shadows.get(0)
			&& !lyInk.shadows.get(1) && !lyInk.shadows.get(2),
			"on a band only the line being sung takes a shadow, got " + lyInk.shadows);
		Ink lyBare = new Ink();
		LyricsLayout.draw(lyBare, lyPanel, Palette.GREEN, true, new LyricsLayout.Style(false, 0.0, false));
		expect(lyBare.shadows.size() == 3 && !lyBare.shadows.contains(Boolean.FALSE),
			"with the band gone every line takes one, so a dim neighbour stays readable, got " + lyBare.shadows);

		// A deck is the custom UI of the card, and the words are part of the card: the band
		// under them is painted from the same deck's colour, not from the shipped one.
		DeckSkin lyDeck = gateDeck("{\"panel\":\"#FF112233\"}");
		HudLayout.Frame lyDeckCard = deckFrame(lyDeck, lyTrack, 640, 360);
		LyricsLayout.Frame lyDeckPanel = LyricsLayout.frame(lyRuler, lySample, 12.0, lyDeckCard, 640, 360);
		Recorder lyDeckPaint = new Recorder();
		LyricsLayout.draw(lyDeckPaint, lyDeckPanel, Palette.GREEN, true);
		expect(lyDeckPanel.skin() == lyDeck && lyricBand(lyDeckPaint, lyDeckPanel).colour() == lyDeck.panel(),
			"the words wear the deck the card is wearing, got "
				+ Integer.toHexString(lyricBand(lyDeckPaint, lyDeckPanel).colour()));

		String lyHud = codeOnly(CLIENT_DIR + "NowPlayingHud.java");
		expect(count(lyHud, "LyricsLayout.draw(") == 1, "the card paints the lyric panel it measured");
		expect(lyHud.contains("lyrics.of(MediaConfig.lyrics(), playing.artist(), playing.title(),\n\t\t\tconfig.onlineLyrics, playing.duration());"),
			"and asks for the words of the track it is drawing, with the online switch and the length");
		expect(!lyHud.contains("HttpClient") && !lyHud.contains("LyricsFetcher"),
			"the draw path owns no socket: a lookup is the cache's business, never the frame's");
		String lyCommand = codeOnly(CLIENT_DIR + "MusicDeckCommand.java");
		expect(lyCommand.contains("literal(\"lyrics\")"), "the lyrics folder is reachable from the command");
		expect(lyCommand.contains("LyricsLibrary.find("),
			"and the command reports the same file the card reads");
		expect(lyCommand.contains("online(client, context, true)") && lyCommand.contains("online(client, context, false)"),
			"the online lookup can be turned on and off from the command");
		String lyConfig = codeOnly(CLIENT_DIR + "MediaConfig.java");
		expect(lyConfig.contains("LyricsLibrary.seedMissing(lyrics())"),
			"the lyrics folder is seeded with its example the way the deck folder is");
		expect(lyConfig.contains("Boolean.parseBoolean(props.getProperty(\"onlineLyrics\", \"true\"))")
			&& lyConfig.contains("props.setProperty(\"onlineLyrics\""),
			"the online switch is stored in musicdeck.properties and is on for a fresh install");

		// --- the placement editor answers both boxes, and the lyric knobs persist ---
		String lyLayoutScreen = codeOnly(CLIENT_DIR + "LayoutScreen.java");
		expect(lyLayoutScreen.contains("panel.gripHolds(mx, my)")
			&& lyLayoutScreen.indexOf("panel.gripHolds(mx, my)") < lyLayoutScreen.indexOf("panel.holds(mx, my)"),
			"the move screen picks the grip before the box, so its corner resizes instead of dragging");
		expect(count(lyLayoutScreen, "config.lyricsX = Integer.toString(panel.x());") >= 2
			&& count(lyLayoutScreen, "config.lyricsY = Integer.toString(panel.y());") >= 2
			&& count(lyLayoutScreen, "config.lyricsWidth = Integer.toString(clampWidth(") >= 2,
			"every resize pins the box it grew from, so an attached panel cannot slip out from under the pointer");
		expect(lyLayoutScreen.contains("config.lyricsX = homeLyricsX;")
			&& lyLayoutScreen.contains("config.lyricsY = homeLyricsY;")
			&& lyLayoutScreen.contains("config.lyricsWidth = homeLyricsWidth;")
			&& lyLayoutScreen.contains("config.lyricsHeight = homeLyricsHeight;"),
			"ESC puts back the words' box as well as the card's place");

		expect(java.util.regex.Pattern.compile("new LyricsLayout\\.Options\\(config\\.lyricsX\\(\\), config\\.lyricsY\\(\\), "
			+ "config\\.lyricsWidth\\(\\),\\s*config\\.lyricsHeight\\(\\), config\\.lyricsLines\\(\\)\\)")
			.matcher(lyHud).find(),
			"the card asks the layout for the box the player set, on every frame");
		expect(lyHud.contains("new LyricsLayout.Style(config.lyricsPanel, config.lyricsOpacity(), editing())"),
			"and paints it with the panel switches the custom-UI page writes");
		expect(lyHud.contains("if (!words.present() && editing())"),
			"the move screen has words to drag even while nothing is playing, and only then");
		expect(lyHud.contains("this.words = panel;"),
			"the frame the editor grabs is the frame that was painted");

		String lyVerbs = codeOnly(CLIENT_DIR + "MusicDeckCommand.java");
		expect(lyVerbs.contains("literal(\"place\")") && lyVerbs.contains("lyricPlace(client, context)"),
			"the command can put the words at a point");
		expect(lyVerbs.contains("literal(\"size\")") && lyVerbs.contains("lyricSize(client, context)"),
			"and give them a width and a height");
		expect(lyVerbs.contains("literal(\"reset\")") && lyVerbs.contains("lyricReset(client, context)"),
			"and put every lyric setting back to auto");

		expect(lyConfig.contains("props.getProperty(\"lyricsX\", AUTO)")
			&& lyConfig.contains("props.setProperty(\"lyricsX\"")
			&& lyConfig.contains("props.getProperty(\"lyricsWidth\", AUTO)")
			&& lyConfig.contains("props.setProperty(\"lyricsWidth\"")
			&& lyConfig.contains("props.getProperty(\"lyricsHeight\", AUTO)")
			&& lyConfig.contains("props.setProperty(\"lyricsHeight\"")
			&& lyConfig.contains("props.getProperty(\"lyricsPanel\", \"true\")")
			&& lyConfig.contains("props.setProperty(\"lyricsPanel\"")
			&& lyConfig.contains("props.getProperty(\"lyricsOpacity\", AUTO)")
			&& lyConfig.contains("props.setProperty(\"lyricsOpacity\""),
			"every lyric knob survives a restart: each is read and written in musicdeck.properties");
		expect(lyConfig.contains("AUTO.equalsIgnoreCase(value)")
			&& lyConfig.contains("return LyricsLayout.AUTO;"),
			"auto is a word in the file, and anything unreadable follows the card rather than landing at zero");
		expect(lyConfig.contains("raw.endsWith(\"%\")") && lyConfig.contains("Math.max(LyricsLayout.MIN_OPACITY"),
			"an opacity is a percentage with or without its sign, and never below the floor the layout paints");

		// --- the online lookup, which is the mod's only network traffic ---
		List<String> lyUrls = LyricsFetcher.urls("Brent Faiyaz", "Trust", 158.4);
		expect(lyUrls.size() == 2
			&& lyUrls.get(0).equals("https://lrclib.net/api/get?artist_name=Brent%20Faiyaz&track_name=Trust&duration=158"),
			"the lookup names the track and its length, got " + lyUrls);
		expect(lyUrls.get(1).endsWith("&track_name=Trust") && !lyUrls.get(1).contains("duration"),
			"and retries without the length, because a remaster can be a second off");
		expect(LyricsFetcher.urls("A", "B", 0.0).size() == 1
			&& !LyricsFetcher.urls("A", "B", 0.0).get(0).contains("duration"),
			"a track with no known length is asked for without one");
		expect(LyricsFetcher.query("AC/DC & Friends + 100%")
			.equals("AC%2FDC%20%26%20Friends%20%2B%20100%25"),
			"every character that could split the query is escaped, got " + LyricsFetcher.query("AC/DC & Friends + 100%"));
		expect(LyricsFetcher.query("Caf\u00e9").equals("Caf%C3%A9"),
			"an accented name goes out as UTF-8 bytes rather than a question mark");
		expect(LyricsFetcher.synced("{\"instrumental\":false,\"syncedLyrics\":\"[00:01.00]a\\n[00:02.00]b\"}")
			.equals("[00:01.00]a\n[00:02.00]b"),
			"the synced words are read out of the answer with their newlines intact");
		expect(LyricsFetcher.synced("{\"syncedLyrics\":null}").isEmpty()
			&& LyricsFetcher.synced("{\"instrumental\":true,\"syncedLyrics\":\"[00:01.00]a\"}").isEmpty()
			&& LyricsFetcher.synced("not json").isEmpty(),
			"no words, an instrumental and a broken answer are all nothing to draw");

		String lyName = LyricsFetcher.fileName("AC/DC", "Back: In Black?");
		expect(lyName.endsWith(".lrc") && !lyName.matches(".*[\\\\/:*?\"<>|].*"),
			"a fetched file's name carries no character Windows forbids, got " + lyName);
		expect(LyricsLibrary.key(lyName.substring(0, lyName.length() - 4))
			.equals(LyricsLibrary.key("AC/DC - Back: In Black?")),
			"and still keys to the track it belongs to, so the file is found on the next play");
		expect(LyricsFetcher.fileName("A", "Title.").equals("A - Title.lrc"),
			"a trailing dot, which Windows silently drops, is dropped here too");
		expect(LyricsFetcher.fileName("", "").equals("lyrics.lrc")
			&& LyricsFetcher.fileName("", "Only A Title").equals("Only A Title.lrc"),
			"an empty name still makes a usable file, and a title with no artist keeps its own name");
		expect(LyricsFetcher.fileName("Artist", "x".repeat(400)).length() <= 100,
			"a very long title is cut rather than making a path Windows refuses");

		java.nio.file.Path lyFetchDir = java.nio.file.Files.createTempDirectory("minetunes-fetch");
		Lyrics lyFetched = LyricsFetcher.save(lyFetchDir, "A", "T",
			"{\"syncedLyrics\":\"[00:01.00]a\\n[00:02.00]b\"}");
		java.nio.file.Path lyFetchedFile = lyFetchDir.resolve("A - T.lrc");
		expect(lyFetched.lines().size() == 2 && java.nio.file.Files.exists(lyFetchedFile)
			&& LyricsLibrary.read(lyFetchedFile).lines().size() == 2,
			"a hit is written into the folder and reads back through the same parser");
		// A folder of its own, because a file that already exists would hide whether the
		// refusal happened or the write was merely blocked by the no-clobber rule.
		java.nio.file.Path lyPlainDir = java.nio.file.Files.createTempDirectory("minetunes-plain");
		expect(!LyricsFetcher.save(lyPlainDir, "A", "T", "{\"syncedLyrics\":\"plain words only\"}").present()
			&& !java.nio.file.Files.exists(lyPlainDir.resolve("A - T.lrc")),
			"words without timestamps are neither drawn nor saved as a file the panel cannot use");
		java.nio.file.Files.writeString(lyFetchedFile, "[00:09.00]mine");
		LyricsFetcher.save(lyFetchDir, "A", "T", "{\"syncedLyrics\":\"[00:01.00]theirs\"}");
		expect(LyricsLibrary.read(lyFetchedFile).at(0).text().equals("mine"),
			"a fetched file never overwrites one that is already there");

		java.nio.file.Path lyOnlineDir = java.nio.file.Files.createTempDirectory("minetunes-online");
		CountingFetcher lyOff = new CountingFetcher();
		LyricsCache lyOffCache = new LyricsCache(lyOff);
		expect(!lyOffCache.of(lyOnlineDir, "A", "T", false, 30.0).present() && lyOff.calls == 0,
			"online off is no request at all, not a request thrown away");
		CountingFetcher lyOn = new CountingFetcher();
		lyOn.answer = Lyrics.parse("[00:01.00]a\n[00:02.00]b");
		LyricsCache lyOnCache = new LyricsCache(lyOn);
		expect(lyOnCache.of(lyOnlineDir, "A", "T", true, 30.0).lines().size() == 2,
			"a miss with online on shows what the lookup came back with");
		expect(lyOn.calls == 1 && Math.abs(lyOn.lastDuration - 30.0) < 0.001,
			"one request per track, carrying the length the card knows");
		expect(lyOnCache.of(lyOnlineDir, "A", "T", true, 30.0).lines().size() == 2 && lyOn.calls == 1,
			"and sixty frames later it is still the same single request");
		// The folder is rechecked once a second, so a request that is only kept by that window
		// is requested again the moment it lapses. Step past it before asking a second question.
		Thread.sleep(1100L);
		expect(lyOnCache.of(lyOnlineDir, "A", "T", true, 30.0).lines().size() == 2 && lyOn.calls == 1,
			"and a second later it is still that request, not a fresh one every frame");
		// That answer just reset the same window, so step past it again: turning the switch off
		// must be seen by the branch that decides, not short-circuited by the recheck timer.
		Thread.sleep(1100L);
		expect(lyOnCache.of(lyOnlineDir, "A", "T", false, 30.0).lines().size() == 2,
			"words that already arrived stay on screen after the switch goes off");
		lyOnCache.of(lyOnlineDir, "C", "T", false, 30.0);
		expect(lyOn.calls == 1, "and nothing new is asked for while it is off");
		lyOnCache.of(lyOnlineDir, "A", "U", true, 30.0);
		expect(lyOn.calls == 2, "a different track is a different lookup");
		CountingFetcher lyBroken = new CountingFetcher();
		lyBroken.failure = new IllegalStateException("no network");
		LyricsCache lyBrokenCache = new LyricsCache(lyBroken);
		expect(!lyBrokenCache.of(lyOnlineDir, "B", "T", true, 30.0).present(),
			"a lookup that fails is no lyrics rather than a crash");
		lyBrokenCache.of(lyOnlineDir, "B", "T", true, 30.0);
		Thread.sleep(1100L);
		lyBrokenCache.of(lyOnlineDir, "B", "T", true, 30.0);
		expect(lyBroken.calls == 1, "and a failure is not retried every frame");

		String lyFetch = codeOnly(CLIENT_DIR + "LyricsFetcher.java");
		expect(lyFetch.contains("sendAsync(") && !lyFetch.contains(".send(") && !lyFetch.contains(".join("),
			"the request never blocks the caller");
		expect(lyFetch.contains("timeout(TIMEOUT)") && lyFetch.contains("connectTimeout(TIMEOUT)"),
			"a request that hangs is abandoned instead of holding the answer forever");
		expect(lyFetch.contains("header(\"User-Agent\", AGENT)") && lyFetch.contains("MineTunes"),
			"the service is told which program is asking");
		expect(lyFetch.contains("https://lrclib.net"), "and the only host in the file is lrclib.net");

		for (java.nio.file.Path lyTemp : List.of(lyFolder, lyLoose, lyTitle, lyUtf8, lyLegacy, lySeed,
			lyCacheDir, lyFetchDir, lyPlainDir, lyOnlineDir)) {
			deleteTree(lyTemp);
		}

		System.out.println(failed == 0 ? "ALL " + passed + " CHECKS PASSED" : failed + " OF "
			+ (passed + failed) + " CHECKS FAILED");
		System.exit(failed == 0 ? 0 : 1);
	}

	/**
	 * A fetcher that answers from a field instead of a socket.
	 *
	 * The cache's rules - a file beats the network, one lookup per track, a failure is no
	 * lyrics - are the part that can go wrong, and they are all answerable without a
	 * network if the answer is handed in.
	 */
	static final class CountingFetcher extends LyricsFetcher {
		int calls;
		double lastDuration = -1.0;
		Lyrics answer = Lyrics.EMPTY;
		RuntimeException failure;

		@Override
		public java.util.concurrent.CompletableFuture<Lyrics> request(java.nio.file.Path directory,
			String artist, String title, double duration) {
			calls++;
			lastDuration = duration;
			if (failure != null) {
				return java.util.concurrent.CompletableFuture.failedFuture(failure);
			}

			return java.util.concurrent.CompletableFuture.completedFuture(answer);
		}
	}
}
