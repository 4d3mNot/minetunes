package com.musicdeck.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The placement editor: the card, and the mouse free to drag it.
 *
 * It is a real {@code Screen} rather than a mode because keybindings stop firing the
 * moment any screen opens, and the whole point of moving the panel is to do it while
 * the game is not eating the mouse. Inside a screen the card is already drawn every
 * frame by the {@code afterRender} hook, so this class only has to answer the mouse and
 * keep {@code hudX}/{@code hudY} honest.
 *
 * A drag writes the file when the button goes up, Done keeps it, and ESC puts back
 * whatever was there when the editor opened, so a mis-drag cannot outlive the screen.
 */
public final class LayoutScreen extends Screen {
	private static final int LEFT = 0;
	/** How far the arrow keys nudge: one pixel, or eight with shift. */
	private static final int NUDGE = 1;
	private static final int NUDGE_FAST = 8;
	private static final int BUTTON_H = 20;
	private static final int BUTTON_W = 100;
	private static final String HINT = "Drag the panel. Arrows nudge. Done keeps it, ESC puts it back.";

	private final NowPlayingHud hud;
	private final MediaConfig config;
	/** Where to go when the editor closes: the settings screen, or nowhere. */
	private final Screen back;
	private final String homeX;
	private final String homeY;
	private boolean dragging;
	private int grabX;
	private int grabY;

	public LayoutScreen(MediaConfig config, Screen back) {
		super(Component.literal("Move the now-playing panel"));
		this.hud = MusicDeckClient.instance().hud();
		this.config = config;
		this.back = back;
		this.homeX = config.hudX;
		this.homeY = config.hudY;
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
			HudLayout.Frame frame = hud.lastFrame();
			if (frame.holds(event.x(), event.y())) {
				// Grab relative to where the card actually is, not where the file says it
				// might be: near an edge the layout clamps it back and an absolute offset
				// would let the panel slip out from under the pointer.
				dragging = true;
				grabX = (int) Math.round(event.x()) - frame.x();
				grabY = (int) Math.round(event.y()) - frame.y();
				return true;
			}
		}

		// Whatever the card did not swallow belongs to the widgets underneath. Skipping this
		// is how a screen ends up drawing a button nobody can press: the press happens here.
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (!dragging) {
			return super.mouseDragged(event, deltaX, deltaY);
		}

		move((int) Math.round(event.x()) - grabX, (int) Math.round(event.y()) - grabY);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (!dragging) {
			return super.mouseReleased(event);
		}

		dragging = false;
		config.save();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			config.hudX = homeX;
			config.hudY = homeY;
			config.save();
			this.minecraft.setScreen(back);
			return true;
		}

		HudLayout.Frame frame = hud.lastFrame();
		int step = event.hasShiftDown() ? NUDGE_FAST : NUDGE;

		if (event.isLeft()) {
			move(frame.x() - step, frame.y());
			return true;
		}

		if (event.isRight()) {
			move(frame.x() + step, frame.y());
			return true;
		}

		if (event.isUp()) {
			move(frame.x(), frame.y() - step);
			return true;
		}

		if (event.isDown()) {
			move(frame.x(), frame.y() + step);
			return true;
		}

		return super.keyPressed(event);
	}

	/**
	 * Store the requested position.
	 *
	 * Only the negative end needs a floor here: the card's own width and the window are
	 * what decide the far edge, and {@code HudLayout.frame} clamps to them every frame, so
	 * an over-large number is legal in the file and simply draws against the border.
	 */
	private void move(int x, int y) {
		config.hudX = Integer.toString(Math.max(0, x));
		config.hudY = Integer.toString(Math.max(0, y));
	}

	private String status() {
		HudLayout.Frame frame = hud.lastFrame();
		return this.title.getString() + "   " + frame.x() + ", " + frame.y();
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
		dragging = false;
		config.save();
	}

	/** ESC is handled above, because cancelling has to undo the move before the screen dies. */
	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
