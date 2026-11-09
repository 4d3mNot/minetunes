package com.musicdeck.client;

import com.musicdeck.MusicDeck;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Owns the PowerShell child process that talks to Windows SMTC.
 *
 * The game side cannot call WinRT, so the mod ships {@code smtc-bridge.ps1} as a
 * resource, drops it next to the config, and speaks a two-file protocol with it:
 * NDJSON out of stdout, one command per file in {@code commands\}. A file channel
 * rather than stdin because reading the console from a second thread makes
 * powershell.exe exit with code 2 and print nothing at all.
 */
public final class MediaBridge {
	private static final String SCRIPT = "/musicdeck/smtc-bridge.ps1";
	private static final String SCRIPT_NAME = "smtc-bridge.ps1";
	/** Past this age the snapshot is too old for the HUD to trust. */
	public static final long STALE_MILLIS = 6_000L;
	private static final int MAX_RETRIES = 4;

	private final Path workDir;
	private final Path commandDir;
	private final Path logFile;
	private final Path artFile;
	private final Object lifecycle = new Object();
	private final AtomicLong sequence = new AtomicLong();

	private volatile NowPlaying state = NowPlaying.EMPTY;
	private volatile long stateAt;
	private volatile String lastAck = "";
	private volatile boolean unavailable;

	private Process process;
	private boolean stopping;
	private int retries;
	private long retryAt;

	public MediaBridge(Path gameDir) {
		this.workDir = gameDir.resolve("musicdeck");
		this.commandDir = workDir.resolve("commands");
		this.logFile = workDir.resolve("bridge.log");
		this.artFile = workDir.resolve("art.png");
	}

	public NowPlaying state() {
		return state;
	}

	/** Where the helper drops the album tile for {@link AlbumArt} to pick up. */
	public Path artFile() {
		return artFile;
	}

	public long stateAgeMillis() {
		return stateAt == 0L ? Long.MAX_VALUE : System.currentTimeMillis() - stateAt;
	}

	public boolean isRunning() {
		synchronized (lifecycle) {
			return process != null && process.isAlive();
		}
	}

	/** Set once the bridge has died enough times that the mod stops relaunching it. */
	public boolean isUnavailable() {
		return unavailable;
	}

	public String lastAck() {
		return lastAck;
	}

	/** Called from the client tick: launches the bridge, or relaunches it when due. */
	public void tick() {
		synchronized (lifecycle) {
			if (unavailable || stopping || isRunning() || System.currentTimeMillis() < retryAt) {
				return;
			}

			try {
				prepare();
				spawn();
				retries = 0;
			} catch (Exception e) {
				MusicDeck.LOGGER.warn("media helper did not start", e);
				retries++;
				retryAt = System.currentTimeMillis() + 30_000L;
				if (retries > MAX_RETRIES) {
					fail("could not start: " + message(e));
				}
			}
		}
	}

	/** Send one verb, optionally {@code verb:argument}; true means it was queued. */
	public boolean send(String command) {
		if (!isRunning()) {
			return false;
		}

		try {
			Files.createDirectories(commandDir);
			// The bridge consumes command files in name order, so zero pad the sequence.
			Path tmp = commandDir.resolve(String.format("%012d.tmp", sequence.incrementAndGet()));
			Files.writeString(tmp, command, StandardCharsets.UTF_8);
			Path cmd = commandDir.resolve(tmp.getFileName().toString().replace(".tmp", ".cmd"));
			try {
				Files.move(tmp, cmd, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, cmd, StandardCopyOption.REPLACE_EXISTING);
			}

			return true;
		} catch (IOException e) {
			MusicDeck.LOGGER.warn("media helper command {} was not queued", command, e);
			return false;
		}
	}

