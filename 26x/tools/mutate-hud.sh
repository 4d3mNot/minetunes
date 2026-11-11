#!/usr/bin/env bash
# Mutation test for the HUD gates: plant a defect, run CheckHud, and require that it
# names the defect. A suite that passes on broken code is worse than no suite.
set -u
cd "$(dirname "$0")/.."
L=src/client/java/com/musicdeck/client/HudLayout.java
P=src/client/java/com/musicdeck/client/Palette.java
S=src/client/java/com/musicdeck/client/LayoutScreen.java
N=src/client/java/com/musicdeck/client/NowPlayingHud.java
G=src/client/resources/assets/musicdeck/lang/en_us.json
B=src/main/resources/musicdeck/smtc-bridge.ps1
A=src/client/java/com/musicdeck/client/AlbumArt.java
K=src/client/java/com/musicdeck/client/KeyBindScreen.java
D=src/client/java/com/musicdeck/client/SettingsScreen.java
C=src/client/java/com/musicdeck/client/MusicDeckClient.java
M=src/client/java/com/musicdeck/client/MusicDeckModMenu.java
V=src/client/java/com/musicdeck/client/MediaConfig.java
Y=src/client/java/com/musicdeck/client/NowPlaying.java
O=src/main/java/com/musicdeck/MusicDeck.java
F=src/main/resources/fabric.mod.json
I=src/main/resources/assets/musicdeck/icon.png
SK=src/client/java/com/musicdeck/client/DeckSkin.java
DL=src/client/java/com/musicdeck/client/DeckLibrary.java
LY=src/client/java/com/musicdeck/client/Lyrics.java
LB=src/client/java/com/musicdeck/client/LyricsLibrary.java
LH=src/client/java/com/musicdeck/client/LyricsCache.java
LP=src/client/java/com/musicdeck/client/LyricsLayout.java
CM=src/client/java/com/musicdeck/client/MusicDeckCommand.java
LF=src/client/java/com/musicdeck/client/LyricsFetcher.java
KR=src/client/java/com/musicdeck/client/Karaoke.java
KL=src/client/java/com/musicdeck/client/KaraokeLayout.java
KS=src/client/java/com/musicdeck/client/KaraokeScreen.java
MC=src/client/java/com/musicdeck/client/MediaClock.java
MB=src/client/java/com/musicdeck/client/MediaBridge.java
PLATFORMS="26.1.2 26.2 26.3"
SRC="tools/CheckHud.java $L $P src/client/java/com/musicdeck/client/HudCanvas.java src/client/java/com/musicdeck/client/NowPlaying.java src/client/java/com/musicdeck/client/JsonLine.java $SK $DL $LY $LB $LH $LP $LF $KR $KL $MC"

# CheckHud reads the screens and the language file off disk at run time, so a plant in
# either is live without a recompile - which also means restore() has to put them back.
# The same goes for the two halves of the artwork pipeline: one is PowerShell and never
# compiles, the other is only ever read as text by the cross-file gates. From this build
# the list is longer: the screens, the wiring, the manifest and the build file are all
# read as text, so a plant in them is caught without the compiler ever seeing it.
restore() {
	cp build-mut/HudLayout.java "$L"
	cp build-mut/Palette.java "$P"
	cp build-mut/LayoutScreen.java "$S"
	cp build-mut/NowPlayingHud.java "$N"
	cp build-mut/en_us.json "$G"
	cp build-mut/smtc-bridge.ps1 "$B"
	cp build-mut/AlbumArt.java "$A"
	cp build-mut/KeyBindScreen.java "$K"
	cp build-mut/SettingsScreen.java "$D"
	cp build-mut/MusicDeckClient.java "$C"
	cp build-mut/MusicDeckModMenu.java "$M"
	cp build-mut/MediaConfig.java "$V"
	cp build-mut/NowPlaying.java "$Y"
	cp build-mut/MusicDeck.java "$O"
	cp build-mut/fabric.mod.json "$F"
	cp build-mut/icon.png "$I"
	cp build-mut/DeckSkin.java "$SK"
	cp build-mut/DeckLibrary.java "$DL"
	cp build-mut/Lyrics.java "$LY"
	cp build-mut/LyricsLibrary.java "$LB"
	cp build-mut/LyricsCache.java "$LH"
	cp build-mut/LyricsLayout.java "$LP"
	cp build-mut/MusicDeckCommand.java "$CM"
	cp build-mut/LyricsFetcher.java "$LF"
	cp build-mut/Karaoke.java "$KR"
	cp build-mut/KaraokeLayout.java "$KL"
	cp build-mut/KaraokeScreen.java "$KS"
	cp build-mut/MediaClock.java "$MC"
	cp build-mut/MediaBridge.java "$MB"
	for v in $PLATFORMS; do
		cp "build-mut/Platform-$v.java" "src/client/versions/$v/java/com/musicdeck/client/Platform.java"
	done
}
mkdir -p build-mut
cp "$L" build-mut/HudLayout.java
cp "$P" build-mut/Palette.java
cp "$S" build-mut/LayoutScreen.java
cp "$N" build-mut/NowPlayingHud.java
cp "$G" build-mut/en_us.json
cp "$B" build-mut/smtc-bridge.ps1
cp "$A" build-mut/AlbumArt.java
cp "$K" build-mut/KeyBindScreen.java
cp "$D" build-mut/SettingsScreen.java
cp "$C" build-mut/MusicDeckClient.java
cp "$M" build-mut/MusicDeckModMenu.java
cp "$V" build-mut/MediaConfig.java
cp "$Y" build-mut/NowPlaying.java
cp "$O" build-mut/MusicDeck.java
cp "$F" build-mut/fabric.mod.json
cp "$I" build-mut/icon.png
cp "$SK" build-mut/DeckSkin.java
cp "$DL" build-mut/DeckLibrary.java
cp "$LY" build-mut/Lyrics.java
cp "$LB" build-mut/LyricsLibrary.java
cp "$LH" build-mut/LyricsCache.java
cp "$LP" build-mut/LyricsLayout.java
cp "$CM" build-mut/MusicDeckCommand.java
cp "$LF" build-mut/LyricsFetcher.java
cp "$KR" build-mut/Karaoke.java
cp "$KL" build-mut/KaraokeLayout.java
cp "$KS" build-mut/KaraokeScreen.java
cp "$MC" build-mut/MediaClock.java
cp "$MB" build-mut/MediaBridge.java
for v in $PLATFORMS; do
	cp "src/client/versions/$v/java/com/musicdeck/client/Platform.java" "build-mut/Platform-$v.java"
done

survivors=0
plants=0
skip_until=${SKIP_UNTIL:-0}
plant() { # file, sed-expr, needle-expected-in-output
	plants=$((plants + 1))
	if [ "$plants" -le "$skip_until" ]; then return; fi
	cp "$1" "$1.cur"
	if ! sed -e "$2" "$1.cur" > "$1.mut"; then
		echo "SKIP (sed rejected): $3"
		rm -f "$1.cur" "$1.mut"
		restore
		return
	fi

	# A pattern that matches nothing plants nothing, and a clean run over an untouched
	# file looks exactly like a gate with teeth. Say so instead.
	if cmp -s "$1.mut" "$1.cur"; then
		echo "NOT-APPLIED: $3   <-- the anchor is gone from the source"
		survivors=$((survivors + 1))
		rm -f "$1.cur" "$1.mut"
		return
	fi

	rm -f "$1.cur"
	mv -f "$1.mut" "$1"

	if ! javac -encoding UTF-8 -d build-mut $SRC 2>build-mut/javac.txt; then
		echo "SKIP (would not compile): $3"
		restore
		return
	fi

	out=$(java -cp build-mut CheckHud 2>&1)
	restore
	if echo "$out" | grep -q '^FAIL'; then
		echo "CAUGHT: $3"
		echo "$out" | grep '^FAIL' | sed 's/^/         /'
	else
		echo "SURVIVED: $3   <-- gate hole"
		survivors=$((survivors + 1))
	fi
}

# The icon is bytes, not text: sed cannot plant a defect in it, so the plant is a whole
# different file dropped onto the same path. Everything else about the run is the same.
swap() { # file, stand-in, what the gate must name
	plants=$((plants + 1))
	if [ "$plants" -le "$skip_until" ]; then return; fi
	if [ ! -f "$2" ]; then
		echo "NOT-APPLIED: $3   <-- the stand-in $2 was never built"
		survivors=$((survivors + 1))
		return
	fi
	if cmp -s "$2" "$1"; then
		echo "NOT-APPLIED: $3   <-- the stand-in is already the file itself"
		survivors=$((survivors + 1))
		return
	fi
	cp "$2" "$1"
	# A copy that failed, or one the file system refused, would otherwise run the gates
	# against a clean tree and report a pass. The plant is only planted if the bytes moved.
	if ! cmp -s "$2" "$1"; then
		echo "NOT-APPLIED: $3   <-- the stand-in did not reach $1"
		survivors=$((survivors + 1))
		return
	fi
	if ! javac -encoding UTF-8 -d build-mut $SRC 2>build-mut/javac.txt; then
		echo "SKIP (would not compile): $3"
		restore
		return
	fi
	out=$(java -cp build-mut CheckHud 2>&1)
	restore
	if echo "$out" | grep -q '^FAIL'; then
		echo "CAUGHT: $3"
		echo "$out" | grep '^FAIL' | sed 's/^/         /'
	else
		echo "SURVIVED: $3   <-- gate hole"
		survivors=$((survivors + 1))
	fi
}

