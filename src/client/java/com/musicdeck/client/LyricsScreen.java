package com.musicdeck.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * The lyric panel's own custom-UI page: whether it has a band, how solid that band is, and
 * the box the words live in.
 *
 * The card's page could grow two more rows and still fit, but the lyric knobs are a page of
 * their own for a better reason than arithmetic: dragging is how most people will place the
 * words, and the numbers here are the precise fallback for when a drag lands one pixel off.
 * The move screen handles both boxes, so this page never has to draw a position editor.
 *
 * Every change writes the file immediately, like the card's page, because the numbers on
 * screen are the only way to judge what they do.
 */
public final class LyricsScreen extends Screen {
	private static final String[] ROW_KEYS = {
		"musicdeck.lyrics.panel",
		"musicdeck.lyrics.opacity",
		"musicdeck.lyrics.lines",
		"musicdeck.lyrics.width",
		"musicdeck.lyrics.height",
		"musicdeck.lyrics.x",
		"musicdeck.lyrics.y",
	};

	private static final int TOP = 30;
	private static final int STEP = 22;
	private static final int ROWS = ROW_KEYS.length;
	private static final int BUTTON_H = 18;
	private static final int LABEL_W = 160;
	private static final int VALUE_W = 118;
	private static final int TOTAL_W = LABEL_W + 4 + VALUE_W;
	private static final int STEP_W = 20;
	/** The gap the drawn number sits in, between the two step buttons. */
	private static final int NUMBER_W = 46;
	private static final int NUDGE = 4;
	private static final int OPACITY_STEP = 5;

	private final MediaConfig config;
	/** Where Done and ESC go back to. */
	private final Screen back;
	/** The live frame, so the first step away from auto starts where the words actually are. */
	private final NowPlayingHud hud;

	public LyricsScreen(MediaConfig config, Screen back) {
		super(Component.translatable("musicdeck.lyrics.title"));
		this.config = config;
		this.back = back;
		this.hud = MusicDeckClient.instance().hud();
	}

	@Override
	protected void init() {
		super.init();
		this.addRenderableWidget(CycleButton.onOffBuilder(config.lyricsPanel)
			.displayOnlyValue()
			.create(valueX(0), rowY(0), VALUE_W, BUTTON_H, Component.translatable(ROW_KEYS[0]),
				(button, value) -> {
					config.lyricsPanel = value;
					config.save();
				}));
		stepper(1, () -> stepOpacity(-OPACITY_STEP), () -> stepOpacity(OPACITY_STEP));
		choice(2, CycleButton.<Integer>builder(value -> Component.literal(Integer.toString(value)),
			config.lyricsLines()).withValues(lineValues()), value -> {
				config.lyricsLines = value;
				config.save();
			});
		stepper(3, () -> stepWidth(-NUDGE), () -> stepWidth(NUDGE));
		stepper(4, () -> stepHeight(-NUDGE), () -> stepHeight(NUDGE));
		stepper(5, () -> stepX(-NUDGE), () -> stepX(NUDGE));
		stepper(6, () -> stepY(-NUDGE), () -> stepY(NUDGE));

		int footer = TOP + ROWS * STEP + 8;
		int half = (TOTAL_W - 10) / 2;
		this.addRenderableWidget(Button.builder(Component.translatable("musicdeck.lyrics.reset"),
			button -> reset()).bounds(left(), footer, half, BUTTON_H).build());
		this.addRenderableWidget(Button.builder(Component.translatable("musicdeck.settings.done"),
			button -> this.minecraft.setScreen(back))
			.bounds(left() + half + 10, footer, TOTAL_W - half - 10, BUTTON_H).build());
	}

	private static Integer[] lineValues() {
		Integer[] values = new Integer[LyricsLayout.MAX_LINES];
		for (int index = 0; index < values.length; index++) {
			values[index] = index + 1;
		}

		return values;
	}

	private void choice(int row, CycleButton.Builder<Integer> builder, Consumer<Integer> onChange) {
		this.addRenderableWidget(builder.displayOnlyValue()
			.create(valueX(row), rowY(row), VALUE_W, BUTTON_H, Component.translatable(ROW_KEYS[row]),
				(button, value) -> onChange.accept(value)));
	}

	/** Two step buttons with the drawn number between them, exactly like the card's page. */
	private void stepper(int row, Runnable minus, Runnable plus) {
		int x = valueX(row);
		int inset = (VALUE_W - 2 * STEP_W - NUMBER_W) / 2;
		this.addRenderableWidget(Button.builder(Component.literal("-"), button -> minus.run())
			.bounds(x, rowY(row), STEP_W, BUTTON_H).build());
		this.addRenderableWidget(Button.builder(Component.literal("+"), button -> plus.run())
			.bounds(x + inset + STEP_W + NUMBER_W, rowY(row), STEP_W, BUTTON_H).build());
	}

	/**
	 * Step a size or a position.
	 *
	 * The first step off an automatic axis starts from where the layout put the panel, not from
	 * zero: a button that teleports the words to the corner before moving them is not a nudge.
	 */
	private void stepX(int amount) {
		LyricsLayout.Frame panel = panel();
		if (panel != null) {
			config.lyricsX = Integer.toString(Math.max(0, panel.x() + amount));
			config.save();
		}
	}

