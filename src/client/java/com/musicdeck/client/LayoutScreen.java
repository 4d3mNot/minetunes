package com.musicdeck.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The placement editor: the card and the lyric panel, and the mouse free to move both.
 *
 * It is a real {@code Screen} rather than a mode because keybindings stop firing the
 * moment any screen opens, and the whole point of moving the panel is to do it while
 * the game is not eating the mouse. Inside a screen both boxes are already drawn every
 * frame by the {@code afterRender} hook, so this class only has to answer the mouse and
 * keep the four lyric numbers honest.
 *
 * Dragging a box writes the file when the button goes up, Done keeps it, and ESC puts back
 * everything that was there when the editor opened, so a mis-drag cannot outlive the screen.
 * The words have their own position and size from the first drag: before that they follow
 * the card, which is what an untouched install shows.
 */
public final class LayoutScreen extends Screen {
	private static final int LEFT = 0;
	/** What the arrows move: the card, or the words. Set by the last box the mouse picked. */
	private static final int CARD = 0;
	private static final int WORDS = 1;
	/** What the held button is doing, if anything. */
	private static final int NONE = 0;
	private static final int MOVE = 1;
	private static final int RESIZE = 2;
	/** How far the arrow keys nudge: one pixel, or eight with shift. */
	private static final int NUDGE = 1;
	private static final int NUDGE_FAST = 8;
	private static final int BUTTON_H = 20;
	private static final int BUTTON_W = 100;
	private static final String HINT = "Drag either box. Shift+arrows resize. Done keeps it, ESC undoes.";

	private final NowPlayingHud hud;
	private final MediaConfig config;
	/** Where to go when the editor closes: the settings screen, or nowhere. */
	private final Screen back;
	private final String homeX;
	private final String homeY;
	private final String homeLyricsX;
	private final String homeLyricsY;
	private final String homeLyricsWidth;
	private final String homeLyricsHeight;
	private int action = NONE;
	private int target = CARD;
	private int grabX;
	private int grabY;

	public LayoutScreen(MediaConfig config, Screen back) {
		super(Component.literal("Move the panels"));
		this.hud = MusicDeckClient.instance().hud();
		this.config = config;
		this.back = back;
		this.homeX = config.hudX;
		this.homeY = config.hudY;
		this.homeLyricsX = config.lyricsX;
		this.homeLyricsY = config.lyricsY;
		this.homeLyricsWidth = config.lyricsWidth;
		this.homeLyricsHeight = config.lyricsHeight;
	}

	@Override
	protected void init() {
		super.init();
		this.addRenderableWidget(Button.builder(Component.literal("Done"),
			button -> this.minecraft.setScreen(back))
			.bounds(this.width / 2 - BUTTON_W / 2, this.height - 40, BUTTON_W, BUTTON_H).build());
	}