echo "=== control run (must be clean) ==="
javac -encoding UTF-8 -d build-mut $SRC && java -cp build-mut CheckHud | tail -1

echo "=== plants: the card as a whole ==="
plant "$L" 's@int textX = sleeveRight || stacked ? x + pad : tileX + (art ? tile + gap : 0);@int textX = sleeveRight || stacked ? x + pad : tileX;@' 'title drawn on top of the art tile'
plant "$L" 's|+ (skin.buttons() ? ROW_GAP + BTN_H : 0) + pad;|+ (skin.buttons() ? ROW_GAP : 0) + pad;|' 'card one row too short for the controls'
plant "$L" 's|parts.textX() + parts.column() - canvas.textWidth(frame.time())|parts.textX() + parts.column()|' 'time not right-aligned'
plant "$L" 's|return time.isEmpty() ? 0 : canvas.textWidth(time) + 4;|return 0;|' 'detail ignores the time it must not touch'
plant "$L" 's|content = Math.max(skin.minWidth(), Math.min(content, skin.maxWidth()));|content = Math.max(skin.minWidth(), content);|' 'the text column has no ceiling'
plant "$L" 's|int x = Math.max(0, Math.min(options.x(), guiWidth - width));|int x = Math.max(0, options.x());|' 'a card can be placed off the right edge'
plant "$L" 's|live ? skin.panel() : dim(skin.panel())|skin.panel()|' 'paused panel not dimmed'
plant "$L" 's|return Palette.withAlpha(panel, alpha);|return Palette.withAlpha(panel + 0x00001400, alpha);|' 'paused panel shifts hue as well as alpha'
plant "$L" 's|canvas.fill(barX, parts.barY(), barX + barW, parts.barY() + 2,|canvas.fill(barX, parts.barY(), barX + barW, parts.barY() + 3,|' 'bar track three pixels tall'
plant "$L" 's|if (flash > 0.0) {|if (false) {|' 'track-change flash removed'
plant "$L" 's|line(canvas, frame.detail(), parts.textX(), detailRoom,|line(canvas, NowPlaying.cap(frame.detail(), 12), parts.textX(), detailRoom,|' 'the box cuts the detail again instead of letting it slide'
plant "$L" 's|title = trimDangling(title);||' 'a budgeted title leaves the bracket it opened hanging'
plant "$L" 's|while (end > 0 \&\& .*|while (end > 0 \&\& " ".indexOf(text.charAt(end - 1)) >= 0) {|' 'the cut only trims spaces, not separators'
plant "$L" 's|return showHud \&\& playing.hasTrack() \&\& (!onlyWhenPlaying \|\| playing.playing());|return showHud;|' 'the card ignores pause, emptiness and the hide switch'

echo "=== plants: the control row ==="
plant "$L" 's|button(canvas, frame.buttons().get(index), index,|button(canvas, new Rect(frame.buttons().get(index).x() + 2, frame.buttons().get(index).y(), BTN_W, BTN_H), index,|' 'a button is painted two pixels off its click box'
plant "$L" 's|buttons.add(new Rect(rowX + index \* BTN_STEP, rowY, BTN_W, BTN_H));|buttons.add(new Rect(rowX + index * BTN_STEP, rowY, BTN_W + 2, BTN_H));|' 'the hit box is wider than the button drawn in it'
plant "$L" 's|int BTN_STEP = BTN_W + 2;|int BTN_STEP = BTN_W;|' 'the buttons touch, so one click can land in two'
plant "$L" 's|int rowY = barY + (skin.progress() ? BAR_GAP + 2 : 0) + ROW_GAP;|int rowY = barY;|' 'the control row is moved on top of the progress bar'
plant "$L" 's|if (interactive) {|if (true) {|' 'an inert card still paints clickable chrome'
plant "$L" 's|index == input.hover()|input.hover() >= 0|' 'hovering one button lights the whole row'
plant "$L" 's|index == input.pressed()|false|' 'a held press is not lit'
plant "$L" 's|case PREV -> "prev";|case PREV -> "next";|' 'the left button skips forward'
# A one-pixel skip bar is legal geometry and an unreadable glyph: it is the only thing
# that tells "next" apart from the bare triangle a paused play button draws.
plant "$L" 's|canvas.fill(cx - 5, cy - 4, cx - 3, cy + 4, colour);|canvas.fill(cx - 5, cy - 4, cx - 4, cy + 4, colour);|' 'the previous bar is one pixel wide'
plant "$L" 's|canvas.fill(cx + 4, cy - 4, cx + 6, cy + 4, colour);|canvas.fill(cx + 4, cy - 4, cx + 5, cy + 4, colour);|' 'the next bar is one pixel wide'
plant "$L" 's|case REPEAT -> "repeat:" + nextMode(repeat);|case REPEAT -> "repeat:Track";|' 'the repeat button can never ask for album or none'
plant "$L" 's|case "Track" -> "Album";|case "Track" -> "None";|' 'the repeat cycle skips album'
plant "$L" 's|default -> null;|default -> "toggle";|' 'an index past the row still sends a verb'
plant "$L" 's|if (mode == 1) {|if (false) {|' 'the repeat dots never show one track'
plant "$L" 's|if (input.editing()) {|if (false) {|' 'the placement editor draws no outline'
# The row is the card's only symmetric feature: left-aligning it is legal geometry and a
# visibly broken control bar, so both ends of the mistake need their own plant.
plant "$L" 's|int rowX = x + (width - rowW) / 2;|int rowX = x + PAD;|' 'the control row is left-aligned, not centred'
plant "$L" 's|int rowX = x + (width - rowW) / 2;|int rowX = x + width - rowW;|' 'the control row hangs against the right edge'
plant "$L" 's|return BUTTONS \* BTN_W + (BUTTONS - 1) \* (BTN_STEP - BTN_W);|return BUTTONS * BTN_W;|' 'the declared row width forgets the gaps'

echo "=== plants: the slide ==="
# The clip and the shift are one mechanism: take either away and the glyphs are painted
# across the panel border, and only the recorder's clip bookkeeping can see that.
plant "$L" 's|int overflow = canvas.textWidth(text) - room;|int overflow = 0;|' 'a line too wide for its column is not noticed, so nothing clips it'
plant "$L" 's|if (overflow <= 0) {|if (false) {|' 'a card whose text fits still opens a clip'
plant "$L" 's|canvas.clip(x, y - 1, x + room, y + LINE + 1);|canvas.clip(x, y - 1, x + room + 8, y + LINE + 1);|' 'the clip runs eight pixels into the time'
plant "$L" 's|canvas.clip(x, y - 1, x + room, y + LINE + 1);|canvas.clip(x, y, x + room, y + LINE);|' 'the clip slices the shadow off the sliding line'
plant "$L" 's|scrollShift(overflow, phase)|scrollShift(overflow, 0.0)|' 'the line is clipped but never moves'
plant "$L" 's|return t / travel;|return 1.0;|' 'the slide jumps to its end instead of travelling to it'
plant "$L" 's|(int) Math.round(overflow \* Math.min|(int) Math.round(overflow * 2.0 * Math.min|' 'the card pulls a line past its own overhang'
plant "$L" 's|Math.min(1.0, Math.max(0.0, phase))|phase|' 'a phase outside 0..1 is applied exactly as asked'
plant "$L" 's|if (t < SCROLL_REST_SEC) {|if (false) {|' 'the slide never rests on its last letters'
plant "$L" 's|double cycle = 2.0 \* (travel + SCROLL_REST_SEC);|double cycle = travel + SCROLL_REST_SEC;|' 'the slide never comes back or rests on its first letters'
plant "$L" 's|if (travel <= 0.0 \|\| seconds < 0.0) {|if (travel <= 0.0) {|' 'a clock that steps backwards lands the card mid-cycle'
plant "$L" 's|overhang / SCROLL_PX_PER_SEC|overhang / (SCROLL_PX_PER_SEC * 4)|' 'the slide is too slow to finish its cycle'
plant "$L" 's|overhang / SCROLL_PX_PER_SEC|overhang / (SCROLL_PX_PER_SEC / 4)|' 'the slide is too fast to read'
# Both clamps, in the direction each one exists for, and the threshold that keeps them
# from ever being asked to time a sub-glyph overhang.
plant "$L" 's|Math.min(MAX_TRAVEL_SEC, Math.max(MIN_TRAVEL_SEC, overhang / SCROLL_PX_PER_SEC));|Math.min(MAX_TRAVEL_SEC, overhang / SCROLL_PX_PER_SEC);|' 'a one-glyph overhang gets a whole leg to crawl through'
plant "$L" 's|Math.min(MAX_TRAVEL_SEC, Math.max(MIN_TRAVEL_SEC, overhang / SCROLL_PX_PER_SEC));|Math.max(MIN_TRAVEL_SEC, overhang / SCROLL_PX_PER_SEC);|' 'a runaway headline makes the player wait out its last word'
plant "$L" 's|if (overhang <= SLIDE_MIN_PX) {|if (false) {|' 'a sub-glyph overhang is slid for anyway'
plant "$L" 's|overflow <= SLIDE_MIN_PX ? 0 :|overflow <= 0 ? 0 :|' "a short line is tugged sideways by its longer neighbour's clock"
# The clock is shared, and shared from the worst line. Either mistake here puts the two
# lines out of step, which is the exact thing this round of the slide exists to stop.
plant "$L" 's|return travelSeconds(Math.max(canvas.textWidth(frame.title()) - column,|return travelSeconds(Math.min(canvas.textWidth(frame.title()) - column,|' 'the card clock takes the tidiest line, so the two drift apart'
plant "$L" 's|int column = frame.parts().column();|int column = 80;|' 'the card clock measures a column the painter never uses'
plant "$N" 's|(System.currentTimeMillis() - changedAt) / 1000.0|0.0|' 'the card is handed a frozen scroll clock'
plant "$N" 's|graphics.enableScissor(x1, y1, x2, y2);|// the panel keeps its own edges|' 'the canvas accepts the clip and does nothing'
# The guard is the only cut left, so both ways of getting it wrong matter: no guard at all,
# and a guard so high it never fires on a headline nobody could scroll through.
plant "$L" 's|NowPlaying.cap(playing.headline(), MAX_HEADLINE_CHARS)|playing.headline()|' 'the runaway headline guard is gone, so the card can be handed a wall of text'
plant "$L" 's|MAX_HEADLINE_CHARS = 200|MAX_HEADLINE_CHARS = 200000|' 'the runaway guard is set so high it never fires'

