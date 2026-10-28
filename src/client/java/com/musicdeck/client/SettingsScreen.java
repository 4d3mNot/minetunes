package com.musicdeck.client;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Every switch the mod has, in one place.
 *
 * Built out of plain buttons rather than the vanilla options list, because that list is
 * driven by {@code Options} and these settings live in a properties file the game never
 * sees. 1.21.11 also has no slider widget any more, so the numeric rows step.
 *
 * A change writes the file the moment it is made. The whole reason this screen exists is
 * to watch the card react while it is still on screen, and a setting that only landed on
 * Done could not do that.
 */
public final class SettingsScreen extends Screen {
	/** The left-hand caption of each row, in draw order. */
	private static final String[] ROW_KEYS = {
		"musicdeck.setting.visible",
		"musicdeck.setting.hidewhenidle",
		"musicdeck.setting.controls",
		"musicdeck.setting.art",
		"musicdeck.setting.theme",
		"musicdeck.setting.deck",
		"musicdeck.setting.x",
		"musicdeck.setting.y",
		"musicdeck.setting.bridge",
	};

	// Nine rows have to fit the smallest window the game allows, 240 GUI pixels tall, so the
	// step between them is tighter than it was and the hint sits lower.
	private static final int TOP = 26;
	private static final int STEP = 20;
	private static final int ROWS = ROW_KEYS.length;
	private static final int BUTTON_H = 18;
	private static final int LABEL_W = 160;
	private static final int VALUE_W = 118;
	private static final int TOTAL_W = LABEL_W + 4 + VALUE_W;
	private static final int STEP_W = 20;
	/** The gap the drawn number sits in, between the two step buttons. */
	private static final int NUMBER_W = 46;
	private static final int NUDGE = 4;

	private final MediaConfig config;
	/** Where Done and ESC go back to: Mod Menu's list, the pause menu, or nowhere. */
	private final Screen back;

	public SettingsScreen(MediaConfig config) {
		this(config, null);
	}

	public SettingsScreen(MediaConfig config, Screen back) {
		super(Component.translatable("musicdeck.settings.title"));
		this.config = config;
		this.back = back;
	}

	@Override
	protected void init() {
		super.init();
		toggle(0, config.showHud, value -> {
			config.showHud = value;
			config.save();
		});
		toggle(1, config.onlyWhenPlaying, value -> {
			config.onlyWhenPlaying = value;
			config.save();
		});
		toggle(2, config.showControls, value -> {
			config.showControls = value;
			config.save();
		});
		choice(3, CycleButton.<String>builder(value -> Component.translatable("musicdeck.art." + value),
			config.artStyle()).withValues(MediaConfig.SMOOTH, MediaConfig.PIXEL), value -> {
				config.artStyle = value;
				config.save();
			});
		choice(4, CycleButton.<String>builder(value -> Component.translatable("musicdeck.theme." + value),
			config.theme()).withValues(Palette.THEME_AUTO, Palette.THEME_GREEN, Palette.THEME_WHITE), value -> {
				config.theme = value;
				config.save();
			});
		// Re-read the folder on every open: a deck saved from a text editor while the game is
		// running should appear without a restart, and this is the screen that shows it.
		List<DeckSkin> decks = DeckLibrary.load(MediaConfig.decks());
		config.refreshDeck();
		DeckSkin current = config.deckSkin();
		choice(5, CycleButton.<DeckSkin>builder(deck -> Component.literal(deck.name()), current)
			.withValues(decks), deck -> config.chooseDeck(DeckLibrary.handle(deck)));
		position(6, true);
		position(7, false);

		toggle(8, config.bridgeEnabled, value -> {
			config.bridgeEnabled = value;
			config.save();
			if (!value) {
				// Turning it off should stop the helper now, not on the next world load.
				MusicDeckClient.instance().bridge().stop();
			}
		});

		int footer = TOP + ROWS * STEP + 6;
		int third = (TOTAL_W - 20) / 3;
		this.addRenderableWidget(Button.builder(Component.translatable("musicdeck.settings.move"),
			button -> this.minecraft.setScreen(new LayoutScreen(config, this)))
			.bounds(left(), footer, third, BUTTON_H).build());
		this.addRenderableWidget(Button.builder(Component.translatable("musicdeck.settings.keys"),
			button -> this.minecraft.setScreen(new KeyBindScreen(this)))
			.bounds(left() + third + 10, footer, third, BUTTON_H).build());
		this.addRenderableWidget(Button.builder(Component.translatable("musicdeck.settings.done"),
			button -> this.minecraft.setScreen(back))
			.bounds(left() + 2 * (third + 10), footer, TOTAL_W - 2 * (third + 10), BUTTON_H).build());
	}