	/** A paused world would stop the very music the panel is supposed to control. */
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		// No renderBackground() here. Since 1.21.11 the renderer applies the screen's own
		// blur before this is called, and a second one in the same frame throws
		// IllegalStateException("Can only blur once per frame") and takes the game down.
		super.render(graphics, mouseX, mouseY, delta);
		hud.renderOverScreen(graphics, mouseX, mouseY);
		graphics.drawCenteredString(this.font, status(), this.width, 12, Palette.withAlpha(Palette.WHITE, 190));
		graphics.drawCenteredString(this.font, HINT, this.width, this.height - 14,
			Palette.withAlpha(Palette.WHITE, 150));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == LEFT) {
			double mx = event.x();
			double my = event.y();
			LyricsLayout.Frame panel = hud.lastLyrics();
			// The words are painted after the card, so they are picked first: a click on the
			// corner of a panel that overlaps the card must resize, not drag the card behind it.
			if (panel != null && panel.gripHolds(mx, my)) {
				target = WORDS;
				action = RESIZE;
				grabX = (int) Math.round(mx) - (panel.x() + panel.width());
				grabY = (int) Math.round(my) - (panel.y() + panel.height());
				return true;
			}

			if (panel != null && panel.holds(mx, my)) {
				target = WORDS;
				action = MOVE;
				grabX = (int) Math.round(mx) - panel.x();
				grabY = (int) Math.round(my) - panel.y();
				return true;
			}

			HudLayout.Frame frame = hud.lastFrame();
			if (frame.holds(mx, my)) {
				// Grab relative to where the card actually is, not where the file says it
				// might be: near an edge the layout clamps it back and an absolute offset
				// would let the panel slip out from under the pointer.
				target = CARD;
				action = MOVE;
				grabX = (int) Math.round(mx) - frame.x();
				grabY = (int) Math.round(my) - frame.y();
				return true;
			}
		}

		// Whatever the boxes did not swallow belongs to the widgets underneath. Skipping this
		// is how a screen ends up drawing a button nobody can press: the press happens here.
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (action == NONE) {
			return super.mouseDragged(event, deltaX, deltaY);
		}

		int mx = (int) Math.round(event.x());
		int my = (int) Math.round(event.y());
		if (action == RESIZE) {
			resizeTo(mx - grabX, my - grabY);
		} else if (target == CARD) {
			moveCard(mx - grabX, my - grabY);
		} else {
			moveWords(mx - grabX, my - grabY);
		}

		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (action == NONE) {
			return super.mouseReleased(event);
		}

		action = NONE;
		config.save();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			config.hudX = homeX;
			config.hudY = homeY;
			config.lyricsX = homeLyricsX;
			config.lyricsY = homeLyricsY;
			config.lyricsWidth = homeLyricsWidth;
			config.lyricsHeight = homeLyricsHeight;
			config.save();
			this.minecraft.setScreen(back);
			return true;
		}

		int step = event.hasShiftDown() ? NUDGE_FAST : NUDGE;
		LyricsLayout.Frame panel = hud.lastLyrics();
		if (target == WORDS && panel != null && event.hasShiftDown()) {
			// Shift is the resize modifier while the words are picked: the card has no size
			// of its own to change, so the same key keeps its fast-nudge meaning there.
			if (event.isLeft()) {
				resizeBy(-step, 0);
				return true;
			}

			if (event.isRight()) {
				resizeBy(step, 0);
				return true;
			}

			if (event.isUp()) {
				resizeBy(0, -step);
				return true;
			}

			if (event.isDown()) {
				resizeBy(0, step);
				return true;
			}
		}

		if (target == WORDS && panel != null) {
			if (event.isLeft()) {
				moveWords(panel.x() - step, panel.y());
				return true;
			}

			if (event.isRight()) {
				moveWords(panel.x() + step, panel.y());
				return true;
			}

			if (event.isUp()) {
				moveWords(panel.x(), panel.y() - step);
				return true;
			}

			if (event.isDown()) {
				moveWords(panel.x(), panel.y() + step);
				return true;
			}
		}

		HudLayout.Frame frame = hud.lastFrame();
		if (event.isLeft()) {
			moveCard(frame.x() - step, frame.y());
			return true;
		}

		if (event.isRight()) {
			moveCard(frame.x() + step, frame.y());
			return true;
		}

		if (event.isUp()) {
			moveCard(frame.x(), frame.y() - step);
			return true;
		}

		if (event.isDown()) {
			moveCard(frame.x(), frame.y() + step);
			return true;
		}

		return super.keyPressed(event);
	}

	/**
	 * Store the requested position of the card.
	 *
	 * Only the negative end needs a floor here: the card's own width and the window are
	 * what decide the far edge, and {@code HudLayout.frame} clamps to them every frame, so
	 * an over-large number is legal in the file and simply draws against the border.
	 */
	private void moveCard(int x, int y) {
		target = CARD;
		config.hudX = Integer.toString(Math.max(0, x));
		config.hudY = Integer.toString(Math.max(0, y));
	}

	/** The same for the words, which stopped following the card the moment this was called. */
	private void moveWords(int x, int y) {
		target = WORDS;
		config.lyricsX = Integer.toString(Math.max(0, x));
		config.lyricsY = Integer.toString(Math.max(0, y));
	}

	/**
	 * Drag the bottom-right corner to a new place.
	 *
	 * The top-left corner is pinned to where the panel is now, including when it was following
	 * the card: resizing a box that is still attached on one axis would otherwise slide it out
	 * from under the pointer as the width changed.
	 */
	private void resizeTo(int right, int bottom) {
		LyricsLayout.Frame panel = hud.lastLyrics();
		if (panel == null) {
			return;
		}

		target = WORDS;
		config.lyricsX = Integer.toString(panel.x());
		config.lyricsY = Integer.toString(panel.y());
		config.lyricsWidth = Integer.toString(clampWidth(panel, right - panel.x()));
		config.lyricsHeight = Integer.toString(clampHeight(panel, bottom - panel.y()));
	}

	private void resizeBy(int dw, int dh) {
		LyricsLayout.Frame panel = hud.lastLyrics();
		if (panel == null) {
			return;
		}

		target = WORDS;
		config.lyricsX = Integer.toString(panel.x());
		config.lyricsY = Integer.toString(panel.y());
		config.lyricsWidth = Integer.toString(clampWidth(panel, panel.width() + dw));
		config.lyricsHeight = Integer.toString(clampHeight(panel, panel.height() + dh));
	}

	private int clampWidth(LyricsLayout.Frame panel, int width) {
		return Math.max(LyricsLayout.MIN_WIDTH,
			Math.min(width, Math.max(LyricsLayout.MIN_WIDTH, this.width - panel.x())));
	}

	private int clampHeight(LyricsLayout.Frame panel, int height) {
		int floor = LyricsLayout.height(1);
		return Math.max(floor, Math.min(height, Math.max(floor, this.height - panel.y())));
	}

	private String status() {
		LyricsLayout.Frame panel = hud.lastLyrics();
		if (target == WORDS && panel != null) {
			return "Words   " + panel.x() + ", " + panel.y() + "   " + panel.width() + "x" + panel.height()
				+ "   " + panel.rows() + " lines";
		}

		HudLayout.Frame frame = hud.lastFrame();
		return "Card   " + frame.x() + ", " + frame.y();
	}

	/** Closing any other way keeps the card where it was left. */
	@Override
	public void removed() {
		persist();
		super.removed();
	}

	@Override
	public void onClose() {
		persist();
		super.onClose();
	}

	/**
	 * Write the position on the way out.
	 *
	 * Both hooks are needed because {@code Minecraft.setScreen} only calls {@code removed()}
	 * - {@code onClose()} is reached through {@code Screen.close()}/popGuiLayer - so saving
	 * solely in {@code onClose()} silently drops every arrow-key nudge followed by Done.
	 */
	private void persist() {
		action = NONE;
		config.save();
	}

	/** ESC is handled above, because cancelling has to undo the move before the screen dies. */
	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