echo "=== plants: the palette ==="
# The vote has two floors and both need a plant that walks straight past them. Note the
# difference from the old average: these covers are one colour each, so only a gate that
# knows the floors can tell a vote from a mean.
plant "$P" 's|votes\[best\] >= argb.length \* MIN_SHARE|votes[best] >= 0|' 'the hue vote needs no weight at all, so a patch of JPEG noise repaints the card'
plant "$P" 's|MIN_SHARE = 0.015|MIN_SHARE = 0.5|' 'the tile must be half coloured before the card takes its colour'
plant "$P" 's|MIN_SHARE = 0.015|MIN_SHARE = 0.0|; s|MIN_VOTERS = 6|MIN_VOTERS = 0|' 'no floor on the vote at all, so one stray pixel repaints the card'
plant "$P" 's|MIN_CHROMA = 20|MIN_CHROMA = 160|' 'a moderately coloured cover is called grey and loses its accent'
plant "$P" 's|MIN_VALUE = 35|MIN_VALUE = 200|' 'a dark cover is thrown away instead of lifted'
plant "$P" 's|1.0 - Math.abs(max / 255.0 - 0.6) / 0.6|1.0|' 'every pixel weighs the same, so a faint patch outranks a solid one'
# Why the average was replaced with a vote: these two are invisible on a one-colour cover.
plant "$P" 's|360.0 / HUES|360.0|' 'the wheel is never cut, so every hue votes into one bucket and the card averages to mud'
plant "$P" 's|HUES = 12|HUES = 1|' 'there is one hue bucket, which is the old average under a new name'
plant "$P" 's|votes\[bucket\] > votes\[best\]|votes[bucket] < votes[best]|' 'the weakest hue bucket wins, so a cover is tinted by its least important colour'
plant "$P" 's|tint(voteRed\[best\], voteGreen\[best\], voteBlue\[best\], votes\[best\])|tint(allRed, allGreen, allBlue, allWeight)|; s|double\[\] voteRed = new double\[HUES\];|double[] voteRed = new double[HUES]; double allRed = 0, allGreen = 0, allBlue = 0, allWeight = 0;|; s|voteRed\[bucket\] += red \* strength;|allRed += red * strength; allWeight += strength;|; s|voteGreen\[bucket\] += green \* strength;|allGreen += green * strength;|; s|voteBlue\[bucket\] += blue \* strength;|allBlue += blue * strength;|' 'the winning bucket is averaged against every coloured pixel after all'
plant "$P" 's|if (max == red) {|if (max == blue) {|' 'the hue wheel is indexed from the wrong corner, so red pixels vote as blue'
plant "$P" 's|green < blue ? 6.0 : 0.0|green < blue ? 1.0 : 0.0|' 'a negative hue is folded into the wrong part of the wheel'
plant "$P" 's|if (max < 150) {|if (false) {|' 'dark accent never lifted'
# Both sides of the discard threshold. Spotify's thumbnail is one flat 78% alpha, so a
# stricter gate reads a whole cover as empty; a looser one lets a faint watermark repaint.
plant "$P" 's|< 128) {|< 200) {|' 'a translucent cover discarded as empty'
plant "$P" 's|< 128) {|< 16) {|' 'a barely-there field accepted as a palette'
plant "$P" 's|return 0xFF000000 | return 0x00000000 |' 'accent loses its alpha'
# The greyscale rung, which is the only thing a monochrome record can be read by.
plant "$P" 's|if (neutral \* 12 >= Math.max(1, argb.length)) {|if (false) {|' 'a greyscale cover loses its silver accent'
plant "$P" 's|chroma < MIN_NEUTRAL_CHROMA|chroma > MIN_NEUTRAL_CHROMA|' 'grey pixels are read as coloured, so a monochrome record gets no accent at all'
# Theme is a three-way choice, so each wrong turn needs its own plant: a swapped
# branch, and an automatic mode that stops reading the cover at all.
plant "$P" 's|if (THEME_GREEN.equals(theme)) {|if (THEME_WHITE.equals(theme)) {|' 'the green theme paints the card white'
plant "$P" 's|return sampled != 0 ? sampled : fallback;|return fallback;|' 'the automatic theme ignores the sampled cover'
plant "$P" 's|return sampled != 0 ? sampled : fallback;|return sampled != 0 ? sampled : sampled;|' 'no cover gives a black card instead of the brand'

echo "=== plants: the screens and the language file ==="
# The bug that actually took the game down: a Screen asking for the blur the renderer has
# already applied this frame. Geometry gates cannot see it, so this one reads the source.
plant "$S" 's|super.extractRenderState(graphics, mouseX, mouseY, delta);|this.extractBlurredBackground(graphics);|' 'a screen asks for a second blur'
plant "$S" 's|return super.mouseClicked(event, doubleClick);|return false;|' 'a click the card refused never reaches the button under it'
plant "$N" 's|Platform.screen(Minecraft.getInstance()) instanceof LayoutScreen|Platform.screen(Minecraft.getInstance()) != null|' 'the card treats every open screen as the placement editor'
plant "$N" 's|private boolean editing() {|private boolean layout = true;\n\n\tprivate boolean editing() {|' 'the editing state is remembered again, so it can outlive the editor'

# The two calls that moved between 26.x targets live behind Platform, and one gate per target
# reads that file. A shim that stops asking the game, stops opening the screen it was handed,
# or names the wrong input type breaks exactly one target, where no other gate is looking.
plant "src/client/versions/26.1.2/java/com/musicdeck/client/Platform.java" 's@return minecraft.screen;@return null;@' '26.1.2 stops asking the game for the open screen'
plant "src/client/versions/26.1.2/java/com/musicdeck/client/Platform.java" 's@minecraft.setScreen(screen);@minecraft.setScreen(null);@' '26.1.2 stops opening the screen it was handed'
plant "src/client/versions/26.1.2/java/com/musicdeck/client/Platform.java" 's@Type.KEYSYM@Type.MOUSE@' '26.1.2 reads key events as mouse buttons'
plant "src/client/versions/26.2/java/com/musicdeck/client/Platform.java" 's@return minecraft.gui.screen();@return null;@' '26.2 stops asking the game for the open screen'
plant "src/client/versions/26.2/java/com/musicdeck/client/Platform.java" 's@minecraft.gui.setScreen(screen);@minecraft.gui.setScreen(null);@' '26.2 stops opening the screen it was handed'
plant "src/client/versions/26.2/java/com/musicdeck/client/Platform.java" 's@Type.KEYSYM@Type.MOUSE@' '26.2 reads key events as mouse buttons'
plant "src/client/versions/26.3/java/com/musicdeck/client/Platform.java" 's@return minecraft.gui.screen();@return null;@' '26.3 stops asking the game for the open screen'
plant "src/client/versions/26.3/java/com/musicdeck/client/Platform.java" 's@minecraft.gui.setScreen(screen);@minecraft.gui.setScreen(null);@' '26.3 stops opening the screen it was handed'
plant "src/client/versions/26.3/java/com/musicdeck/client/Platform.java" 's@Type.KEYBOARD@Type.MOUSE@' '26.3 reads key events as mouse buttons'
plant "$S" 's|public void removed() {|public void onLeaving() {|' 'the editor saves only in a hook the game never calls'
plant "$G" 's|"key.musicdeck.move": "Move Now-Playing Panel",||' 'a keybinding has no label in the language file'
plant "$G" 's|"key.category.musicdeck.main": "MineTunes",||' 'the Controls section has no heading'
plant "$G" 's|"musicdeck.theme.white": "White"|"musicdeck.theme.whitee": "White"|' 'a theme value is shown as its raw key'

