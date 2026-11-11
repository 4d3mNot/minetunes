## A clock that only goes forward

1.2.1 fixes the twitch: the elapsed time on the card, the progress bar under it and the karaoke sweep
all drew their position from the same number, and that number used to step backwards several times a
second. Spotify reports a whole second and then repeats it about three times before the next one, and
each repeat used to restart the estimate, so the clock stuttered and the lyric sweep jumped back with
it. Song position is now smoothed by its own clock.

### What changed
- A repeated sample no longer restarts the estimate; it is treated as the same reading it is.
- Small disagreements between a new sample and the running clock are absorbed in steps of at most
  0.12 s, so the number moves smoothly instead of snapping.
- The displayed position can never go backwards while the track is playing, even when two samples
  arrive in the same millisecond.
- A real seek still lands at once: a jump larger than three seconds is obeyed immediately, forwards or
  backwards.
- Changing track resets the anchor, so a new song never inherits the position of the one before it.
- Pausing freezes the clock, and resuming continues from where it stopped.
- The clock is cleared when the helper process dies or is restarted, so a stale anchor cannot survive a
  reconnect.
- Nothing runs past the end of the track, and a track with an unknown length is left untouched.

### Compatibility
One jar per Minecraft version: `1.21.11`, `26.1.2`, `26.2` and `26.3`. Fabric Loader 0.19.2+ (0.19.3
on 26.x), Fabric API, Java 21+ on 1.21.11 and Java 25 on 26.x. Windows 10/11 only, client-side;
nothing is sent to the Minecraft server.

### Verified
735 checks pass on 1.21.11 and 745 on the 26.x tree (the extra ten are its per-target checks), with
342 planted defects on 1.21.11 and 351 on 26.x — every one caught by a named check. The clock has its
own twenty: a repeated sample, a backward and a forward seek, a pause, a new track, a stale reader,
the end cap, a helper that dies, and the bridge actually routing every sample through it.
