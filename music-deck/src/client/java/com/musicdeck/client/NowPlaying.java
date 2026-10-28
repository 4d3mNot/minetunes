package com.musicdeck.client;

/**
 * One snapshot of the Windows media session.
 *
 * The bridge polls about three times a second, so the position it reports is
 * already stale by up to that much. {@link #position()} extrapolates locally against
 * the wall clock instead, which is what keeps the progress bar moving smoothly.
 */
public record NowPlaying(boolean session, String source, String status, String title, String artist,
	String album, double reportedPosition, double duration, long receivedAt, String repeat) {

	public static final NowPlaying EMPTY = new NowPlaying(false, "", "", "", "", "", 0.0, 0.0, 0L, "");

	private static final double MAX_EXTRAPOLATION_SECONDS = 4.0;

	public static NowPlaying from(JsonLine line) {
		return new NowPlaying(line.bool("session", false),
			line.string("source", ""), line.string("status", ""), line.string("title", ""),
			line.string("artist", ""), line.string("album", ""),
			line.number("pos", 0.0), line.number("end", 0.0), System.currentTimeMillis(),
			line.string("repeat", ""));
	}

	public boolean known() {
		return receivedAt > 0L;
	}

	public boolean playing() {
		return status.equals("Playing");
	}

	public boolean spotify() {
		return source.toLowerCase().contains("spotify");
	}

	public double position() {
		if (!playing()) {
			return reportedPosition;
		}

		double elapsed = (System.currentTimeMillis() - receivedAt) / 1000.0;
		double moved = reportedPosition + Math.min(elapsed, MAX_EXTRAPOLATION_SECONDS);
		return duration > 0.0 ? Math.min(moved, duration) : moved;
	}

	public double fraction() {
		if (duration <= 0.0) {
			return 0.0;
		}

		return Math.max(0.0, Math.min(1.0, position() / duration));
	}

	public boolean hasTrack() {
		return !title.isEmpty() || !artist.isEmpty();
	}

	/** The card's first line: the track alone, because the artist owns the second. */
	public String headline() {
		return title.isEmpty() ? artist : title;
	}

	public String label(int maxChars) {
		// ASCII only, here and in the ellipsis below: the default font has an em dash, a
		// texture pack's font is not obliged to, and a missing glyph draws as a hole in
		// the middle of the track title.
		return cap(title.isEmpty() ? artist : (artist.isEmpty() ? title : title + " - " + artist), maxChars);
	}

	/** Trims to a character budget and marks the cut with dots. */
	public static String cap(String text, int maxChars) {
		if (maxChars <= 0 || text.length() <= maxChars) {
			return text;
		}

		return text.substring(0, Math.max(1, maxChars - 3)).trim() + "...";
	}

	public String progressText() {
		if (duration <= 0.0) {
			return "";
		}

		return clock(position()) + " / " + clock(duration);
	}

	/** Where the card's detail row puts it: only the elapsed clock, which is what fits. */
	public String elapsedText() {
		return duration <= 0.0 ? "" : clock(position());
	}

	private static String clock(double seconds) {
		long whole = (long) Math.max(0.0, seconds);
		return (whole / 60) + ":" + String.format("%02d", whole % 60);
	}
}