echo "=== plants: the artwork tile, across the two languages ==="
# The writer is PowerShell and the reader is Java, and they agree on the tile size only
# because both were told 48. Nothing in the build catches the two drifting apart, and the
# player only ever sees a missing cover. sed matches one line at a time, so every plant
# here is anchored on a whole line rather than across a break.
plant "$B" 's@^\$script:ArtSize = 48@$script:ArtSize = 64@' 'the helper writes a tile the card cannot blit'
plant "$A" 's#^\t\t\t\tdrop();$#\t\t\t\t// and the last good tile stays up#' 'a refused tile leaves the previous cover painted as the new one'
plant "$A" 's#^\t\taccent = 0;$#\t\t// and the last album colour stays up#' 'a card with no tile keeps the previous album colour'

# The bug the player reported: one flat wait after a failed fetch, which is five seconds
# of missing cover on nearly every new track because the title lands before the thumbnail.
plant "$B" 's|^\$script:ArtBackoff = .*$|$script:ArtBackoff = @(5.0)|' 'the retry table collapsed back to one flat wait'
plant "$B" 's|(0.0, 0.3, 0.6, 1.2, 2.5, 5.0)|(0.0, 5.0, 5.0, 5.0, 5.0, 5.0)|' 'a failed fetch waits five seconds again'
plant "$B" 's#^\t\$wait = .*$#\t$wait = 5.0#' 'the fetch ignores the table and waits a constant'
plant "$B" 's@^\t\t\$script:ArtRetry = 0@\t\t$unused = 0@' 'the retry counter never resets, so every track after the first inherits the old backoff'
plant "$B" 's@^\t\t\$script:ArtTriedAt = \[DateTime\]::MinValue@\t\t$script:ArtTriedAt = Get-Date@' 'a new track waits out the backoff the previous one earned'

# A track change that cannot be fetched must clear the tile, but only that branch may do
# it: clearing on a poll that merely stuttered is what turned a hiccup into a blank card.
plant "$B" 's#^\t\t\tUpdate-Art .*$#&\n\t\t} else {\n\t\t\tRemove-Art\n\t\t}#' 'the tile is cleared again on a poll that stutters'
plant "$B" 's#-not [$]task.Wait([$]TimeoutMs)#$true#' 'the await reports success whether or not it timed out'

# An await with no deadline parks the poll loop, and a loop that is not polling is a
# frozen card rather than a missing picture - which is a worse bug than the one reported.
plant "$B" 's# 2000$##' 'a properties fetch has no deadline'
plant "$B" 's# 4000$##' 'a thumbnail fetch has no deadline'
plant "$B" 's# 2000$# 90000#' 'a properties deadline is longer than the whole poll is worth'

echo "=== plants: reaching the settings, and rebinding from them ==="
# None of these files is compiled by the gate build - the manifest, the build script, the
# screens and the wiring are all read as text - so every plant here is a shape change the
# compiler would happily accept and only a gate can see.
plant "$F" 's@"com.musicdeck.client.MusicDeckModMenu"@"com.musicdeck.client.MusicDeckConfig"@' 'the Mod Menu entrypoint names a class that does not exist'
plant "$F" 's@"fabric-api": "\*"@"fabric-api": "*", "modmenu": "*"@' 'Mod Menu becomes required, so the mod refuses to load without it'
plant "$F" 's@"id": "musicdeck"@"id": "spotifybridge"@' 'the manifest still carries the old name'
plant "$O" 's@MOD_ID = "musicdeck"@MOD_ID = "spotifybridge"@' 'the mod id still carries the old name, which moves every lang key and asset path'
plant "$M" 's@implements ModMenuApi@implements Runnable@' 'the entrypoint does not implement the interface Mod Menu looks for'
plant "$M" 's@, parent)@)@' 'Mod Menu screen is opened with no parent, so Done drops the player into the world'
plant "$M" 's@client == null@false@' 'the screen is built from a client that has not initialised yet'
plant "$D" 's@Platform.open(this.minecraft, back)@Platform.open(this.minecraft, null)@' 'Done forgets which screen opened the settings'
plant "$D" 's@new KeyBindScreen(this)@new LayoutScreen(config, this)@' 'the settings screen no longer leads to the keybind list'
plant "$K" 's@MusicDeckClient.instance().keyBinds()@minecraft.options.keyMappings@' 'the keybind screen lists the whole game instead of the mod'\''s own six'
plant "$K" 's@this.minecraft.options.save();@@' 'a rebind is never written to options.txt, so it is lost on restart'
plant "$K" 's@mapping.setKey(mapping.getDefaultKey());@mapping.setKey(InputConstants.UNKNOWN);@' 'right click unbinds the key instead of restoring the shipped default'
plant "$K" 's@System.currentTimeMillis() - armedAt > GRACE_MILLIS@true@' 'the click that armed a row is itself taken as the new binding'
plant "$K" 's@event.key() != UNKNOWN_KEY@true@' 'a key event carrying no key silently binds the row to a phantom'
plant "$K" 's@return super.mouseClicked(event, doubleClick);@return true;@' 'a press outside a row never reaches the Back button under it'
plant "$K" 's@capturing = row;@capturing = row; this.rebuildWidgets();@' 'arming a row rebuilds the widget list while the click is still being sent'
plant "$K" 's@^\t\tif (event.isEscape()) {$@\t\tif (false) {\t\t\tcapturing = -1;@' 'ESC abandons the screen in the middle of a rebinding'
plant "$K" 's@minecraft.options.keyMappings@new KeyMapping[0]@' 'a key that already belongs to something else is accepted in silence'
plant "$K" 's@^\t\treturn false;$@\t\treturn true;@' 'opening the keybind screen pauses the world, which stops the music it controls'
plant "$K" 's@musicdeck.keys.hint@musicdeck.keys.help@' 'the keybind hint names a key the language file does not have'
plant "$G" 's@"musicdeck.keys.title": "MineTunes keybinds",@@' 'the keybind screen has no title in the language file'
plant "$C" 's@hudKey, moveKey@moveKey@' 'a registered binding is missing from the keybind screen, so it is stuck on its default'
plant "$C" 's@new SettingsScreen(config, Platform.screen(minecraft))@new SettingsScreen(config)@' 'the settings key opens with nothing behind it, so Done cannot go back to the inventory'
plant "$V" 's@Files.exists(file()) ? file() : legacyFile()@file()@' 'the rename throws away the panel position saved under the old name'
plant "$V" 's#|| "spotify".equals(value)##' 'a panel themed spotify before the rename silently resets to auto'
plant "$Y" 's@contains("spotify")@contains("green")@' 'the rename swept the session-owner test, so it can never be true again'

echo "=== plants: the picture the mod shows about itself ==="
plant "$F" 's@"name": "MineTunes"@"name": "Old Name"@' 'the mod list shows the old branding'
plant "$G" 's@"musicdeck.settings.title": "MineTunes"@"musicdeck.settings.title": "Old Name"@' 'the settings screen shows the old branding'
plant "$G" 's@"key.category.musicdeck.main": "MineTunes"@"key.category.musicdeck.main": "Old Name"@' 'the Controls category shows the old branding'
powershell -NoProfile -ExecutionPolicy Bypass -File tools/make-art.ps1 \
	-Source publish/minetunes-icon.png -Out build-mut/icon-32.png -Size 32 > /dev/null
plant "$F" 's@"icon": "assets/musicdeck/icon.png"@"icon": "assets/spotifybridge/icon.png"@' 'the icon points at the folder the old name used, which is not in the jar'
plant "$F" 's@"icon": "assets/musicdeck/icon.png"@"icon": "assets/musicdeck/art.png"@' 'the manifest names an icon that is not there'
plant "$F" 's@"icon": "assets/musicdeck/icon.png",@@' 'the manifest ships no icon, so every mod list shows the placeholder block'
swap "$I" publish/minetunes-icon.png 'the full-size page art shipped inside the jar, which every download carries'
swap "$I" publish/minetunes-banner.png 'a wide banner shipped as the square icon'
swap "$I" build-mut/icon-32.png 'an icon too small for a mod list to scale up without turning to mush'
swap "$I" src/main/resources/fabric.mod.json 'a file that is not a PNG sitting at the icon path'

