import com.musicdeck.client.DeckLibrary;
import com.musicdeck.client.DeckSkin;
import com.musicdeck.client.HudCanvas;
import com.musicdeck.client.HudLayout;
import com.musicdeck.client.JsonLine;
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

	static void expect(boolean condition, String what) {
		if (condition) {
			passed++;
			System.out.println("ok   " + what);
		} else {
			failed++;
			System.out.println("FAIL " + what);
		}
	}

	public static void main(String[] args) {
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
		for (String screen : new String[] {"LayoutScreen.java", "SettingsScreen.java", "KeyBindScreen.java"}) {
			String code = codeOnly(CLIENT_DIR + screen);
			expect(!code.contains("renderBackground("),
				screen + " asks for no second blur, which is what took the game down last build");
			expect(code.contains("super.render("), screen + " still draws the widgets it adds");
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
		expect(iconWide == iconTall && iconWide >= 64,
			"the icon is square and at least 64px, got " + iconWide + "x" + iconTall);
		expect(png.length > 0 && png.length <= 64 * 1024,
			"the icon is small enough to sit in every download of the jar, got " + png.length + " bytes");
		expect(java.nio.file.Files.exists(java.nio.file.Path.of("publish/musicdeck-icon.png"))
			&& java.nio.file.Files.exists(java.nio.file.Path.of("publish/musicdeck-banner.png")),
			"the full-size page art is kept with the project, so the release page can be rebuilt without regenerating it");

		String gradle = read("build.gradle");
		expect(gradle.contains("modCompileOnly \"maven.modrinth:modmenu:"),
			"Mod Menu is compile-only, so its intermediary names are remapped at build time and its classes stay out of the jar");
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
		expect(settings.contains("this.minecraft.setScreen(back)"),
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
		expect(binds.contains("InputConstants.Type.KEYSYM.getOrCreate(")
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
			.compile("(\\w+) = KeyBindingHelper\\.registerKeyBinding\\(new KeyMapping\\(\\s*\"(key\\.[^\"]+)\"")
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
		expect(client.contains("new SettingsScreen(config, minecraft.screen)"),
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
		expect(hud.contains("Minecraft.getInstance().screen instanceof LayoutScreen"),
			"the card asks the game whether the editor is open instead of remembering it");
		expect(!hud.contains("setLayout(") && !hud.contains("boolean layout"),
			"no sticky editing flag can outlive the editor and kill the transport buttons");
		expect(editor.contains("public void removed()"),
			"the editor saves on the way out through the hook setScreen actually calls");

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
		int afterDraw = measured.indexOf("private static int dim(");
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
		expect(count(hud, "graphics.guiWidth(), graphics.guiHeight()") == 1
			&& count(hud, "getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight()") == 1,
			"the card is measured against the live screen width, which is what tells it which side it is on");

		System.out.println(failed == 0 ? "ALL " + passed + " CHECKS PASSED" : failed + " OF "
			+ (passed + failed) + " CHECKS FAILED");
		System.exit(failed == 0 ? 0 : 1);
	}
}