	private void stepY(int amount) {
		LyricsLayout.Frame panel = panel();
		if (panel != null) {
			config.lyricsY = Integer.toString(Math.max(0, panel.y() + amount));
			config.save();
		}
	}

	private void stepWidth(int amount) {
		LyricsLayout.Frame panel = panel();
		if (panel != null) {
			int room = Math.max(LyricsLayout.MIN_WIDTH, this.width - panel.x());
			config.lyricsX = Integer.toString(panel.x());
			config.lyricsWidth = Integer.toString(
				Math.max(LyricsLayout.MIN_WIDTH, Math.min(panel.width() + amount, room)));
			config.save();
		}
	}

	private void stepHeight(int amount) {
		LyricsLayout.Frame panel = panel();
		if (panel != null) {
			int floor = LyricsLayout.height(1);
			int room = Math.max(floor, this.height - panel.y());
			config.lyricsY = Integer.toString(panel.y());
			config.lyricsHeight = Integer.toString(Math.max(floor, Math.min(panel.height() + amount, room)));
			config.save();
		}
	}

	/**
	 * The panel the buttons act on.
	 *
	 * When a song with words is playing this is the box on screen. When none is - no track, no
	 * file, an instrumental - there is still a box to size, so the same layout is asked for one
	 * with no words in it: its automatic width and height are the ones the words will get, and a
	 * nudge from there lands where the player would expect. With no card to hang under (the mod
	 * menu, before a world is joined) there is nothing to anchor to, so the buttons stay inert.
	 */
	private LyricsLayout.Frame panel() {
		LyricsLayout.Frame live = hud.lastLyrics();
		if (live != null) {
			return live;
		}

		HudLayout.Frame card = hud.lastFrame();
		if (card == null) {
			return null;
		}

		HudCanvas ruler = new HudCanvas() {
			@Override
			public int textWidth(String text) {
				return text.isEmpty() ? 0 : LyricsScreen.this.font.width(text);
			}

			@Override
			public void clip(int x1, int y1, int x2, int y2) {
			}

			@Override
			public void unclip() {
			}

			@Override
			public void text(String text, int x, int y, int colour, boolean shadow) {
			}

			@Override
			public void fill(int x1, int y1, int x2, int y2, int colour) {
			}

			@Override
			public void art(int x, int y, int size) {
			}
		};
		return LyricsLayout.frame(ruler, Lyrics.EMPTY, 0.0, card,
			new LyricsLayout.Options(config.lyricsX(), config.lyricsY(), config.lyricsWidth(),
				config.lyricsHeight(), config.lyricsLines()), this.width, this.height);
	}

	private void stepOpacity(int amount) {
		double current = config.lyricsOpacity();
		int percent = current <= 0.0 ? 85 : (int) Math.round(current * 100.0);
		percent = Math.max(15, Math.min(100, percent + amount));
		config.lyricsOpacity = Integer.toString(percent);
		config.save();
	}

	private void reset() {
		config.lyricsX = MediaConfig.AUTO;
		config.lyricsY = MediaConfig.AUTO;
		config.lyricsWidth = MediaConfig.AUTO;
		config.lyricsHeight = MediaConfig.AUTO;
		config.lyricsLines = LyricsLayout.LINES;
		config.lyricsPanel = true;
		config.lyricsOpacity = MediaConfig.AUTO;
		config.save();
		this.rebuildWidgets();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		// No renderBackground(): the renderer has already blurred the world once this frame.
		super.render(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(this.font, this.title, this.width, 12, Palette.WHITE);
		for (int row = 0; row < ROWS; row++) {
			graphics.drawString(this.font, Component.translatable(ROW_KEYS[row]), left(), rowY(row) + 5, Palette.WHITE);
		}

		drawValue(graphics, 1, opacityText());
		drawValue(graphics, 3, boxText(true));
		drawValue(graphics, 4, boxText(false));
		drawValue(graphics, 5, positionText(true));
		drawValue(graphics, 6, positionText(false));
		graphics.drawCenteredString(this.font, Component.translatable("musicdeck.lyrics.hint"),
			this.width, this.height - 12, Palette.withAlpha(Palette.WHITE, 150));
	}

	private String opacityText() {
		double opacity = config.lyricsOpacity();
		return opacity <= 0.0 ? MediaConfig.AUTO : Math.round(opacity * 100.0) + "%";
	}

	private String boxText(boolean horizontal) {
		LyricsLayout.Frame panel = panel();
		boolean auto = horizontal ? config.lyricsWidth() < 0 : config.lyricsHeight() < 0;
		if (auto || panel == null) {
			return MediaConfig.AUTO;
		}

		return Integer.toString(horizontal ? panel.width() : panel.height());
	}

	private String positionText(boolean horizontal) {
		LyricsLayout.Frame panel = panel();
		boolean auto = horizontal ? config.lyricsX() < 0 : config.lyricsY() < 0;
		if (auto || panel == null) {
			return MediaConfig.AUTO;
		}

		return Integer.toString(horizontal ? panel.x() : panel.y());
	}

	private void drawValue(GuiGraphics graphics, int row, String value) {
		int x = valueX(row) + (VALUE_W - this.font.width(value)) / 2;
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