echo "=== plants: the deck folder and its knobs ==="
# A community deck is somebody else's file, so the gates here are about what cannot happen:
# no number that escapes its clamp, no colour that reads as a failure, no switch the painter
# can ignore. Plant each one and require the suite to name it.
plant "$SK" 's@Math.max(low, Math.min(high, json.number(key, fallback)))@Math.max(low, json.number(key, fallback))@' 'a deck number has a floor but no ceiling'
plant "$SK" 's@Math.max(low, Math.min(high, json.number(key, fallback)))@Math.min(high, json.number(key, fallback))@' 'a deck number has a ceiling but no floor'
plant "$SK" 's@return value.length() == 6 ? parsed | 0xFF000000 : parsed;@return parsed;@' 'a six-digit colour is read with no alpha, so it paints invisible'
plant "$SK" 's@return parsed == null ? fallback : parsed.intValue();@return parsed == null || parsed < 0 ? fallback : parsed.intValue();@' 'a pinned colour is thrown away because every opaque ARGB int is negative'
plant "$SK" 's@int base = parsedPanel == null ? BUILT_IN.panel : parsedPanel.intValue();@int base = BUILT_IN.panel;@' 'a deck that names its panel is painted with the built-in one'
plant "$SK" 's@int alpha = (int) Math.round(number(json, "opacity", defaultOpacity, 0.15, 1.0) \* MAX_ALPHA);@int alpha = MAX_ALPHA;@' 'the opacity knob does nothing'
plant "$SK" 's@return value.startsWith("#") ? value.length() == 9 : value.length() == 8;@return false;@' 'an eight-digit colour loses the alpha it carried with it'
plant "$SK" 's@^\t\t\tlayout = ROW;@\t\t\tlayout = "column";@' 'a layout nobody invented keeps its own name'
plant "$SK" 's@name.isEmpty() ? fileName : name@name@' 'a deck that does not name itself shows a blank in the picker'
plant "$SK" 's@number(json, "tile", HudLayout.TILE, 0, MAX_TILE)@number(json, "tile", HudLayout.TILE, 0, 512)@' 'the tile ceiling is not one the card can draw'
plant "$SK" 's@MAX_WIDTH = 260;@MAX_WIDTH = 4000;@' 'the content ceiling is wider than any window'
plant "$DL" 's@Files.exists(target)@false@' 'seeding overwrites a deck the player edited to taste'
plant "$DL" 's@decks.add(DeckSkin.BUILT_IN);@@' 'the shipped card disappears from the picker'
plant "$DL" 's@return DeckSkin.BUILT_IN.equals(deck) ? 0 : 1;@return 1;@' 'the shipped card sorts wherever its name happens to land'
plant "$DL" 's@raw.trim().toLowerCase(Locale.ROOT).replace@raw.trim().replace@' 'a deck name is matched case-sensitively'
plant "$DL" 's@^\t\treturn null;@\t\treturn DeckSkin.BUILT_IN;@' 'the command cannot tell a missing deck from the built-in one'
plant "$DL" 's@Files.isRegularFile(file) && file.getFileName()@Files.isRegularFile(file) || file.getFileName()@' 'any file dropped in the folder is read as a deck'
plant "$DL" 's@"tile": 0,@"tile": 48,@' 'the minimal example is not actually the text-only card it advertises'
plant "$DL" 's@return normalize(deck.name());@return "deck-" + deck.name();@' 'the picker offers a handle the command cannot take back'
plant "$L" 's@boolean stacked = art && skin.stacked();@boolean stacked = false;@' 'a stacked deck still draws its sleeve beside the words'
plant "$L" 's@int inset = skin.border() ? 1 : 0;@int inset = 1;@' 'a deck cannot switch its border off'
plant "$L" 's@if (skin.progress()) {@if (true) {@' 'a deck cannot switch the progress bar off'
plant "$L" 's@if (skin.buttons()) {@if (true) {@' 'a deck cannot switch the control row off'
plant "$L" 's@String detail = skin.detail() ? detail(playing) : "";@String detail = detail(playing);@' 'a deck cannot hide the second line'
plant "$L" 's@String time = skin.time() ? playing.elapsedText() : "";@String time = playing.elapsedText();@' 'a deck cannot hide the clock'
plant "$L" 's@int textRoom = textBlock + TEXT_SINK + BAR_GAP;@int textRoom = textBlock;@' 'the progress bar is drawn straight through the text band'
plant "$L" 's@int textBlock = (skin.detail() || skin.time()) ? LINE \* 2 : LINE;@int textBlock = skin.detail() ? LINE * 2 : LINE;@' 'the card shrinks for a line that is gone while the clock still sits on that row'
plant "$V" 's@DeckLibrary.seedMissing(decks());@@' 'the deck folder is never seeded, so nothing tells a player the feature exists'
plant "$V" 's@props.setProperty("deck", deck);@@' 'the chosen deck is never written, so it resets on the next start'
plant "$V" 's@config.deckSkin = DeckLibrary.resolve(decks(), config.deck);@config.deckSkin = DeckSkin.BUILT_IN;@' 'a deck saved in the config file is ignored when it is read back'
plant "$N" 's@new HudLayout.Options(config.x(), config.y(), config.deckSkin())@new HudLayout.Options(config.x(), config.y(), DeckSkin.BUILT_IN)@' 'the card is measured from the shipped deck whatever the player picked'
plant "$N" 's@skin.accentOr(Palette.themed@Palette.themed@' 'a deck that pins its own colour loses it to the cover'
plant "$D" 's@"musicdeck.setting.deck",@@' 'the deck row vanishes from the settings screen'
plant "$D" 's@choice(5, CycleButton@choice(4, CycleButton@' 'two widgets are stacked on one row of the settings screen'
plant "$D" 's@List<DeckSkin> decks = DeckLibrary.load(MediaConfig.decks());@List<DeckSkin> decks = List.of(DeckSkin.BUILT_IN);@' 'the picker lists a fixed set instead of what is in the folder'
plant "$D" 's@STEP = 20;@STEP = 30;@' 'the settings rows no longer fit the window the game gives'

echo "=== plants: the card turning to face the corner it sits in ==="
# Which side the card reads from is one decision, made once, in the frame. Every plant below
# breaks it in a different place: the decision itself, the threshold, the sleeve, the column,
# the clock or the caller that tells the frame how wide the screen is.
plant "$L" 's@boolean mirror = width < guiWidth && 2 \* x + width > guiWidth;@boolean mirror = false;@' 'a card dragged to the right edge never turns to face its corner'
plant "$L" 's@boolean mirror = width < guiWidth && 2 \* x + width > guiWidth;@boolean mirror = width < guiWidth;@' 'every card turns, however far it sits from the right edge'
plant "$L" 's@2 \* x + width > guiWidth@2 * x + width >= guiWidth@' 'the card turns a pixel early, exactly on the middle line'
plant "$L" 's@width < guiWidth && 2 \* x@2 * x@' 'a card wider than the screen turns with nowhere to put its sleeve'
plant "$L" 's@2 \* x + width > guiWidth@2 * x > guiWidth@' 'the turn is keyed to the panel edge, not to the middle of the card'
plant "$L" 's@boolean sleeveRight = art && mirror;@boolean sleeveRight = mirror;@' 'a card with no sleeve moves its text off the panel anyway'
plant "$L" 's@int tileX = sleeveRight ? x + width - pad - tile : x + pad;@int tileX = x + pad;@' 'the text slides underneath a sleeve that never moved'
plant "$L" 's@int tileX = sleeveRight ? x + width - pad - tile : x + pad;@int tileX = sleeveRight ? x + width - tile : x + pad;@' 'a mirrored sleeve sits on top of the panel border'
plant "$L" 's@int textX = sleeveRight || stacked ? x + pad :@int textX = stacked ? x + pad :@' 'a mirrored card writes its text from under the sleeve'
plant "$L" 's@int textEnd = sleeveRight && !stacked ? tileX - gap : x + width - pad;@int textEnd = x + width - pad;@' 'the mirrored column runs straight under the cover'
plant "$L" 's@sleeveRight && !stacked ? tileX - gap :@sleeveRight \&\& !stacked ? tileX :@' 'a mirrored card lets its title touch the sleeve'
plant "$L" 's@int textEnd = sleeveRight && !stacked ?@int textEnd = sleeveRight ?@' 'a stacked card loses column width to a sleeve drawn above its text'
plant "$L" 's@int column = textEnd - textX;@int column = x + width - pad - textX;@' 'the mirrored column is measured from the panel pad again'
plant "$L" 's@parts.textX() + parts.column() - canvas.textWidth(frame.time())@x + width - pad - canvas.textWidth(frame.time())@' 'the clock of a turned card hides underneath the sleeve'
plant "$L" 's@parts, mirror, skin, title@parts, false, skin, title@' 'the measured card forgets which side it turned to'
plant "$L" 's@int rowX = x + (width - rowW) / 2;@int rowX = mirror ? x + width - rowW - pad : x + (width - rowW) / 2;@' 'the control row slides to one edge when the card turns'
plant "$N" 's@int guiWidth = graphics.guiWidth(), guiHeight = graphics.guiHeight();@int guiWidth = graphics.guiHeight(), guiHeight = graphics.guiWidth();@' 'the card is measured against the screen height, so it never learns which side it is on'
plant "$N" 's|lyricsOptions(), guiWidth, guiHeight);|lyricsOptions(), graphics.guiWidth(), graphics.guiHeight());|' 'the words are measured against a second read of the screen, so they can disagree with the card'
plant "$N" 's@minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight()@minecraft.getWindow().getGuiScaledHeight(), minecraft.getWindow().getGuiScaledHeight()@' 'the click path measures the card against another screen than the painter does'

echo "=== plants: the lyric file and its words ==="
# The parser is the one place a player's own file is read, and every mistake here is silent:
# a wrong fraction, a dropped offset or an unsorted list all draw plausible words at the
# wrong moment. Each plant names one of those silences.
plant "$LY" 's|shifted.add(new Line(Math.max(0.0, line.at() - offset), line.text(), line.words()));|shifted.add(new Line(line.at(), line.text(), line.words()));|' 'an offset written at the bottom no longer covers the lines above it'
plant "$LY" 's|return fraction.isEmpty() ? value : value + Double.parseDouble("0." + fraction);|return fraction.isEmpty() ? value : value + Integer.parseInt(fraction) / 100.0;|' 'the fraction is read as hundredths whatever its length, so .5 is five hundredths'
plant "$LY" 's|text = text.substring(1);|text = text;|' 'a byte-order mark is left on the first line, so that line is thrown away'
plant "$LY" 's|shifted.sort(Comparator.comparingDouble(Line::at));||' 'the lines stay in file order, so a file written out of order rewinds the song'
plant "$LY" 's|stamps.add(seconds);|stamps.clear(); stamps.add(seconds);|' 'a chorus written with two stamps is only sung the second time'
plant "$LY" 's|Line body = withWords(stamps.get(0), line.substring(at));|Line body = withWords(stamps.get(0), line.substring(at)); if (body.text().isEmpty()) { continue; }|' 'a timed gap is dropped, so the previous line stands through the instrumental'
plant "$LY" 's|lines.get(index).at() <= seconds|lines.get(index).at() < seconds|' 'a line does not start on its own stamp, so every line lands a frame late'
plant "$LY" 's|-> "\.\.\.";|-> ".";|' 'the ellipsis a lyric site writes is halved in the middle of a line'

