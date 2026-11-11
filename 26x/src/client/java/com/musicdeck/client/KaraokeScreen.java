package com.musicdeck.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * The karaoke view: the line being sung, big, with the next one under it.
 *
 * It is a screen rather than a second overlay because it owns the whole window while it is
 * open, and because the player expects to leave it the way they leave everything else. It
 * is not a pause screen: the song has to keep playing, and the world behind it keeping on
 * moving is the difference between a karaoke view and a menu.
 *
 * Nothing here decides any geometry. {@link KaraokeLayout} measures the stage and paints it
 * through {@link HudCanvas}, exactly like the card and the panel, which is what lets the
 * gate suite check the placement and the lit prefix with no window at all.
 */
public final class KaraokeScreen extends Screen {
	private final NowPlayingHud hud;
	private final MediaBridge bridge;

	public KaraokeScreen(NowPlayingHud hud, MediaBridge bridge) {
		super(Component.translatable("musicdeck.karaoke.title"));
		this.hud = hud;
		this.bridge = bridge;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// The same key that opened it closes it, so a player who pressed J twice is not left
		// hunting for ESC. ESC itself is left to the screen base class.
		if (event.key() == InputConstants.KEY_J) {
			this.onClose();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// No renderBackground(): the renderer has already blurred the world once this frame.
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		NowPlaying playing = bridge.state();
		Lyrics words = hud.wordsFor(playing);
		int accent = hud.accent(playing);
		String header = playing.hasTrack() ? playing.label(64) : "";
		String message = "";
		if (!playing.hasTrack()) {
			message = Component.translatable("musicdeck.karaoke.none").getString();
		} else if (!words.present()) {
			message = Component.translatable("musicdeck.karaoke.nowords").getString();
		}

		HudCanvas canvas = new ScreenCanvas(graphics, this.font);
		KaraokeLayout.chrome(canvas, header,
			Component.translatable("musicdeck.karaoke.hint").getString(), message,
			this.width, this.height, accent);
		if (!message.isEmpty()) {
			return;
		}

		KaraokeLayout.Stage stage = KaraokeLayout.stage(canvas, words, playing.position(),
			this.width, this.height);
		// The stage is measured in its own units, so the zoom is applied to the pose rather
		// than to every coordinate. Nothing inside the stage clips: a scissor is set in screen
		// pixels and would not follow the pose, so the stage lights whole characters instead.
		graphics.pose().pushMatrix();
		graphics.pose().scale(stage.scale(), stage.scale());
		KaraokeLayout.draw(canvas, stage, accent, playing.playing());
		graphics.pose().popMatrix();
	}

	/** {@link HudCanvas} over the screen's own drawing calls, which is the same surface as the card. */
	private static final class ScreenCanvas implements HudCanvas {
		private final GuiGraphicsExtractor graphics;
		private final Font font;

		ScreenCanvas(GuiGraphicsExtractor graphics, Font font) {
			this.graphics = graphics;
			this.font = font;
		}

		@Override
		public int textWidth(String text) {
			return text.isEmpty() ? 0 : font.width(text);
		}

		@Override
		public void text(String text, int x, int y, int colour, boolean shadow) {
			graphics.text(font, text, x, y, colour, shadow);
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
			// The stage draws no album tile: there is no card on this screen to put it in.
		}
	}
}
