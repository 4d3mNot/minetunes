package com.musicdeck.client;

/**
 * A deck: the card's look, as data.
 *
 * Community decks are plain JSON files dropped into {@code config/musicdeck/decks}. They are
 * read and never executed. A format that lets someone move the artwork above the text is a
 * theme; a format that lets them run code is a door for whoever posted the file, so every
 * knob here is a number, a colour or a switch, and every one of them is clamped on the way
 * in. A typo in a shared deck costs the author a default, not a card drawn off the edge of
 * the screen or 4,000 pixels wide.
 *
 * The built-in values are the constants {@link HudLayout} has always used, so a missing file
 * and an empty one both draw the card that shipped.
 */
public record DeckSkin(String name, String layout, String accent, int panel, int tile, int pad, int gap,
	int minWidth, int maxWidth, boolean detail, boolean time, boolean progress, boolean buttons,
	boolean border) {
	public static final String ROW = "row";
	public static final String STACK = "stack";
	public static final int MAX_TILE = 64;
	public static final int MAX_WIDTH = 260;
	private static final int MAX_ALPHA = 255;

	/** The card as it shipped: artwork beside text, everything switched on. */
	public static final DeckSkin BUILT_IN = new DeckSkin("Built in", ROW, "", 0xD80A0A0C, HudLayout.TILE,
		HudLayout.PAD, HudLayout.GAP, HudLayout.MIN_CONTENT, HudLayout.MAX_CONTENT,
		true, true, true, true, true);

	/** True when there is room for an artwork tile at all; a deck that asks for 0 is text-only. */
	public boolean hasArt() {
		return tile > 0;
	}

	public boolean stacked() {
		return STACK.equals(layout);
	}

	/**
	 * The colour this deck asks for, or {@code fallback} when it says auto or says it badly.
	 *
	 * A deck that pins its own accent does not want the cover's colour, so this is asked before
	 * the artwork is ever sampled.
	 */
	public int accentOr(int fallback) {
		Integer parsed = colour(accent);
		return parsed == null ? fallback : parsed.intValue();
	}

	public boolean pinsAccent() {
		return colour(accent) != null;
	}

	/**
	 * Read one deck file.
	 *
	 * Unknown keys are ignored rather than rejected: a deck written for a newer version of the
	 * mod should still draw, just without the knobs this build does not know. A file with no
	 * JSON object in it at all is the built-in card under the file's own name.
	 */
	public static DeckSkin parse(String fileName, String text) {
		JsonLine json = JsonLine.parse(text);
		String layout = json.string("layout", ROW).trim().toLowerCase();
		if (!STACK.equals(layout)) {
			layout = ROW;
		}

		String rawPanel = json.string("panel", "").trim();
		Integer parsedPanel = colour(rawPanel);
		int base = parsedPanel == null ? BUILT_IN.panel : parsedPanel.intValue();
		// An 8-digit colour carries its own alpha and means it; anything else gets the shipped
		// 85% unless the deck says otherwise, so naming a colour never blanks the panel.
		double defaultOpacity = alphaOf(parsedPanel != null && hasAlpha(rawPanel) ? base : BUILT_IN.panel)
			/ (double) MAX_ALPHA;
		int alpha = (int) Math.round(number(json, "opacity", defaultOpacity, 0.15, 1.0) * MAX_ALPHA);
		int panel = base & 0x00FFFFFF | alpha << 24;

		int tile = (int) Math.round(number(json, "tile", HudLayout.TILE, 0, MAX_TILE));
		int pad = (int) Math.round(number(json, "pad", HudLayout.PAD, 2, 10));
		int gap = (int) Math.round(number(json, "gap", HudLayout.GAP, 2, 12));
		int minWidth = (int) Math.round(number(json, "minWidth", HudLayout.MIN_CONTENT, 20, MAX_WIDTH));
		int maxWidth = (int) Math.round(number(json, "maxWidth", HudLayout.MAX_CONTENT, minWidth, MAX_WIDTH));
		String name = json.string("name", "").trim();

		return new DeckSkin(name.isEmpty() ? fileName : name, layout, json.string("accent", "").trim(),
			panel, tile, pad, gap, minWidth, maxWidth,
			json.bool("detail", true), json.bool("time", true), json.bool("progress", true),
			json.bool("buttons", true), json.bool("border", true));
	}

	private static boolean hasAlpha(String raw) {
		String value = raw.trim();
		return value.startsWith("#") ? value.length() == 9 : value.length() == 8;
	}

	private static double number(JsonLine json, String key, double fallback, double low, double high) {
		return Math.max(low, Math.min(high, json.number(key, fallback)));
	}

	/**
	 * {@code #rgb}, {@code #rrggbb}, {@code #aarrggbb} and the same without the hash.
	 *
	 * Null, not a number, when the text is none of those. An ARGB int has no spare value to
	 * fail with: every opaque colour has the top bit set, so the sign is a real channel and a
	 * negative sentinel would read as a colour on every deck that named one badly.
	 */
	static Integer colour(String raw) {
		String value = raw == null ? "" : raw.trim();
		if (value.startsWith("#")) {
			value = value.substring(1);
		}

		if (value.length() == 3) {
			value = "" + value.charAt(0) + value.charAt(0) + value.charAt(1) + value.charAt(1)
				+ value.charAt(2) + value.charAt(2);
		}

		if (value.length() != 6 && value.length() != 8) {
			return null;
		}

		try {
			int parsed = (int) Long.parseLong(value, 16);
			return value.length() == 6 ? parsed | 0xFF000000 : parsed;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static int alphaOf(int argb) {
		return argb >>> 24;
	}
}
