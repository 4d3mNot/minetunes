package com.musicdeck.client;

/**
 * Turns the helper's coarse, repeating position into a clock that only moves forward.
 *
 * Spotify reports its position as a whole second and repeats that same value on every
 * poll until it ticks. Taking each sample at face value restarted the extrapolation from
 * the old second three times a second, so the elapsed clock stuttered backwards and the
 * lyric sweep jumped back with it. This keeps one anchor per track and lets it run: a
 * sample that repeats the last one is the source not having ticked yet, not the track
 * going backwards, so it is ignored and the anchor keeps counting.
 *
 * A sample that moved is folded in as a correction rather than a reset, so the clock
 * converges on the truth without jumping. Only a change too large to be rounding - a
 * seek, or a scrub - is taken at face value.
 */
public final class MediaClock {
	/** A position change this big is a seek, not the source's rounding. */
	public static final double SNAP_SECONDS = 3.0;
	/** The share of a smaller difference folded in per sample, and the most seconds it may add. */
	public static final double ABSORB = 0.2;
	public static final double MAX_STEP = 0.12;

	private boolean anchored;
	private String track = "";
	private double shown;
	private long shownAt;
	private double lastRaw;

	public void reset() {
		anchored = false;
		track = "";
		shown = 0.0;
		shownAt = 0L;
		lastRaw = 0.0;
	}

	/**
	 * Fold one bridge sample in and hand back the snapshot the card should show.
	 *
	 * The returned snapshot carries the smoothed position and the sample's own arrival
	 * time, so {@link NowPlaying#position()} keeps counting from there between samples.
	 */
	public NowPlaying settle(NowPlaying raw) {
		if (!raw.known() || !raw.session() || !raw.hasTrack()) {
			reset();
			return raw;
		}

		long now = raw.receivedAt();
		double pos = raw.reportedPosition();
		String key = raw.title() + '\u0001' + raw.artist();
		if (!anchored || !key.equals(track)) {
			// A new track starts wherever it says it starts, even if that is backwards.
			track = key;
			anchored = true;
			lastRaw = pos;
			anchor(pos, now);
			return with(raw, pos);
		}

		if (!raw.playing()) {
			lastRaw = pos;
			anchor(pos, now);
			return with(raw, pos);
		}

		double running = shown + Math.max(0L, now - shownAt) / 1000.0;
		boolean moved = pos != lastRaw;
		lastRaw = pos;
		if (!moved) {
			anchor(running, now);
			return with(raw, running);
		}

		double error = pos - running;
		double next = Math.abs(error) > SNAP_SECONDS
			? pos
			: Math.max(shown, running + clamp(error * ABSORB, -MAX_STEP, MAX_STEP));
		anchor(next, now);
		return with(raw, next);
	}

	private void anchor(double position, long at) {
		shown = position;
		shownAt = at;
	}

	private static NowPlaying with(NowPlaying raw, double position) {
		double bounded = raw.duration() > 0.0 ? Math.min(position, raw.duration()) : position;
		return new NowPlaying(raw.session(), raw.source(), raw.status(), raw.title(), raw.artist(),
			raw.album(), Math.max(0.0, bounded), raw.duration(), raw.receivedAt(), raw.repeat());
	}

	private static double clamp(double value, double low, double high) {
		return Math.max(low, Math.min(high, value));
	}
}
