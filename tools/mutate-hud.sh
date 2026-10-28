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
SRC="tools/CheckHud.java $L $P src/client/java/com/musicdeck/client/HudCanvas.java src/client/java/com/musicdeck/client/NowPlaying.java src/client/java/com/musicdeck/client/JsonLine.java $SK $DL"

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

survivors=0
plant() { # file, sed-expr, needle-expected-in-output
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
	if cmp -s "$2" "$1"; then
		echo "NOT-APPLIED: $3   <-- the stand-in is already the file itself"
		survivors=$((survivors + 1))
		return
	fi
	cp "$2" "$1"
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
plant "$S" 's|super.render(graphics, mouseX, mouseY, delta);|this.renderBackground(graphics, mouseX, mouseY, delta);|' 'a screen asks for a second blur'
plant "$S" 's|return super.mouseClicked(event, doubleClick);|return false;|' 'a click the card refused never reaches the button under it'
plant "$N" 's|Minecraft.getInstance().screen instanceof LayoutScreen|Minecraft.getInstance().screen != null|' 'the card treats every open screen as the placement editor'
plant "$N" 's|private boolean editing() {|private boolean layout = true;\n\n\tprivate boolean editing() {|' 'the editing state is remembered again, so it can outlive the editor'
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
plant "$D" 's@this.minecraft.setScreen(back)@this.minecraft.setScreen(null)@' 'Done forgets which screen opened the settings'
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
plant "$C" 's@new SettingsScreen(config, minecraft.screen)@new SettingsScreen(config)@' 'the settings key opens with nothing behind it, so Done cannot go back to the inventory'
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
plant "$N" 's@graphics.guiWidth(), graphics.guiHeight()@graphics.guiHeight(), graphics.guiWidth()@' 'the card is measured against the screen height, so it never learns which side it is on'
plant "$N" 's@minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight()@minecraft.getWindow().getGuiScaledHeight(), minecraft.getWindow().getGuiScaledHeight()@' 'the click path measures the card against another screen than the painter does'

echo "=== control run again (files must be back) ==="
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
	&& echo "sources restored"
echo "surviving plants: $survivors"
