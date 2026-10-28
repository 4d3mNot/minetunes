package com.musicdeck.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

import com.musicdeck.MusicDeck;

/**
 * The now-playing card.
 *
 * All of the geometry lives in {@link HudLayout}; this class only adapts the game's
 * drawing calls to that layout, decides when the card should exist at all, and answers
 * the mouse.
 *
 * It is drawn in two places and never both in the same frame: as a HUD element while the
 * game owns the mouse, and over whatever screen is open, where the buttons are live.
 */
public final class NowPlayingHud {
	/** How long the border stays lit after the track changes. */
	private static final long FLASH_MILLIS = 700L;
	/** How long a clicked button stays lit, which is longer than the ack takes to arrive. */
	private static final long PRESS_MILLIS = 220L;
	/** Once the draw has failed this many frames the overlay stops attempting it. */
	private static final int GIVE_UP = 60;
	private static final int LEFT = 0;

	/**
	 * What the placement editor shows when nothing is playing.
	 *
	 * The editor has to have a card to drag even with the media helper down, and a
	 * zero-text card would be a 60px sliver the player could not grab.
	 */
	private static final NowPlaying DEMO = new NowPlaying(true, "Now Playing", "Playing",
		"Move the panel", "Drag it anywhere", "", 60.0, 180.0, System.currentTimeMillis(), "");

	private final MediaConfig config;
	private final MediaBridge bridge;
	private final AlbumArt art;
	private int failures;
	private String trackKey = "";
	private long changedAt;
	private String asked = "";
	private int hover = -1;
	private int pressed = -1;
	private long pressedAt;
	private HudLayout.Frame frame = HudLayout.Frame.EMPTY;

	public NowPlayingHud(MediaConfig config, MediaBridge bridge, AlbumArt art) {
		this.config = config;
		this.bridge = bridge;
		this.art = art;
	}

	/**
	 * True while the placement editor is the open screen, which is the only time the card moves.
	 *
	 * Asked of the game rather than remembered in a field: {@code Minecraft.setScreen} runs
	 * {@code Screen.removed()} on the outgoing screen and that method does nothing, so a flag
	 * cleared in {@code onClose()} can survive the editor and leave the card permanently
	 * thinking it is still being dragged - inert buttons and a stuck outline.
	 */
	private boolean editing() {
		return Minecraft.getInstance().screen instanceof LayoutScreen;
	}

	public void render(GuiGraphics graphics, DeltaTracker delta) {
		// Only ever one pass per frame: whatever screen is open owns the card, because
		// that is the only place the mouse can reach it.
		if (Minecraft.getInstance().screen != null) {
			return;
		}

		draw(graphics, -1, -1, false);
	}

	/** The card over an open screen, where the mouse is free and the buttons answer. */
	public void renderOverScreen(GuiGraphics graphics, int mouseX, int mouseY) {
		draw(graphics, mouseX, mouseY, true);
	}

	/** Where the card actually landed last frame, after the editor moved it. */
	public HudLayout.Frame lastFrame() {
		return frame;
	}

	/**
	 * Take a click that landed on a button. True means the screen behind must not also
	 * see it, so only a real hit on a real control is ever consumed.
	 */
	public boolean click(double mouseX, double mouseY, int button) {
		if (button != LEFT || editing() || !usable()) {
			return false;
		}

		Minecraft minecraft = Minecraft.getInstance();
		NowPlaying playing = state();
		HudLayout.Frame hit = HudLayout.frame(measurer(minecraft), playing, options(),
			minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
		int index = hit.buttonAt(mouseX, mouseY);
		if (index < 0) {
			return false;
		}

		String command = HudLayout.command(index, mode(playing));
		pressed = index;
		pressedAt = System.currentTimeMillis();
		if (command != null && command.startsWith("repeat:")) {
			// Spotify will not read the mode back, so the card shows what was asked for.
			asked = command.substring("repeat:".length());
		}

		if (!bridge.send(command)) {
			bridge.tick();
		}

		return true;
	}

	private boolean usable() {
		if (editing()) {
			return true;
		}

		Minecraft minecraft = Minecraft.getInstance();
		NowPlaying playing = bridge.state();
		return minecraft.player != null && minecraft.level != null && playing.session()
			&& HudLayout.visible(playing, config.showHud, config.onlyWhenPlaying, false);
	}

	/** The session, or a stand-in while the editor needs something to drag. */
	private NowPlaying state() {
		NowPlaying playing = bridge.state();
		return editing() && !playing.hasTrack() ? DEMO : playing;
	}

	private void draw(GuiGraphics graphics, int mouseX, int mouseY, boolean overScreen) {
		if (failures > GIVE_UP) {
			return;
		}

		try {
			paint(graphics, mouseX, mouseY, overScreen);
		} catch (Throwable t) {
			failures++;
			if (failures <= 3) {
				MusicDeck.LOGGER.warn("Now-playing draw failed (failure " + failures + ")", t);
			} else if (failures == GIVE_UP + 1) {
				MusicDeck.LOGGER.warn("Now-playing stopped drawing after repeated failures");
			}
		}
	}

	private void paint(GuiGraphics graphics, int mouseX, int mouseY, boolean overScreen) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!usable()) {
			return;
		}

