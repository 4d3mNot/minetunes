package com.musicdeck.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * The mod's own keys, rebinding without leaving the mod's screens.
 *
 * Vanilla's Controls list can already do this, and its "All key binds" button below goes
 * there, but that list is two dozen rows deep and a player who only wants to move the
 * skip key has to hunt for it. These six rows are the whole of it.
 *
 * Writing a binding is {@code KeyMapping.setKey} plus {@code Options.save()} - the same
 * two calls the vanilla list makes. Nothing else has to be told: the overlay polls the
 * mapping every tick, so the new key answers on the next frame.
 */
public final class KeyBindScreen extends Screen {
	private static final int TOP = 34;
	private static final int STEP = 22;
	private static final int BUTTON_H = 20;
	private static final int LABEL_W = 150;
	private static final int VALUE_W = 120;
	private static final int TOTAL_W = LABEL_W + 4 + VALUE_W;
	private static final int RIGHT = 1;
	private static final int UNKNOWN_KEY = -1;
	/**
	 * How long after arming a row a click still belongs to the press that armed it.
	 *
	 * Without this the click that says "press a key" is itself taken as the answer, and the
	 * row ends up bound to the mouse button the player just clicked. Vanilla uses the same
	 * grace for the same reason.
	 */
	private static final long GRACE_MILLIS = 300L;
	/** The notice line is a warning when it names a clash, so it needs its own colour. */
	private static final int WARNING = 0xFFE2A55C;

	private final Screen back;
	private final KeyMapping[] keys;
	private Button[] rows;
	private int capturing = -1;
	private long armedAt;
	private String notice = "";
	private boolean warning;

	public KeyBindScreen(Screen back) {
		super(Component.translatable("musicdeck.keys.title"));
		this.back = back;
		this.keys = MusicDeckClient.instance().keyBinds();
	}

	@Override
	protected void init() {
		super.init();
		rows = new Button[keys.length];
		for (int row = 0; row < keys.length; row++) {
			int index = row;
			rows[row] = this.addRenderableWidget(Button.builder(valueText(row), button -> capture(index))
				.bounds(valueX(), rowY(row), VALUE_W, BUTTON_H).build());
		}

		int footer = rowY(keys.length) + 6;
		int half = (TOTAL_W - 10) / 2;
		this.addRenderableWidget(Button.builder(Component.translatable("musicdeck.keys.back"),
			button -> this.minecraft.setScreen(back)).bounds(left(), footer, half, BUTTON_H).build());
		this.addRenderableWidget(Button.builder(Component.translatable("musicdeck.keys.all"),
			button -> this.minecraft.setScreen(new KeyBindsScreen(this, this.minecraft.options)))
			.bounds(left() + half + 10, footer, half, BUTTON_H).build());
	}

	/** Arming a row must not rebuild the widget list while the press is still being sent. */
	private void capture(int row) {
		capturing = row;
		armedAt = System.currentTimeMillis();
		notice = "";
		warning = false;
	}

	private Component valueText(int row) {
		if (capturing == row) {
			return Component.translatable("musicdeck.keys.press");
		}

		return keys[row].getTranslatedKeyMessage();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (capturing < 0) {
			return super.keyPressed(event);
		}

		if (event.isEscape()) {
			capturing = -1;
			return true;
		}

		if (event.key() != UNKNOWN_KEY) {
			bind(capturing, InputConstants.Type.KEYSYM.getOrCreate(event.key()));
		}

		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (capturing >= 0 && System.currentTimeMillis() - armedAt > GRACE_MILLIS) {
			bind(capturing, InputConstants.Type.MOUSE.getOrCreate(event.button()));
			return true;
		}

		int row = rowAt((int) event.y());
		if (event.button() == RIGHT && row >= 0) {
			reset(row);
			return true;
		}

		// Every press this screen does not claim still has to reach the button under it.
		return super.mouseClicked(event, doubleClick);
	}

	private void bind(int row, InputConstants.Key to) {
		KeyMapping mapping = keys[row];
		mapping.setKey(to);
		this.minecraft.options.save();
		capturing = -1;
		String clash = clash(mapping);
		notice = name(mapping) + " -> " + mapping.getTranslatedKeyMessage().getString() + clash;
		warning = !clash.isEmpty();
	}

	private void reset(int row) {
		KeyMapping mapping = keys[row];
		mapping.setKey(mapping.getDefaultKey());
		this.minecraft.options.save();
		capturing = -1;
		notice = name(mapping) + " reset";
		warning = false;
	}

	/**
	 * Say so when the key the player just chose already belongs to something else.
	 *
	 * The vanilla list shows the same thing from a private conflict table this mod cannot
	 * reach, so the comparison is made on the rendered label: two bindings that print the
	 * same key string are bound to the same key, whatever their fields look like.
	 */
	private String clash(KeyMapping bound) {
		String label = bound.getTranslatedKeyMessage().getString();
		for (KeyMapping other : this.minecraft.options.keyMappings) {
			if (other != bound && !other.getName().equals(bound.getName())
				&& other.getTranslatedKeyMessage().getString().equals(label)) {
				return "  (also on " + name(other) + ")";
			}
		}

		return "";
	}

	private static String name(KeyMapping mapping) {
		return Component.translatable(mapping.getName()).getString();
	}

	private int rowAt(int y) {
		for (int row = 0; row < keys.length; row++) {
			if (y >= rowY(row) && y < rowY(row) + BUTTON_H) {
				return row;
			}
		}

		return -1;
	}

	/** A paused world would stop the very music these keys are for. */
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		// No renderBackground(): 1.21.11 blurs the world once per frame before a screen
		// draws, and a second blur in the same frame throws and takes the game down.
		super.render(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(this.font, this.title, this.width, 12, Palette.WHITE);
		for (int row = 0; row < keys.length; row++) {
			graphics.drawString(this.font, Component.translatable(keys[row].getName()), left(), rowY(row) + 6,
				Palette.WHITE);
			// The widget's own label is refreshed here rather than by rebuilding the list,
			// so arming a row cannot mutate the list the click is still being sent through.
			rows[row].setMessage(valueText(row));
		}

		if (!notice.isEmpty()) {
			graphics.drawCenteredString(this.font, notice, this.width, rowY(keys.length) + 6 + BUTTON_H + 6,
				warning ? WARNING : Palette.withAlpha(Palette.WHITE, 190));
		}

		graphics.drawCenteredString(this.font, Component.translatable("musicdeck.keys.hint"),
			this.width, this.height - 12, Palette.withAlpha(Palette.WHITE, 150));
	}

	private int left() {
		return (this.width - TOTAL_W) / 2;
	}

	private int valueX() {
		return left() + LABEL_W + 4;
	}

	private static int rowY(int row) {
		return TOP + row * STEP;
	}
}
