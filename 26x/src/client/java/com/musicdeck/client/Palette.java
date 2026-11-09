package com.musicdeck.client;

/**
 * Colour maths for the now-playing card, with no game types in sight.
 *
 * Everything here works on plain ARGB ints so the same code can be exercised by the
 * off-screen HUD preview as by the live overlay.
 */
public final class Palette {
	public static final int WHITE = 0xFFF4F4F4;
	public static final int GREY = 0xFF9A9A9A;
	public static final int GREEN = 0xFF1DB954;
	/**
	 * The colour modes the card understands. The config file stores exactly these strings,
	 * which is why they live next to the maths that reads them rather than in the loader.
	 */
	public static final String THEME_AUTO = "auto";
	public static final String THEME_GREEN = "green";
	public static final String THEME_WHITE = "white";

	private Palette() {
	}

	/**
	 * The colour the card is tinted with, once the player's theme has had its say.
	 *
	 * A sampled accent of 0 means there was no artwork to read from, so the automatic
	 * theme hands back the caller's brand colour instead of painting the border black.
	 */
	public static int themed(String theme, int sampled, int fallback) {
		if (THEME_GREEN.equals(theme)) {
			return GREEN;
		}

		if (THEME_WHITE.equals(theme)) {
			return WHITE;
		}

		return sampled != 0 ? sampled : fallback;
	}

	/**
	 * The colour the card is tinted with, taken from the artwork itself.
	 *
	 * A hue vote rather than an average, because averaging is exactly what made the card
	 * read as mud: a cover that is red with a teal band is *red*, and the mean of its
	 * pixels is a brown that names neither half. Every pixel with any colour in it votes
	 * for one of twelve hue buckets, weighted by how much colour it has and by how far it
	 * sits from crushed or blown out. The winning bucket then averages only its own
	 * pixels, so the second hue on a cover loses the vote instead of contaminating the
	 * colour it won.
	 *
	 * Two rungs below that handle the covers a vote cannot read. A greyscale record has no
	 * hue at all, so its lightest pixels average into a silver accent; and a black sleeve,
	 * or a thumbnail that never arrived, has neither, and hands the decision back to the
	 * caller's brand colour.
	 */
	public static int accent(int[] argb, int fallback) {
		double[] votes = new double[HUES];
		double[] voteRed = new double[HUES];
		double[] voteGreen = new double[HUES];
		double[] voteBlue = new double[HUES];
		int[] voters = new int[HUES];
		long neutralRed = 0;
		long neutralGreen = 0;
		long neutralBlue = 0;
		int neutral = 0;

		for (int pixel : argb) {
			// Half-and-up rather than nearly-opaque: Spotify's own thumbnail is one flat
			// 78% alpha, and a gate at 200 would discard the entire cover and quietly fall
			// back to the brand colour on every track.
			if (((pixel >>> 24) & 0xFF) < 128) {
				continue;
			}

			int red = (pixel >> 16) & 0xFF;
			int green = (pixel >> 8) & 0xFF;
			int blue = pixel & 0xFF;
			int max = Math.max(red, Math.max(green, blue));
			int min = Math.min(red, Math.min(green, blue));
			int chroma = max - min;
			if (chroma >= MIN_CHROMA && max >= MIN_VALUE) {
				// Weight is colour times position: a pixel that is grey, or one crushed into
				// black or burned to white, carries no hue worth trusting.
				double strength = (chroma / 255.0) * (1.0 - Math.abs(max / 255.0 - 0.6) / 0.6);
				int bucket = hueBucket(red, green, blue, max, chroma);
				votes[bucket] += strength;
				voteRed[bucket] += red * strength;
				voteGreen[bucket] += green * strength;
				voteBlue[bucket] += blue * strength;
				voters[bucket]++;
			} else if (chroma < MIN_NEUTRAL_CHROMA && max > 110) {
				neutralRed += red;
				neutralGreen += green;
				neutralBlue += blue;
				neutral++;
			}
		}

		int best = 0;
		for (int bucket = 1; bucket < HUES; bucket++) {
			if (votes[bucket] > votes[best]) {
				best = bucket;
			}
		}

		if (voters[best] >= MIN_VOTERS && votes[best] >= argb.length * MIN_SHARE) {
			return readable(tint(voteRed[best], voteGreen[best], voteBlue[best], votes[best]));
		}

		if (neutral * 12 >= Math.max(1, argb.length)) {
			return readable(average(neutralRed, neutralGreen, neutralBlue, neutral));
		}

		return fallback;
	}