	private void toggle(int row, boolean current, Consumer<Boolean> onChange) {
		this.addRenderableWidget(CycleButton.onOffBuilder(current)
			.displayOnlyValue()
			.create(valueX(row), rowY(row), VALUE_W, BUTTON_H, Component.translatable(ROW_KEYS[row]),
				(button, value) -> onChange.accept(value)));
	}

	private <T> void choice(int row, CycleButton.Builder<T> builder, Consumer<T> onChange) {
		this.addRenderableWidget(builder.displayOnlyValue()
			.create(valueX(row), rowY(row), VALUE_W, BUTTON_H, Component.translatable(ROW_KEYS[row]),
				(button, value) -> onChange.accept(value)));
	}

	/**
	 * A row that steps one axis of the panel's position.
	 *
	 * The number between the buttons is drawn rather than a widget: it has nothing to do
	 * when clicked, and a dead button is the one thing an options screen should not ship.
	 */
	private void position(int row, boolean horizontal) {
		int x = valueX(row);
		int inset = (VALUE_W - 2 * STEP_W - NUMBER_W) / 2;
		this.addRenderableWidget(Button.builder(Component.literal("-"),
			button -> nudge(horizontal, -NUDGE)).bounds(x, rowY(row), STEP_W, BUTTON_H).build());
		this.addRenderableWidget(Button.builder(Component.literal("+"),
			button -> nudge(horizontal, NUDGE))
			.bounds(x + inset + STEP_W + NUMBER_W, rowY(row), STEP_W, BUTTON_H).build());
	}

	private void nudge(boolean horizontal, int amount) {
		int x = config.x();
		int y = config.y();
		if (horizontal) {
			x += amount;
		} else {
			y += amount;
		}

		// Only the negative end needs a floor here; HudLayout.frame clamps the far edge to
		// the window every frame, so an over-large value is legal in the file.
		config.hudX = Integer.toString(Math.max(0, x));
		config.hudY = Integer.toString(Math.max(0, y));
		config.save();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		// No renderBackground(): since 1.21.11 the renderer blurs the world once per frame
		// before a screen draws, and a second blur in the same frame throws and takes the
		// game down. The card is painted over this screen by the screen hook, on top.
		super.render(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(this.font, this.title, this.width, 12, Palette.WHITE);
		for (int row = 0; row < ROWS; row++) {
			graphics.drawString(this.font, Component.translatable(ROW_KEYS[row]), left(), rowY(row) + 5, Palette.WHITE);
		}

		drawNumber(graphics, 6, config.hudX);
		drawNumber(graphics, 7, config.hudY);
		graphics.drawCenteredString(this.font, Component.translatable("musicdeck.settings.hint"),
			this.width, this.height - 12, Palette.withAlpha(Palette.WHITE, 150));
	}

	private void drawNumber(GuiGraphics graphics, int row, String value) {
		int x = valueX(row) + STEP_W + (VALUE_W - 2 * STEP_W - this.font.width(value)) / 2;
		graphics.drawString(this.font, value, x, rowY(row) + 5, Palette.WHITE);
	}

	private int left() {
		return (this.width - TOTAL_W) / 2;
	}

	private int valueX(int row) {
		return left() + LABEL_W + 4;
	}

	private static int rowY(int row) {
		return TOP + row * STEP;
	}
}