echo "=== plants: finding the file for the song ==="
# A .lrc is somebody's own file in a folder of their own files, so the matcher has to be
# forgiving about spelling and strict about which song it is - and must never crash on a
# folder that is not there.
plant "$LB" 's|if (name.equals(key(stem(file)))) {|if (name.equals(key(baseName(file)))) {|' 'a file named after the track is compared with its .lrc suffix attached, so nothing ever matches'
plant "$LB" 's|if (Character.isLetterOrDigit(ch)) {|if (!Character.isWhitespace(ch)) {|' 'a file name is compared with its punctuation kept, so a hyphen defeats the match'
plant "$LB" 's|kept.append(Character.toLowerCase(ch));|kept.append(ch);|' 'a file name is matched case-sensitively, so a title saved in lower case is never read'
plant "$LB" 's|for (String name : wanted) {|for (String name : wanted.subList(wanted.size() - 1, wanted.size())) {|' 'only the loosest name is tried, so a title-only file outranks the artist and title'
plant "$LB" 's|if (!plain.equals(of)) {|if (false) {|' 'a bracketed note in a title stops the file named without it from matching'
plant "$LB" 's|if (name.equals(key(stem(file)))) {|if (true) {|' 'the first file in the folder is read for every track'
plant "$LB" 's|return new String(bytes, LEGACY);|return new String(bytes, StandardCharsets.UTF_8);|' 'an old Windows-encoded file is read as UTF-8, so its accents come out as junk'
plant "$LB" 's|if (Files.exists(target)) {|if (false) {|' 'seeding overwrites a lyric file the player edited'

echo "=== plants: the cache over the file ==="
# The card asks sixty times a second. A cache that never re-reads freezes the words of the
# last song; one that re-reads every frame is a folder listing in the frame budget.
plant "$LH" 's|private static final long RECHECK_MILLIS = 1000L;|private static final long RECHECK_MILLIS = 3600000L;|' 'an edited lyric file is not picked up until the song changes'
plant "$LH" 's|if (!wanted.equals(key)) {|if (false) {|' 'the cache never notices the track changed, so the next song keeps the last one'"'"'s words'
plant "$LH" 's|if (found.equals(file) \&\& modified == stamp) {|if (found.equals(file)) {|' 'a file edited while its own song plays is never re-read'

echo "=== plants: the lyric panel ==="
# Three rows, one of them lit, inside a box that stays on screen. Every plant here is a
# legal drawing of the wrong thing, which is why the geometry has to be checked by number.
plant "$LP" 's|widest = Math.max(widest, canvas.textWidth(line.text()));|widest = canvas.textWidth(line.text());|' 'the width is taken from the last line in the file instead of the widest'
plant "$LP" 's|width = Math.max(MIN_WIDTH, Math.min(width, Math.min(MAX_WIDTH, guiWidth)));|width = Math.max(MIN_WIDTH, width);|' 'the panel can grow past the window and off the screen'
plant "$LP" 's|int placed = card.mirror() ? card.x() + card.width() - width : card.x();|int placed = card.x();|' 'a right-parked card'"'"'s words hang off its left edge instead of its right'
plant "$LP" 's|int first = current < 0 ? 0 : Math.max(0, Math.min(current - rows / 2, lyrics.lines().size() - rows));|int first = 0;|' 'the window never scrolls, so the panel is stuck on its first rows'
plant "$LP" 's|Math.min(current - rows / 2, lyrics.lines().size() - rows)|current - rows / 2|' 'at the end the window runs past the last line, leaving blank rows'
plant "$LP" 's|boolean singing = index == frame.current();|boolean singing = true;|' 'every line is painted as the one being sung'
plant "$LP" 's|int otherInk = Palette.withAlpha(currentInk, live ? 130 : 90);|int otherInk = currentInk;|' 'the neighbours are as bright as the line being sung'
plant "$LP" 's|String text = fit(canvas, line.text(), room);|String text = line.text();|' 'a line wider than the panel runs over its border instead of being cut'
plant "$LP" 's|int textX = frame.x() + (frame.width() - canvas.textWidth(text)) / 2;|int textX = frame.x() + PAD;|' 'the lines are left-aligned inside the panel instead of centred'
plant "$LP" 's|if (text.isEmpty()) {|if (false) {|' 'a timed gap is painted as an empty line instead of skipped'
plant "$LP" 's|band = HudLayout.dim(band);|band = band;|' 'a paused lyric panel stays fully lit'
plant "$LP" 's|int above = card.y() - GAP - height;|int above = card.y() + card.height() + GAP;|' 'a card at the bottom of the screen has its words drawn off the bottom edge'
plant "$LP" 's|int y = roomBelow >= roomAbove ? below : above;|int y = below;|' 'a squeezed panel always covers the card from below, even when above is the emptier half'

echo "=== plants: the words as their own box ==="
# A panel that can go anywhere is four numbers that must each be obeyed, and a grip that
# must be exactly where it is painted. Every plant here draws a legal panel in the wrong
# place, which is why the numbers are checked instead of the picture.
plant "$LP" 's|int x = options.x() < 0 ? placed : options.x();|int x = placed;|' 'words dragged sideways snap back to the card'"'"'s edge'
plant "$LP" 's|int y = options.y() < 0 ? place(card, height, guiHeight) : options.y();|int y = place(card, height, guiHeight);|' 'words dragged up snap back under the card'
plant "$LP" 's|width = Math.max(MIN_WIDTH, Math.min(options.width(), guiWidth));|width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, guiWidth));|' 'a width the player dragged is ignored for the automatic one'
plant "$LP" 's|int height = options.height() <= 0 ? height(rows) : options.height();|int height = height(rows);|' 'a box dragged taller is snapped back to three rows'
plant "$LP" 's|return y + (height - block) / 2 + row \* (LINE + LINE_GAP);|return y + PAD + row \* (LINE + LINE_GAP);|' 'a tall box stacks its rows at the top instead of centring them'
plant "$LP" 's|int rows = options.lines() <= 0 ? LINES : Math.min(MAX_LINES, Math.max(1, options.lines()));|int rows = LINES;|' 'the line count is ignored, so the settings row does nothing'
plant "$LP" 's|x = Math.max(0, Math.min(x, guiWidth - width));|x = Math.max(0, x);|' 'a box dragged past the right edge is drawn half off the screen'
plant "$LP" 's|y = Math.max(0, Math.min(y, guiHeight - height));|y = Math.max(0, y);|' 'a box dragged past the bottom edge is drawn off the bottom'
plant "$LP" 's|int size = Math.min(GRIP, Math.min(width, height));|int size = GRIP;|' 'a grip bigger than a one-line box hangs off it'
plant "$LP" 's|return new HudLayout.Rect(x + width - size, y + height - size, size, size);|return new HudLayout.Rect(x + width - GRIP, y + height - GRIP, size, size);|' 'the grip is not in the corner it is painted in'
plant "$LP" 's|return grip().holds(mx, my);|return true;|' 'every click on the box resizes it, so the words can never be dragged'

echo "=== plants: the lyric panel's custom UI ==="
plant "$LP" 's|if (style.panel()) {|if (true) {|' 'the panel stays drawn after the player switches it off'
plant "$LP" 's|if (style.opacity() > 0.0) {|if (false) {|' 'an opacity set by hand never reaches the band'
plant "$LP" 's|Math.max(MIN_OPACITY, style.opacity())|style.opacity()|' 'a faint opacity can make the words disappear'
plant "$LP" 's|if (style.editing()) {|if (false) {|' 'the move screen paints no grip for the words'
plant "$LP" 's|if (style.editing()) {|if (true) {|' 'every ordinary panel shows the editor'"'"'s grip and outline'
plant "$LP" 's|canvas.text(text, textX, textY, otherInk, !style.panel());|canvas.text(text, textX, textY, otherInk, false);|' 'a dim neighbour on a panel with no band is drawn with no shadow'
plant "$LP" 's|canvas.text(text, textX, textY, otherInk, !style.panel());|canvas.text(text, textX, textY, otherInk, true);|' 'every row on a panel takes the sung line'"'"'s shadow'
plant "$LP" 's|int band = skin.panel();|int band = DeckSkin.BUILT_IN.panel();|' 'the words ignore the deck the card is wearing'

echo "=== plants: the placement editor ==="
plant "$S" 's|panel.gripHolds(mx, my)|false|' 'the corner of the words is never grabbed to resize'
plant "$S" 's|config.lyricsX = Integer.toString(panel.x());|config.lyricsX = Integer.toString(0);|' 'a resize does not pin the box it grew from, so the words slide away'
plant "$S" 's|config.lyricsHeight = homeLyricsHeight;||' 'ESC keeps the height a mis-drag left behind'

