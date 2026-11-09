package com.musicdeck.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The lyrics for the track that is playing, resolved once rather than every frame.
 *
 * The card asks sixty times a second, and listing a folder and parsing a file that often
 * is how a cosmetic feature lands in the frame budget. So the answer is kept until the
 * track changes, and after that only re-read when the file itself has moved on - which is
 * what lets a player fix a mistyped line and see it without restarting the game.
 *
 * A file the player wrote always wins over the network: it is the only way to fix timing,
 * and a track with a file must never quietly start showing someone else's transcription.
 * Only a miss is offered to {@link LyricsFetcher}, once per track, and the answer is kept
 * in memory so the draw path never waits for a socket.
 */
public final class LyricsCache {
	/** How often the folder is asked again, which is often enough to feel live and cheap. */
	private static final long RECHECK_MILLIS = 1000L;

	private final LyricsFetcher fetcher;
	private String key = "";
	private Path file;
	private long stamp;
	private long checkedAt;
	private Lyrics lyrics = Lyrics.EMPTY;
	/** The track a lookup was made for, so a miss is not asked again sixty times a second. */
	private String asked = "";
	private CompletableFuture<Lyrics> pending;
	private Lyrics fetched = Lyrics.EMPTY;

	public LyricsCache() {
		this(new LyricsFetcher());
	}

	public LyricsCache(LyricsFetcher fetcher) {
		this.fetcher = fetcher;
	}

	/**
	 * The lyrics for this track, or {@link Lyrics#EMPTY}.
	 *
	 * The track is named by both halves of what the player sees, because two different
	 * artists can own the same song title and a cache keyed on the title alone would hand
	 * one of them the other's words.
	 */
	public Lyrics of(Path directory, String artist, String title, boolean online, double duration) {
		long now = System.currentTimeMillis();
		String wanted = (title == null ? "" : title) + "|" + (artist == null ? "" : artist);
		if (!wanted.equals(key)) {
			key = wanted;
			file = null;
			stamp = 0L;
			checkedAt = 0L;
			lyrics = Lyrics.EMPTY;
			asked = "";
			pending = null;
			fetched = Lyrics.EMPTY;
		} else if (now - checkedAt < RECHECK_MILLIS) {
			return current();
		}

		checkedAt = now;
		Path found = LyricsLibrary.find(directory, artist, title);
		if (found == null) {
			file = null;
			stamp = 0L;
			lyrics = Lyrics.EMPTY;
			return online(directory, artist, title, online, duration);
		}

		// A file of the player's own is the whole answer; anything fetched for this track is
		// dropped rather than kept as a shadow copy the next change could resurrect.
		asked = "";
		pending = null;
		fetched = Lyrics.EMPTY;
		long modified = modifiedAt(found);
		if (found.equals(file) && modified == stamp) {
			return lyrics;
		}

		lyrics = LyricsLibrary.read(found);
		file = found;
		stamp = modified;
		return lyrics;
	}

	/** The file the current lyrics came from, or null. The command reports it. */
	public Path file() {
		return file;
	}

	private Lyrics online(Path directory, String artist, String title, boolean online, double duration) {
		// Switched off means no new request; words that already arrived are a file in the
		// folder by now, and taking them back would also have to mean deleting that file.
		if (!online) {
			return current();
		}

		String track = key;
		if (!track.equals(asked)) {
			asked = track;
			fetched = Lyrics.EMPTY;
			pending = fetcher.request(directory, artist, title, duration);
		}

		if (pending != null && pending.isDone()) {
			try {
				fetched = pending.join();
			} catch (CompletionException e) {
				fetched = Lyrics.EMPTY;
			}

			pending = null;
		}

		return fetched;
	}

	/** The answer without re-reading anything: a local file first, then whatever was fetched. */
	private Lyrics current() {
		return file != null ? lyrics : fetched;
	}

	private static long modifiedAt(Path file) {
		try {
			return Files.getLastModifiedTime(file).toMillis();
		} catch (IOException e) {
			return 0L;
		}
	}
}
