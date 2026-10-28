package com.musicdeck.client;

/**
 * The drawing surface the HUD layout writes to.
 *
 * Keeping the layout on top of this interface rather than on {@code GuiGraphics} is what
 * lets the same code be rendered off-screen to a PNG: the overlay's geometry is then
 * reviewable without launching a second Minecraft client next to a running one.
 */
public interface HudCanvas {
	/** Width of {@code text} in GUI pixels, in the font this canvas draws with. */
	int textWidth(String text);

	void text(String text, int x, int y, int colour, boolean shadow);

	/**
	 * Restrict drawing to the half-open rectangle until {@link #unclip}.
	 *
	 * A line that does not fit is slid sideways inside its own column, so the part that
	 * has left the box has to be cut off rather than painted over the panel border.
	 */
	void clip(int x1, int y1, int x2, int y2);

	/** Undo the last {@link #clip}. */
	void unclip();

	/** Paints the half-open rectangle from (x1, y1) up to but excluding (x2, y2). */
	void fill(int x1, int y1, int x2, int y2, int colour);

	/** Blits the album tile at 1:1 texels; the caller only asks when art exists. */
	void art(int x, int y, int size);
}
