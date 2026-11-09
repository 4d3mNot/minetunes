# MineTunes — 26.x tree

The same mod as the 1.21.11 project one level up, built for the Minecraft 26.x line. One source
tree, three targets, one jar each.

```
./gradlew build -Pminecraft_version=26.1.2
./gradlew build -Pminecraft_version=26.2
./gradlew build -Pminecraft_version=26.3
```

`minecraft_version` defaults to `26.3`, so a bare `./gradlew build` gives you that one. The jar
lands in `build/libs/minetunes-1.0.0+<target>.jar`, and its `fabric.mod.json` carries the target's
own `minecraft` range and version suffix, so the three can sit in the same folder without being
loaded by the wrong game.

## Targets

| Target | Fabric API | Mod Menu | `minecraft` range | Java |
| --- | --- | --- | --- | --- |
| 26.1.2 | 0.155.3+26.1.2 | 18.0.2 | `>=26.1.2 <26.2` | 25 |
| 26.2 | 0.161.0+26.2 | 20.0.3 | `>=26.2 <26.3` | 25 |
| 26.3 | 0.165.0+26.3 | 21.0.0 | `>=26.3 <26.4` | 25 |

Only those numbers differ per target, plus one small shim file (below). Bump a row in
`build.gradle`'s `targets` map to move a target onto a newer Fabric API or Mod Menu.

## Why this tree is built differently from the 1.21.11 one

Since 26.x, Minecraft ships unobfuscated. That removes the whole remapping step:

- **`net.fabricmc.fabric-loom`** without the `-remap` suffix, and **no `mappings` line** at all —
  there are no Yarn mappings past 1.21.11 and Fabric intermediary is `0.0.0` for every 26.x build.
- Fabric API and the loader are plain `implementation` dependencies; nothing needs remapping on the
  way in.
- **Java 25** everywhere (`options.release = 25`), which is what the 26.x targets themselves require.

## What actually changed in the code

The game's drawing and screen APIs moved; the mod's own logic did not. Compiled differences are
confined to a handful of call shapes:

| 1.21.11 | 26.x |
| --- | --- |
| `GuiGraphics` | `GuiGraphicsExtractor` (extract, then `GuiRenderer` paints) |
| `Screen.render(graphics, mouseX, mouseY, delta)` | `extractRenderState(graphics, mouseX, mouseY, delta)` |
| `graphics.drawString(...)` / `drawCenteredString(...)` | `text(...)` / `centeredText(...)` |
| `NativeImage.read(NativeImage.Format.RGBA, in)` | `NativeImage.read(in)` |
| `player.displayClientMessage(msg, true)` | `player.sendOverlayMessage(msg)` |
| `ScreenEvents.afterRender` | `ScreenEvents.afterExtract` |
| `KeyBindingHelper.registerKeyBinding` (`fabric-key-binding-api-v1`) | `KeyMappingHelper.registerKeyMapping` (`fabric-key-mapping-api-v1`) |
| `ClientCommandManager` | `ClientCommands` |

Two calls also changed *inside* the 26.x line, so they live behind
`src/client/versions/<target>/java/com/musicdeck/client/Platform.java`:

| | 26.1.2 | 26.2 / 26.3 |
| --- | --- | --- |
| open screen | `minecraft.screen` / `minecraft.setScreen` | `minecraft.gui.screen()` / `gui.setScreen` |
| key type constant | `InputConstants.Type.KEYSYM` | `Type.KEYSYM` (26.2), `Type.KEYBOARD` (26.3) |

`build.gradle` adds the matching folder to the client source set, so exactly one `Platform` compiles
per target. Everything else in `src/` is shared with nothing version-specific in it.

One more thing that changed shape rather than name: the 26.x screen base class calls
`extractBackground` itself inside `extractRenderStateWithTooltipAndSubtitles`, so a screen that asks
for its own background blurs twice per frame. None of the mod's three screens do — `CheckHud` gates
that, because the 1.21.11 form of the same mistake was a hard crash.

## Verifying without launching the game

Same shape as the 1.21.11 tree — the layout is pure Java over a draw-op sink, so a bare JDK runs it:

```
javac -encoding UTF-8 -d build-gate tools/CheckHud.java \
  src/client/java/com/musicdeck/client/{HudCanvas,HudLayout,NowPlaying,Palette,JsonLine,DeckSkin,DeckLibrary,Lyrics,LyricsLibrary,LyricsCache,LyricsFetcher,LyricsLayout}.java
java -cp build-gate CheckHud
```

`tools/CheckHud.java` here also reads the three `Platform.java` files as text and fails if a shim
names the API its own version does not have. 620 checks.

```
bash tools/mutate-hud.sh
```

265 plants across all three targets. The last line must read `surviving plants: 0`; a `SKIP` or
`NOT-APPLIED` line means an anchor went stale and that coverage is gone, so read those too.

## Status

Compiled for all three targets, gated, and mutation-tested on this machine. Nothing here has been
launched in a 26.x client — there is no 26.x instance installed, so in-game behaviour (the HUD, the
PowerShell bridge, click-through) is unproven on 26.x in the same way it was unproven on 1.21.11
until the first launch there.