	/**
	 * Which of the twelve hue buckets a coloured pixel belongs to.
	 *
	 * Degrees of hue, cut into thirty-degree slices: red at zero, green at a hundred and
	 * twenty, blue at two hundred and forty. The chroma has to be non-zero, which the
	 * caller guarantees.
	 */
	private static int hueBucket(int red, int green, int blue, int max, int chroma) {
		double hue;
		if (max == red) {
			hue = 60.0 * ((green - blue) / (double) chroma + (green < blue ? 6.0 : 0.0));
		} else if (max == green) {
			hue = 60.0 * ((blue - red) / (double) chroma + 2.0);
		} else {
			hue = 60.0 * ((red - green) / (double) chroma + 4.0);
		}

		int bucket = (int) (hue / (360.0 / HUES));
		return Math.max(0, Math.min(HUES - 1, bucket));
	}

	private static int tint(double red, double green, double blue, double weight) {
		return 0xFF000000 | clamp((int) Math.round(red / weight)) << 16
			| clamp((int) Math.round(green / weight)) << 8 | clamp((int) Math.round(blue / weight));
	}

	/** The twelve slices the hue wheel is cut into for the vote. */
	private static final int HUES = 12;
	/** Below this a pixel is grey whatever its lightness, so it has no hue to vote with. */
	private static final int MIN_CHROMA = 20;
	/** Below this the artwork is black with a tint in it, and no accent reads against the panel either. */
	private static final int MIN_VALUE = 35;
	/** Above this a light pixel is still grey, and belongs to the greyscale rung rather than the vote. */
	private static final int MIN_NEUTRAL_CHROMA = 8;
	/**
	 * How much colour the winning bucket needs before the card takes its colour.
	 *
	 * Counted in whole pixels at full strength, so it moves with the tile size: six voters
	 * is the floor that stops a JPEG artefact or one stray logo edge repainting the card,
	 * and one and a half per cent of the tile is where a real coloured region begins.
	 */
	private static final double MIN_SHARE = 0.015;
	private static final int MIN_VOTERS = 6;

	private static int average(long red, long green, long blue, int count) {
		return 0xFF000000 | (int) (red / count) << 16 | (int) (green / count) << 8 | (int) (blue / count);
	}

	/**
	 * Pushes a colour into the band where it still reads against a dark panel, without
	 * moving its hue. A sampled accent that averages to a muddy 40 is useless as an
	 * accent, and one at 255 burns out the text next to it.
	 */
	public static int readable(int argb) {
		int red = (argb >> 16) & 0xFF;
		int green = (argb >> 8) & 0xFF;
		int blue = argb & 0xFF;
		int max = Math.max(red, Math.max(green, blue));
		if (max == 0) {
			return GREEN;
		}

		double factor = 1.0;
		if (max < 150) {
			factor = 150.0 / max;
		} else if (max > 235) {
			factor = 235.0 / max;
		}

		return 0xFF000000 | clamp((int) Math.round(red * factor)) << 16
			| clamp((int) Math.round(green * factor)) << 8 | clamp((int) Math.round(blue * factor));
	}

	/** Multiplies the channels, keeps the alpha: how the card dims while paused. */
	public static int scale(int argb, double factor) {
		int alpha = (argb >>> 24) & 0xFF;
		int red = (int) Math.round(((argb >> 16) & 0xFF) * factor);
		int green = (int) Math.round(((argb >> 8) & 0xFF) * factor);
		int blue = (int) Math.round((argb & 0xFF) * factor);
		return alpha << 24 | clamp(red) << 16 | clamp(green) << 8 | clamp(blue);
	}

	public static int mix(int from, int to, double toward) {
		double t = Math.max(0.0, Math.min(1.0, toward));
		int alpha = (int) Math.round(((from >>> 24) & 0xFF) * (1.0 - t) + ((to >>> 24) & 0xFF) * t);
		int red = (int) Math.round(((from >> 16) & 0xFF) * (1.0 - t) + ((to >> 16) & 0xFF) * t);
		int green = (int) Math.round(((from >> 8) & 0xFF) * (1.0 - t) + ((to >> 8) & 0xFF) * t);
		int blue = (int) Math.round((from & 0xFF) * (1.0 - t) + (to & 0xFF) * t);
		return alpha << 24 | clamp(red) << 16 | clamp(green) << 8 | clamp(blue);
	}

	public static int withAlpha(int argb, int alpha) {
		return clamp(alpha) << 24 | argb & 0x00FFFFFF;
	}

	private static int clamp(int channel) {
		return Math.max(0, Math.min(255, channel));
	}
}
