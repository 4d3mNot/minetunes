## Sing along: the words fill in, and a stage for them

1.2.0 makes the lyrics keep time with the voice. The line being sung now fills in word by word, and `J` opens a full-window karaoke stage built for reading across a room.

### The sweep, in the panel you already have
- The line being sung is painted dim first and then again, bright, only up to the word the voice has reached — one extra draw call per frame, not a second font.
- Enhanced `.lrc` files that stamp words `<mm:ss.xx>` fill in exactly when those stamps land.
- Every other file still sweeps: each line's characters share the time slot evenly, and spaces count half, so the bright edge moves with the syllables rather than racing through the gaps.
- The sweep never runs past the line's own slot: a fast line rests bright until the next one starts instead of leaking into it, and every line gets at least 0.6 s of reading time.
- A repeated chorus that carries no stamps uses the same estimate rather than the timings of the first time it was sung.

### The karaoke stage
- `J` (rebindable) or `/music karaoke` opens a full-window view: the line being sung, big and centred, with the next line dimmed underneath.
- The stage zooms itself: 3×, 2× or 1×, whichever makes the widest of the two lines fit inside the window with its margin.
- A line that begins after a gap gets a 3-2-1 count-in in the panel's accent colour, above where the line will appear.
- It is not a pause screen: the song keeps playing and the world keeps moving behind it.
- `J` or `ESC` closes it, and the HUD card stays off the stage so its buttons cannot take a click meant for the window.
- Pausing dims the stage the same way it dims the card; the words stay in place.

### Everything else
- A seventh keybind, **Karaoke View**, default `J`, in the same **MineTunes** Controls category.
- The stage and the panel read the same lyrics cache, so they can never disagree about which file won.
- No new network traffic, no new files, no new settings: the view is always there, and a track with no timed words says so in the middle of the stage instead of showing an empty one.

### Compatibility
One jar per Minecraft version: `1.21.11`, `26.1.2`, `26.2` and `26.3`. Fabric Loader 0.19.2+ (0.19.3 on 26.x), Fabric API, Java 21+ on 1.21.11 and Java 25 on 26.x. Windows 10/11 only, client-side; nothing is sent to the Minecraft server.

### Verified
715 checks pass on 1.21.11 and 725 on the 26.x tree (the extra ten are its per-target checks), with
331 planted defects on 1.21.11 and 340 on 26.x — every one caught by a named check. The stage was also
proven with a rendered picture: the estimated sweep cut mid-line, the 3-2-1 count-in in the accent
colour, an enhanced-`.lrc` per-word sweep, the paused dim, and the no-words message.