echo "=== plants: the lyric knobs through the card and the command ==="
plant "$N" 's|config.lyricsLines()|LyricsLayout.LINES|' 'the card ignores the line count the settings page writes'
plant "$N" 's|config.lyricsOpacity()|0.0|' 'the card ignores the opacity the settings page writes'
plant "$N" 's|!words.present() && editing()|!words.present()|' 'the demo words appear outside the move screen'
plant "$N" 's|this.words = panel;|this.words = null;|' 'the editor is handed no frame to grab'
plant "$CM" 's|literal("place")|literal("where")|' 'the command cannot put the words at a point'
plant "$CM" 's|lyricSize(client, context)|lyricPlace(client, context)|' 'the command cannot set the words'"'"' size'
plant "$CM" 's|literal("reset")|literal("clear")|' 'the command cannot put the lyric knobs back to auto'
plant "$V" 's|props.setProperty("lyricsWidth", lyricsWidth);||' 'a width the player set is lost on the next restart'
plant "$V" 's|AUTO.equalsIgnoreCase(value)|value.isEmpty()|' 'the word auto in the file lands the words at zero'
plant "$V" 's|raw.endsWith("%")|false|' 'an opacity written with its percent sign is read as auto'

echo "=== plants: the wiring from the card to the folder ==="
plant "$N" 's|LyricsLayout.draw(canvas, panel, accent, playing.playing(),|// the panel is measured and never painted|' 'the card measures the words and never paints them'
plant "$N" 's|MediaConfig.lyrics(), playing.artist(), playing.title(),|MediaConfig.lyrics(), playing.artist(), playing.album(),|' 'the card looks the words up under the album name instead of the song'
plant "$CM" 's|ClientCommands.literal("lyrics")|ClientCommands.literal("lyric")|' 'the lyrics folder cannot be reached from the command'
plant "$V" 's|LyricsLibrary.seedMissing(lyrics());||' 'the lyrics folder is never created, so nothing shows the player the format'

echo "=== plants: the online lookup, the mod's only socket ==="
plant "$LF" 's|urls.add(base + "&duration=" + seconds);|urls.add(0, base);|' 'the first address carries no length, so a live take and the studio one are the same song'
plant "$LF" 's|urls.add(base);|urls.add(base + "&duration=" + seconds);|' 'the retry pins the length again, so a remaster a second off is never found'
plant "$LF" 's|if (seconds > 0L) {|if (true) {|' 'an unknown length is sent as zero seconds instead of left out'
plant "$LF" 's|point == '"'"'~'"'"')|point == '"'"'~'"'"' \|\| point == '"'"'%'"'"')|' 'a percent sign is left raw, so the name is not escaped the way the query needs'
plant "$LF" 's|getBytes(StandardCharsets.UTF_8)|getBytes(StandardCharsets.ISO_8859_1)|' 'an accented name is sent as one byte, so the service reads a question mark'
plant "$LF" 's|return line.string("syncedLyrics", "");|return line.string("syncedLyrics", "").replace("\\n", " ");|' 'the answer is flattened onto one line, so every line lands at once'
plant "$LF" 's|if (line.bool("instrumental", false)) {|if (false) {|' 'an instrumental answer is drawn as the words of the track'
plant "$LF" 's|, "_").trim();|, "?").trim();|' 'a fetched file is named with a character Windows forbids, so it cannot be kept'
plant "$LF" 's|String name = by.isEmpty() ? of : by + " - " + of;|String name = of;|' 'the artist is dropped from the file name, so the matcher never finds it on the next play'
plant "$LF" 's|name = trimEnd(name);|name = name;|' 'a name ending in a dot is kept, and Windows quietly drops the dot'
plant "$LF" 's|return (name.isEmpty() ? "lyrics" : name)|return (name.isEmpty() ? "" : name)|' 'a track with no name makes a bare ".lrc" instead of a usable fallback'
plant "$LF" 's|if (name.length() > MAX_NAME) {|if (false) {|' 'a very long title makes a path Windows refuses'
plant "$LF" 's|write(directory, fileName(artist, title), text);|// the answer is never written down|' 'a hit is shown once and thrown away, so the next play asks the network again'
plant "$LF" 's|if (!lyrics.present()) {|if (false) {|' 'words with no timestamps are saved as a file the panel cannot use'
plant "$LF" 's|if (!Files.exists(target)) {|if (true) {|' 'a fetched file overwrites the timing the player wrote'
plant "$LF" 's|return get(urls, 0)|return CompletableFuture.completedFuture(get(urls, 0).join())|' 'the lookup is waited on where it is started, so the frame stalls until the answer arrives'
plant "$LF" 's|connectTimeout(TIMEOUT)|connectTimeout(Duration.ofSeconds(30))|' 'a connection that hangs holds its thread for half a minute'
plant "$LF" 's|.header("User-Agent", AGENT)|.header("X-Client", AGENT)|' 'the service is not told which program is asking'
plant "$LF" 's|public static final String HOST = "https://lrclib.net";|public static final String HOST = "http://lyrics.example.com";|' 'the words are asked for from somewhere other than the one host this mod knows'
plant "$LH" 's|if (!online) {|if (!online \&\& false) {|' 'online off is still a request, so a switch that says off is not off'
plant "$LH" 's|return online(directory, artist, title, online, duration);|return online(directory, artist, title, true, duration);|' 'a miss asks the network even with the switch off'
plant "$LH" 's|pending = fetcher.request(directory, artist, title, duration);|pending = fetcher.request(directory, artist, title, -1.0);|' 'the lookup goes out without the length the card knows'
plant "$LH" 's|asked = track;|asked = "";|' 'the same track is asked for again on every frame, and a failure is retried forever'
plant "$LH" 's|if (!online) {|if (!online) { fetched = Lyrics.EMPTY;|' 'turning the switch off drops words that already arrived'
plant "$LH" 's|if (!track.equals(asked)) {|if (false) {|' 'a new track keeps the last one'"'"'s words'
plant "$LH" 's|} catch (CompletionException e) {|} catch (CompletionException e) { if (e != null) return Lyrics.parse("[00:01.00]x");|' 'a failed lookup paints an invented line instead of nothing'
plant "$LY" 's|if (stamp == null) {|if (true) {|' 'a word stamp is kept in the line, so the timing is drawn across the screen and the words never fill'
plant "$LY" 's|.trim();|.replaceAll("<[^>]*>", " ").trim();|' 'anything in angle brackets is eaten, so a line about "<3" loses its words'
plant "$N" 's|config.onlineLyrics, playing.duration());|true, playing.duration());|' 'the card ignores the online switch and always asks'
plant "$N" 's|LyricsLayout.draw(canvas, panel, accent, playing.playing(),|LyricsLayout.draw(canvas, panel, accent, playing.playing() \|\| LyricsFetcher.HOST.isEmpty(),|' 'the draw path reaches for the fetcher itself instead of asking the cache'
plant "$CM" 's|online(client, context, false)|online(client, context, true)|' 'the command has no way to turn the lookup off'
plant "$V" 's|props.setProperty("onlineLyrics"|props.setProperty("onlineLyric"|' 'the online switch is written under a name that is never read back'

