package com.musicdeck.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads the flat JSON objects the SMTC bridge prints, one per line.
 *
 * A real JSON library is not worth shipping in a mod jar: the producer is our own
 * script, every object is one level deep, and the only structural features in play
 * are escaped strings and bare literals. A {@code null} value is dropped, which is
 * what the bridge means by "this player does not report it".
 */
public final class JsonLine {
	private final Map<String, String> values = new HashMap<>();

	private JsonLine() {
	}

	public static JsonLine parse(String text) {
		JsonLine line = new JsonLine();
		int cursor = text.indexOf('{');
		int end = text.lastIndexOf('}');
		if (cursor < 0 || end <= cursor) {
			return line;
		}

		cursor++;
		while (cursor < end) {
			int keyOpen = text.indexOf('"', cursor);
			int keyClose = keyOpen < 0 ? -1 : closingQuote(text, keyOpen + 1);
			if (keyClose < 0 || keyClose > end) {
				break;
			}

			int colon = text.indexOf(':', keyClose + 1);
			if (colon < 0 || colon > end) {
				break;
			}

			String key = unescape(text.substring(keyOpen + 1, keyClose));
			int value = skipSpaces(text, colon + 1, end);
			if (value >= end) {
				break;
			}

			String raw;
			if (text.charAt(value) == '"') {
				int valueClose = closingQuote(text, value + 1);
				if (valueClose < 0 || valueClose > end) {
					break;
				}

				raw = unescape(text.substring(value + 1, valueClose));
				cursor = valueClose + 1;
			} else {
				int valueEnd = value;
				while (valueEnd < end && text.charAt(valueEnd) != ',' && text.charAt(valueEnd) != '}') {
					valueEnd++;
				}

				raw = text.substring(value, valueEnd).trim();
				cursor = valueEnd;
			}

			if (!raw.equals("null")) {
				line.values.put(key, raw);
			}
		}

		return line;
	}

	public String string(String key, String fallback) {
		return values.getOrDefault(key, fallback);
	}

	public double number(String key, double fallback) {
		String raw = values.get(key);
		if (raw == null) {
			return fallback;
		}

		try {
			return Double.parseDouble(raw);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public boolean bool(String key, boolean fallback) {
		String raw = values.get(key);
		return raw == null ? fallback : raw.equals("true");
	}

	public boolean has(String key) {
		return values.containsKey(key);
	}

	/**
	 * Step over whatever separates a colon from its value.
	 *
	 * The bridge prints one flat object per line with plain spaces, but a hand-written deck
	 * file is pretty-printed and can put the value on the next line after the key.
	 */
	private static int skipSpaces(String text, int from, int end) {
		while (from < end && Character.isWhitespace(text.charAt(from))) {
			from++;
		}

		return from;
	}

	/** Index of the closing quote for a string that opens at {@code from}, honouring backslash escapes. */
	private static int closingQuote(String text, int from) {
		for (int i = from; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\\') {
				i++;
			} else if (c == '"') {
				return i;
			}
		}

		return -1;
	}

	private static String unescape(String raw) {
		if (raw.indexOf('\\') < 0) {
			return raw;
		}

		StringBuilder out = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c != '\\' || i + 1 >= raw.length()) {
				out.append(c);
				continue;
			}

			char next = raw.charAt(++i);
			switch (next) {
				case 'n' -> out.append('\n');
				case 'r' -> out.append('\r');
				case 't' -> out.append('\t');
				case 'b' -> out.append('\b');
				case 'f' -> out.append('\f');
				case 'u' -> {
					if (i + 4 < raw.length()) {
						out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
						i += 4;
					}
				}
				default -> out.append(next);
			}
		}

		return out.toString();
	}
}
