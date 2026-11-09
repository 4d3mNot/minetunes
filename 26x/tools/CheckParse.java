// Scratch harness: feeds the real state lines the PowerShell bridge printed during
// the live test through JsonLine + NowPlaying, and fails loudly on a wrong string.
// Compiled and run outside Gradle because neither class imports Minecraft.

import com.musicdeck.client.JsonLine;
import com.musicdeck.client.NowPlaying;

public class CheckParse {
	private static int failures = 0;

	public static void main(String[] args) {
	 NowPlaying playing = NowPlaying.from(JsonLine.parse(
		"{\"t\":\"state\",\"session\":true,\"source\":\"Spotify.exe\",\"status\":\"Playing\",\"title\":\"Trust\",\"artist\":\"Brent Faiyaz\",\"album\":\"Björk\",\"pos\":17.0,\"end\":158.4,\"shuffle\":null,\"repeat\":null}"));
		expect(playing.session(), "session true");
		expect(playing.spotify(), "source is spotify");
		expect(playing.playing(), "status is playing");
		expect(playing.album().equals("Björk"), "utf-8 album survives, got: " + playing.album());
		expect(playing.duration() == 158.4, "duration parses");
		expect(playing.known(), "receivedAt stamped");
		expect(playing.progressText().startsWith("0:1"), "progress starts under a minute: " + playing.progressText());
		expect(playing.progressText().endsWith("/ 2:38"), "duration formats 158.4 as 2:38, got: " + playing.progressText());
		expect(playing.fraction() > 0.10 && playing.fraction() < 0.12, "fraction near 17/158: " + playing.fraction());

	 NowPlaying shortLabel = NowPlaying.from(JsonLine.parse(
		"{\"t\":\"state\",\"session\":true,\"source\":\"Spotify.exe\",\"status\":\"Playing\",\"title\":\"The Longest Song Name Ever Written By Anyone\",\"artist\":\"Somebody\",\"album\":\"\",\"pos\":0.0,\"end\":0.0}"));
		// The budget is "never longer than", not "exactly": trimming the dangling
		// space before the dots is the behaviour that reads correctly in the HUD.
		expect(shortLabel.label(20).length() <= 20 && shortLabel.label(20).length() >= 18,
			"label stays inside the 20 budget, got: " + shortLabel.label(20));
		expect(shortLabel.label(20).endsWith("..."), "truncation marked with ascii dots: " + shortLabel.label(20));
		expect(shortLabel.progressText().isEmpty(), "no duration means no progress text");
		expect(shortLabel.playing(), "playing parsed");

	 NowPlaying chrome = NowPlaying.from(JsonLine.parse(
		"{\"t\":\"state\",\"session\":true,\"source\":\"Chrome\",\"status\":\"Paused\",\"title\":\"Video\",\"artist\":\"\",\"album\":\"\",\"pos\":44.0,\"end\":600.0}"));
		expect(!chrome.spotify(), "chrome is not spotify");
		expect(!chrome.playing(), "paused is not playing");
		expect(chrome.position() == 44.0, "a paused position is not extrapolated: " + chrome.position());
		expect(chrome.label(34).equals("Video"), "empty artist falls back to the title: " + chrome.label(34));

	 NowPlaying none = NowPlaying.from(JsonLine.parse("{\"t\":\"state\",\"session\":false}"));
		expect(!none.session(), "no session parsed");
		expect(!none.hasTrack(), "no session means no track");
		expect(none.fraction() == 0.0, "no duration is a zero fraction, not a divide by zero");

	 expect(JsonLine.parse("{\"t\":\"ack\",\"cmd\":\"toggle\",\"ok\":true}").bool("ok", false), "ack ok");
	 expect(!JsonLine.parse("{\"t\":\"ack\",\"cmd\":\"next\",\"ok\":false,\"error\":\"no-media-session\"}")
		.bool("ok", true), "ack refused");
	 expect(JsonLine.parse("{\"t\":\"ack\",\"cmd\":\"x\",\"ok\":true}").string("shuffle", "").isEmpty(),
		"an absent key falls back");
	 expect(JsonLine.parse("garbage").string("t", "").isEmpty(), "a bad line degrades to empty");

	 NowPlaying escaped = NowPlaying.from(JsonLine.parse(
		"{\"t\":\"state\",\"session\":true,\"source\":\"Spotify.exe\",\"status\":\"Playing\",\"title\":\"Say \\\"Hi\\\"\",\"artist\":\"A\\\\B\",\"album\":\"\",\"pos\":1.0,\"end\":2.0}"));
		expect(escaped.title().equals("Say \"Hi\""), "quoted title survives, got: " + escaped.title());
		expect(escaped.artist().equals("A\\B"), "backslash artist survives, got: " + escaped.artist());

		System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
		if (failures > 0) {
			System.exit(1);
		}
	}

	private static void expect(boolean ok, String what) {
		if (!ok) {
			failures++;
			System.out.println("FAIL: " + what);
		} else {
			System.out.println("ok   " + what);
		}
	}
}
