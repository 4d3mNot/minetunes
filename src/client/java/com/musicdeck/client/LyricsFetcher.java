package com.musicdeck.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Asks lrclib.net for the words of the track that is playing.
 *
 * The request is the only network traffic this mod makes, it carries nothing but the
 * artist, the title and the length, and it never happens while the card is drawing: the
 * answer arrives on a worker thread and the frame reads whatever it has. A hit is written
 * into the lyrics folder, so the second play of a song is a local read and works offline.
 *
 * The response is read with {@link JsonLine}, which is all this file needs: the endpoint
 * returns one flat object and the field of interest, {@code syncedLyrics}, is already the
 * LRC text the panel parses.
 */
public class LyricsFetcher {
	public static final String HOST = "https://lrclib.net";
	/** lrclib asks for a descriptive agent; it also says which program to blame in a log. */
	public static final String AGENT = "MineTunes/1.1.0 (Fabric client mod; lyrics from lrclib.net)";
	private static final Duration TIMEOUT = Duration.ofSeconds(6);
	/** A lyrics file is a few kilobytes; anything this large is not a lyrics file. */
	private static final int MAX_BODY = 1 << 20;
	/** Long names are cut, not rejected: Windows refuses a path past 255 characters. */
	private static final int MAX_NAME = 96;

	private volatile HttpClient client;

	/**
	 * Ask for the words, without waiting for them.
	 *
	 * The future always succeeds: a missing song, a dead network and an unreadable answer
	 * all arrive as {@link Lyrics#EMPTY}, because a card that fails to draw is worse than a
	 * card without words. The result is also saved to {@code directory} first, so a later
	 * play of the same track never needs the network.
	 */
	public CompletableFuture<Lyrics> request(Path directory, String artist, String title, double duration) {
		if (title == null || title.isBlank()) {
			return CompletableFuture.completedFuture(Lyrics.EMPTY);
		}

		List<String> urls = urls(artist, title, duration);
		return get(urls, 0)
			.thenApply(body -> save(directory, artist, title, body))
			.exceptionally(failure -> Lyrics.EMPTY);
	}

	/**
	 * The addresses to try, in order.
	 *
	 * The first one pins the length, which is how a live take is told from the studio one.
	 * The last drops it, because an album and a remaster can be a second apart and a record
	 * that exists is worth more than a perfect measurement.
	 */
	public static List<String> urls(String artist, String title, double duration) {
		String base = HOST + "/api/get?artist_name=" + query(artist) + "&track_name=" + query(title);
		List<String> urls = new ArrayList<>(2);
		long seconds = Math.round(duration);
		if (seconds > 0L) {
			urls.add(base + "&duration=" + seconds);
		}

		urls.add(base);
		return urls;
	}

	/** Percent-encoding for a query value: UTF-8 bytes, and a space is %20, never '+'. */
	public static String query(String value) {
		String text = value == null ? "" : value;
		StringBuilder out = new StringBuilder(text.length() + 8);
		for (int at = 0; at < text.length(); ) {
			int point = text.codePointAt(at);
			at += Character.charCount(point);
			if ((point >= 'a' && point <= 'z') || (point >= 'A' && point <= 'Z')
				|| (point >= '0' && point <= '9') || point == '-' || point == '.' || point == '_' || point == '~') {
				out.appendCodePoint(point);
				continue;
			}

			for (byte bite : new String(Character.toChars(point)).getBytes(StandardCharsets.UTF_8)) {
				out.append('%')
					.append(Character.toUpperCase(Character.forDigit((bite >> 4) & 0xF, 16)))
					.append(Character.toUpperCase(Character.forDigit(bite & 0xF, 16)));
			}
		}

		return out.toString();
	}

	/**
	 * The LRC text inside a response body, or "" when there is none.
	 *
	 * An instrumental answer is a real answer - the service has the song and says there are
	 * no words - so it is nothing to draw, not something to retry.
	 */
	public static String synced(String body) {
		if (body == null || body.isEmpty()) {
			return "";
		}

		JsonLine line = JsonLine.parse(body);
		if (line.bool("instrumental", false)) {
			return "";
		}

		return line.string("syncedLyrics", "");
	}

	/**
	 * Parse a body and keep it in the folder. The parsing rules live with the file reader,
	 * so a fetched file and a hand-written one are read by exactly the same code. Public
	 * because the gate suite proves what a hit leaves behind.
	 */
	public static Lyrics save(Path directory, String artist, String title, String body) {
		String text = synced(body);
		if (text.isEmpty()) {
			return Lyrics.EMPTY;
		}

		Lyrics lyrics = Lyrics.parse(text);
		if (!lyrics.present()) {
			// Plain words with no stamps would paint a panel with no idea which line is sung.
			return Lyrics.EMPTY;
		}

		write(directory, fileName(artist, title), text);
		return lyrics;
	}

	/** The name a fetched file takes, which is the name the matcher will look for next time. */
	public static String fileName(String artist, String title) {
		String by = artist == null ? "" : artist.trim();
		String of = title == null ? "" : title.trim();
		String name = by.isEmpty() ? of : by + " - " + of;
		// Everything Windows forbids in a name, plus control characters, becomes a separator;
		// the matcher throws punctuation away, so "_" still lines up with the track's name.
		name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
		name = trimEnd(name);
		if (name.length() > MAX_NAME) {
			name = trimEnd(name.substring(0, MAX_NAME));
		}

		return (name.isEmpty() ? "lyrics" : name) + LyricsLibrary.SUFFIX;
	}

	private static String trimEnd(String name) {
		return name.replaceAll("[. ]+$", "");
	}

	private static void write(Path directory, String name, String text) {
		if (directory == null) {
			return;
		}

		Path target = directory.resolve(name);
		try {
			Files.createDirectories(directory);
			// Never over a file that is already there: the player's own timing wins.
			if (!Files.exists(target)) {
				Files.writeString(target, text, StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			// Not being able to keep the answer is not a reason to drop it for this play.
		}
	}

	private CompletableFuture<String> get(List<String> urls, int index) {
		if (index >= urls.size()) {
			return CompletableFuture.completedFuture("");
		}

		HttpRequest request = HttpRequest.newBuilder(URI.create(urls.get(index)))
			.timeout(TIMEOUT)
			.header("User-Agent", AGENT)
			.GET()
			.build();
		return http()
			.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
			.thenCompose(response -> {
				if (response.statusCode() == 200) {
					String body = response.body();
					return CompletableFuture.completedFuture(body != null && body.length() <= MAX_BODY ? body : "");
				}

				if (response.statusCode() == 404) {
					return get(urls, index + 1);
				}

				return CompletableFuture.completedFuture("");
			});
	}

	/** Built on the first real request, so the gate suite never opens a socket. */
	private HttpClient http() {
		HttpClient found = client;
		if (found == null) {
			synchronized (this) {
				found = client;
				if (found == null) {
					found = HttpClient.newBuilder()
						.connectTimeout(TIMEOUT)
						.followRedirects(HttpClient.Redirect.NORMAL)
						.build();
					client = found;
				}
			}
		}

		return found;
	}
}