echo "=== plants: the karaoke clock and its stage ==="
# The sweep is arithmetic over the player's own file, so every mistake here is a plausible
# fill at the wrong moment. The word offsets, the estimate, the spread between two stamps
# and the count-in each get a plant that moves the fill without breaking the draw.
plant "$LY" 's|marks.add(stamp - at);|marks.add(stamp);|' 'a word stamp is kept as an absolute second, so a shifted line fills on the wrong beat'
plant "$LY" 's|seconds == stamps.get(0) ? body.words() : List.of()|body.words()|' 'a chorus written once and repeated reuses word beats that all landed before it'
plant "$LY" 's|map\[index\] = text.length();|map[index] = index;|' 'a word offset is a position in the raw line, so folded punctuation shifts every word after it'
plant "$KR" 's|return start + Math.min(gap, Math.max(MIN_SPAN, estimate));|return start + Math.max(gap, estimate);|' 'a line before a long silence fills across the whole silence'
plant "$KR" 's|if (gap < MIN_SPAN) {|if (false) {|' 'two lines stamped on top of each other leave the first one unsung'
plant "$KR" 's|PER_CHARACTER = 0.16|PER_CHARACTER = 0.30|' 'the fill runs past its line and sits finished while the words go on'
plant "$KR" 's|if (text.charAt(index) != '"'"' '"'"') {|if (true) {|' 'a space is counted as sung, so a spaced line fills slower than a packed one'
plant "$KR" 's|times\[index\] = start + (stop - start) \* ((index - from) / (double) (to - from));|times[index] = start;|' 'every letter between two stamps lights with the first of them'
plant "$KR" 's|return Math.min(last, low + Math.max(0.0, part));|return low;|' 'the fill jumps a whole letter instead of cutting through it'
plant "$KR" 's|(int) Math.ceil(fraction - 1e-6)|(int) Math.floor(fraction)|' 'a letter waits to be finished before it lights'
plant "$KR" 's|edge += (int) Math.round(canvas.textWidth(text.substring(whole, whole + 1)) \* part);|edge += 0;|' 'the bright copy stops at the start of the letter being sung'
plant "$KR" 's|COUNT_IN = 3|COUNT_IN = 4|' 'the count-in starts a second too early'
plant "$KL" 's|for (int candidate = MAX_SCALE; candidate > 1; candidate--) {|for (int candidate = 2; candidate > 1; candidate--) {|' 'the words are never drawn bigger than double'
plant "$KL" 's|MAX_SCALE = 3;|MAX_SCALE = 4;|' 'the stage zooms past the size that still fits a line'
plant "$KL" 's|if (widest \* candidate + MARGIN \* 2 <= width) {|if (widest * candidate <= width) {|' 'a line that fits the screen ends up touching both walls'
plant "$KL" 's|int logical = Math.max(1, width / zoom);|int logical = Math.max(1, width);|' 'the stage is centred in screen pixels while it is drawn three times as large'
plant "$KL" 's|int currentY = Math.max(0, (height \* 44 / 100) / zoom);|int currentY = Math.max(0, height / 2 / zoom);|' 'the sung line sits dead centre, on top of the count-in'
plant "$KL" 's|return Math.max(MARGIN, (room - canvas.textWidth(text)) / 2);|return Math.max(MARGIN, room - canvas.textWidth(text));|' 'the words are pinned to the right wall instead of centred'
plant "$KL" 's|int index = lyrics.indexAt(seconds);|int index = Math.max(0, lyrics.indexAt(seconds));|' 'before the first stamp the stage sings the first line early'
plant "$KL" 's|String current = LyricsLayout.fit(canvas, line.text(), room);|String current = line.text();|' 'a line too wide for the stage runs over the screen edge'
plant "$KL" 's|next, centred(canvas, next, logical), currentY + LyricsLayout.LINE + NEXT_GAP,|next, centred(canvas, next, logical), currentY + LyricsLayout.LINE,|' 'the preview touches the line being sung'
plant "$KL" 's|canvas.text(text, stage.currentX(), stage.currentY(), dim, true);|canvas.text(text, stage.currentX(), stage.currentY(), ink, true);|' 'the whole sung line is lit before the voice reaches it'
plant "$KL" 's|canvas.text(glyph, x, stage.currentY(), ink, true);|canvas.text(glyph, stage.currentX(), stage.currentY(), ink, true);|' 'every lit letter is drawn at the start of the line, in a heap'
plant "$KL" 's|int lit = Karaoke.bright(stage.fraction(), text.length());|int lit = text.length();|' 'the line is lit whole the moment it starts'
plant "$KL" 's|int ink = live ? Palette.WHITE : Palette.scale(Palette.WHITE, HudLayout.PAUSED);|int ink = Palette.WHITE;|' 'a paused track is painted as though the voice were still on it'
plant "$KL" 's|stage.countdownX(), stage.countdownY(),|stage.countdownX(), stage.currentY(),|' 'the count-in is drawn on top of the words'
plant "$KL" 's|canvas.text(header, centred(canvas, header, width), HEADER_Y, accent, true);|canvas.text(header, 0, HEADER_Y, accent, true);|' 'the track name hangs off the left edge'
plant "$KL" 's|int y = Math.max(0, height - HINT_Y - LyricsLayout.LINE);|int y = Math.max(0, height - HINT_Y);|' 'the way out runs off the bottom edge'
plant "$KL" 's|int y = Math.max(0, (height - LyricsLayout.LINE) / 2);|int y = Math.max(0, height / 2);|' 'the message is centred on its top edge rather than its middle'
plant "$KL" 's|canvas.fill(0, 0, width, height, Palette.withAlpha(WASH, 190));|canvas.fill(0, 0, width / 2, height, Palette.withAlpha(WASH, 190));|' 'the wash covers half the window'
plant "$KS" 's|return false;|return true;|' 'opening the karaoke view pauses the world, which stops the song it is about'
plant "$KS" 's|event.key() == InputConstants.KEY_J|event.key() == InputConstants.KEY_K|' 'the key that opened the view does not close it'
plant "$KS" 's|super.extractRenderState(graphics, mouseX, mouseY, delta);|this.renderBackground(graphics, mouseX, mouseY, delta);|' 'the karaoke view asks for a second blur and takes the client down'
plant "$KS" 's|Lyrics words = hud.wordsFor(playing);|Lyrics words = Lyrics.EMPTY;|' 'the view never asks the cache that already has the words'
plant "$KS" 's|graphics.pose().scale(stage.scale(), stage.scale());|graphics.pose().scale(1, 1);|' 'the stage is measured for a zoom it is then drawn without'
plant "$C" 's|if (screen instanceof KaraokeScreen) {|if (false) {|' 'the card and its buttons sit on top of the karaoke words'
plant "$C" 's|settingsKey, karaokeKey};|settingsKey};|' 'the karaoke key is bound but never listed, so it cannot be changed'
plant "$C" 's|InputConstants.KEY_J|InputConstants.KEY_K|' 'the karaoke key is bound to a letter nobody would press for it'
plant "$C" 's|while (karaokeKey.consumeClick()) {|while (false) {|' 'the key is registered and never polled'
plant "$CM" 's|literal("karaoke")|literal("sing")|' 'the command cannot open the view the key opens'
plant "$N" 's|public Lyrics wordsFor(|private Lyrics wordsFor(|' 'the view has no way to ask the card for the words it already loaded'
plant "$G" 's|"key.musicdeck.karaoke": "Karaoke View",||' 'the karaoke key has no label in the language file'
plant "$G" 's|"musicdeck.karaoke.nowords": .*||' 'a track with no words is explained with a raw key'

echo "=== control run again (files must be back) ==="
echo "=== plants: the position the source keeps repeating ==="
plant "$MC" 's|anchor(running, now);|anchor(pos, now);|' 'a repeated position restarts the clock from the old second'
plant "$MC" 's|boolean moved = pos != lastRaw;|boolean moved = true;|' 'every sample counts as a tick, so a repeat drags the clock back'
plant "$MC" 's|double next = Math.abs(error) > SNAP_SECONDS|double next = error > -SNAP_SECONDS|' 'a backward seek is absorbed like jitter instead of obeyed'
plant "$MC" 's|public static final double SNAP_SECONDS = 3.0;|public static final double SNAP_SECONDS = 0.5;|' 'every wobble bigger than half a second is treated as a seek'
plant "$MC" 's|: Math.max(shown, running + clamp(|: Math.max(0.0, running + clamp(|' 'a correction may move the clock backwards'
plant "$MC" 's|error \* ABSORB|MAX_STEP|' 'the correction can push the clock past the sample it is correcting'
plant "$MC" 's@if (!anchored || !key.equals(track)) {@if (!anchored) {@' 'a new track inherits the clock of the one before it'
plant "$MC" 's|if (!raw.playing()) {|if (false) {|' 'a paused track keeps counting'
plant "$MC" 's|raw.duration() > 0.0 ? Math.min(position, raw.duration()) : position|position|' 'the clock runs past the end of the track'
plant "$MB" 's|clock.settle(NowPlaying.from(json))|NowPlaying.from(json)|' 'the bridge hands the raw sample straight to the card'
plant "$MB" 's|clock.reset();||' 'a dead helper leaves the old anchor behind'


javac -encoding UTF-8 -d build-mut $SRC && java -cp build-mut CheckHud | tail -1
diff -q "$L" build-mut/HudLayout.java && diff -q "$P" build-mut/Palette.java \
	&& diff -q "$S" build-mut/LayoutScreen.java && diff -q "$N" build-mut/NowPlayingHud.java \
	&& diff -q "$G" build-mut/en_us.json && diff -q "$B" build-mut/smtc-bridge.ps1 \
	&& diff -q "$A" build-mut/AlbumArt.java && diff -q "$K" build-mut/KeyBindScreen.java \
	&& diff -q "$D" build-mut/SettingsScreen.java && diff -q "$C" build-mut/MusicDeckClient.java \
	&& diff -q "$M" build-mut/MusicDeckModMenu.java && diff -q "$V" build-mut/MediaConfig.java \
	&& diff -q "$Y" build-mut/NowPlaying.java && diff -q "$O" build-mut/MusicDeck.java \
	&& diff -q "$F" build-mut/fabric.mod.json \
	&& diff -q "$I" build-mut/icon.png \
	&& diff -q "$SK" build-mut/DeckSkin.java \
	&& diff -q "$DL" build-mut/DeckLibrary.java \
	&& diff -q "$LY" build-mut/Lyrics.java \
	&& diff -q "$LB" build-mut/LyricsLibrary.java \
	&& diff -q "$LH" build-mut/LyricsCache.java \
	&& diff -q "$LP" build-mut/LyricsLayout.java \
	&& diff -q "$CM" build-mut/MusicDeckCommand.java \
	&& diff -q "$LF" build-mut/LyricsFetcher.java \
	&& diff -q "$KR" build-mut/Karaoke.java \
	&& diff -q "$KL" build-mut/KaraokeLayout.java \
	&& diff -q "$KS" build-mut/KaraokeScreen.java \
	&& diff -q "$MC" build-mut/MediaClock.java \
	&& diff -q "$MB" build-mut/MediaBridge.java \
	&& echo "sources restored"
echo "plants run this pass: $((plants - skip_until)) of $plants"
echo "surviving plants: $survivors"