		NowPlaying playing = state();
		String key = playing.title() + "|" + playing.artist() + "|" + playing.album();
		if (!key.equals(trackKey)) {
			trackKey = key;
			changedAt = System.currentTimeMillis();
		}

		boolean hasArt = art.ready(minecraft, config.artStyle());
		int brand = playing.spotify() ? Palette.GREEN : Palette.GREY;
		// A deck that names its own colour is asking for that colour on every track, so it is
		// settled before the cover gets a say; the theme only decides what it did not pin.
		DeckSkin skin = config.deckSkin();
		int accent = skin.accentOr(Palette.themed(config.theme(), hasArt ? art.accent() : 0, brand));
		GuiCanvas canvas = new GuiCanvas(graphics, minecraft.font, art);
		HudLayout.Frame measured = HudLayout.frame(canvas, playing, options(),
			graphics.guiWidth(), graphics.guiHeight());
		frame = measured;
		boolean interactive = overScreen && config.showControls && !editing();
		hover = interactive ? measured.buttonAt(mouseX, mouseY) : -1;
		if (System.currentTimeMillis() - pressedAt > PRESS_MILLIS) {
			pressed = -1;
		}

		HudLayout.Input input = new HudLayout.Input(mode(playing), hover, pressed, interactive, editing());
		HudLayout.draw(canvas, measured, playing, input, accent, hasArt, flash(),
			(System.currentTimeMillis() - changedAt) / 1000.0);
	}

	/** What to paint behind the repeat button: the session's answer, else the last ask. */
	private String mode(NowPlaying playing) {
		return playing.repeat().isEmpty() ? asked : playing.repeat();
	}

	private HudLayout.Options options() {
		return new HudLayout.Options(config.x(), config.y(), config.deckSkin());
	}

	/** A canvas that only measures, for the click path where nothing is being drawn. */
	private HudCanvas measurer(Minecraft minecraft) {
		return new HudCanvas() {
			@Override
			public int textWidth(String text) {
				return text.isEmpty() ? 0 : minecraft.font.width(text);
			}

			@Override
			public void text(String text, int x, int y, int colour, boolean shadow) {
			}

			@Override
			public void clip(int x1, int y1, int x2, int y2) {
			}

			@Override
			public void unclip() {
			}

			@Override
			public void fill(int x1, int y1, int x2, int y2, int colour) {
			}

			@Override
			public void art(int x, int y, int size) {
			}
		};
	}

	private double flash() {
		long age = System.currentTimeMillis() - changedAt;
		return age >= FLASH_MILLIS ? 0.0 : 1.0 - age / (double) FLASH_MILLIS;
	}

	/** Draws {@link HudLayout}'s decisions with the game's own text renderer and atlas-free blit. */
	private static final class GuiCanvas implements HudCanvas {
		private final GuiGraphics graphics;
		private final Font font;
		private final AlbumArt art;

		GuiCanvas(GuiGraphics graphics, Font font, AlbumArt art) {
			this.graphics = graphics;
			this.font = font;
			this.art = art;
		}

		@Override
		public int textWidth(String text) {
			return text.isEmpty() ? 0 : font.width(text);
		}

		@Override
		public void text(String text, int x, int y, int colour, boolean shadow) {
			graphics.drawString(font, text, x, y, colour, shadow);
		}

		@Override
		public void clip(int x1, int y1, int x2, int y2) {
			graphics.enableScissor(x1, y1, x2, y2);
		}

		@Override
		public void unclip() {
			graphics.disableScissor();
		}

		@Override
		public void fill(int x1, int y1, int x2, int y2, int colour) {
			graphics.fill(x1, y1, x2, y2, colour);
		}

		@Override
		public void art(int x, int y, int size) {
			int tex = art.texSize();
			graphics.blit(RenderPipelines.GUI_TEXTURED, art.location(), x, y, 0.0F, 0.0F,
				size, size, tex, tex);
		}
	}
}