	public void stop() {
		synchronized (lifecycle) {
			stopping = true;
			if (process != null) {
				send("quit");
				try {
					if (!process.waitFor(3, TimeUnit.SECONDS)) {
						process.destroy();
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					process.destroy();
				}

				process = null;
			}
		}

		pruneCommands();
	}

	public void restart() {
		synchronized (lifecycle) {
			if (process != null) {
				process.destroyForcibly();
				process = null;
			}
		}

		pruneCommands();
		stopping = false;
		unavailable = false;
		retries = 0;
		retryAt = 0L;
	}

	private void prepare() throws IOException {
		Files.createDirectories(commandDir);
		Path script = workDir.resolve(SCRIPT_NAME);
		String want = readResource();
		String have = Files.exists(script)
			? new String(Files.readAllBytes(script), StandardCharsets.UTF_8) : "";
		// Rewrite whenever the shipped script differs, so a mod update takes effect
		// without asking the player to go looking for the folder.
		if (!want.equals(have)) {
			Files.writeString(script, want, StandardCharsets.UTF_8);
		}
	}

	private static String readResource() throws IOException {
		try (InputStream in = MediaBridge.class.getResourceAsStream(SCRIPT)) {
			if (in == null) {
				throw new IOException(SCRIPT + " is missing from the jar");
			}

			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private void spawn() throws IOException {
		Path script = workDir.resolve(SCRIPT_NAME);
		ProcessBuilder builder = new ProcessBuilder(powershell(),
			"-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
			"-File", script.toString(), "-CommandDir", commandDir.toString(), "-ArtPath", artFile.toString())
			.redirectError(logFile.toFile());

		Process spawned = builder.start();
		// Hand back the stdin pipe straight away; the script never reads it, and this
		// is the only thread that could block on it.
		try (OutputStream stdin = spawned.getOutputStream()) {
			// Closing without writing is the point.
		}

		process = spawned;
		Thread reader = new Thread(() -> pump(spawned), "musicdeck-smtc");
		reader.setDaemon(true);
		reader.start();
	}

	private static String powershell() {
		String root = System.getenv("SystemRoot");
		if (root == null) {
			return "powershell.exe";
		}

		Path system32 = Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
		return Files.isExecutable(system32) ? system32.toString() : "powershell.exe";
	}

	/** Reads the child's stdout until it closes, then decides whether that was an accident. */
	private void pump(Process mine) {
		int exit = -1;
		try (BufferedReader in = new BufferedReader(
			new InputStreamReader(mine.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = in.readLine()) != null) {
				handle(line);
			}

			exit = mine.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		} catch (IOException e) {
			if (!stopping) {
				MusicDeck.LOGGER.warn("media helper reader stopped", e);
			}
		}

		synchronized (lifecycle) {
			// stop()/restart() may already have replaced this process.
			if (mine != process) {
				return;
			}

			process = null;
			if (stopping) {
				return;
			}

			retries++;
			MusicDeck.LOGGER.warn("media helper exited with code {}", exit);
			if (retries > MAX_RETRIES) {
				fail("the helper exited " + retries + " times");
			} else {
				retryAt = System.currentTimeMillis() + (retries == 1 ? 2_000L : 20_000L);
			}
		}
	}

	private void fail(String why) {
		unavailable = true;
		state = NowPlaying.EMPTY;
		stateAt = 0L;
		lastAck = why + "; media control disabled until /deck restart";
		MusicDeck.LOGGER.warn("media helper unavailable: {}", why);
	}

	private void handle(String line) {
		if (line.isBlank()) {
			return;
		}

		JsonLine json = JsonLine.parse(line);
		switch (json.string("t", "")) {
			case "state" -> {
				state = NowPlaying.from(json);
				stateAt = System.currentTimeMillis();
			}
			case "ack" -> {
				String error = json.string("error", "");
				lastAck = json.string("cmd", "") + (json.bool("ok", false) ? " ok"
					: " refused" + (error.isEmpty() ? "" : " (" + error + ")"));
			}
			case "error" -> MusicDeck.LOGGER.warn("media helper: {}", json.string("msg", line));
			default -> {
			}
		}
	}

	private void pruneCommands() {
		if (!Files.isDirectory(commandDir)) {
			return;
		}

		try (Stream<Path> files = Files.list(commandDir)) {
			files.filter(Files::isRegularFile).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
					// A leftover command file is harmless; the bridge clears the dir on start.
				}
			});
		} catch (IOException e) {
			MusicDeck.LOGGER.warn("media helper command dir not cleaned", e);
		}
	}

	private static String message(Throwable t) {
		return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
	}
}
