package com.musicdeck.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.musicdeck.MusicDeck;

/**
 * Wires the SMTC helper to the game: one keybinding per transport verb, the overlay,
 * and the {@code /deck} command.
 *
 * Windows only, because SMTC is a Windows API. On any other OS the mod loads, says so
 * once and stays out of the way rather than failing to start.
 */
public class MusicDeckClient implements ClientModInitializer {
	private static final int TICKS_BETWEEN_CHECKS = 10;
	/** A command file is picked up within 100ms, so this is how long to wait for its ack. */
	private static final int ACK_DELAY_TICKS = 6;
	/**
	 * One Controls section for the whole mod, rather than six keys lost among the vanilla
	 * bindings. Registering is what puts the heading in the list, and doing it in a static
	 * field means it happens exactly once however many times the initializer runs.
	 */
	private static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MusicDeck.MOD_ID, "main"));

	private static MusicDeckClient instance;

	private MediaConfig config;
	private MediaBridge bridge;
	private NowPlayingHud hud;
	private KeyMapping toggleKey;
	private KeyMapping nextKey;
	private KeyMapping previousKey;
	private KeyMapping hudKey;
	private KeyMapping moveKey;
	private KeyMapping settingsKey;

	private int ticks;
	private String pendingAck;
	private int pendingAt;

	public static MusicDeckClient instance() {
		return instance;
	}

	public MediaConfig config() {
		return config;
	}

	public MediaBridge bridge() {
		return bridge;
	}

	public NowPlayingHud hud() {
		return hud;
	}

	/**
	 * This mod's own bindings, in the order the settings screen should list them.
	 *
	 * The array is built per call rather than stored so the screen cannot show a stale list
	 * if a binding is ever added: it reads as a missing row instead of a wrong one.
	 */
	public KeyMapping[] keyBinds() {
		return new KeyMapping[] {toggleKey, nextKey, previousKey, hudKey, moveKey, settingsKey};
	}

	public boolean enabled() {
		return config.bridgeEnabled && isWindows();
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		config = MediaConfig.load();
		bridge = new MediaBridge(FabricLoader.getInstance().getGameDir());

		hud = new NowPlayingHud(config, bridge, new AlbumArt(bridge.artFile()));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MusicDeck.MOD_ID, "now_playing"), hud::render);

		// Keybindings are only polled while no screen is open, so the card cannot be moved
		// or clicked from inside the inventory without the screen API letting it keep the
		// mouse. Every screen gets the same two hooks: draw on top, and answer first.
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (!(screen instanceof LayoutScreen)) {
				ScreenEvents.afterRender(screen).register((ignored, graphics, mouseX, mouseY, delta) ->
					hud.renderOverScreen(graphics, mouseX, mouseY));
			}

			ScreenMouseEvents.allowMouseClick(screen).register((ignored, event) -> !hud.click(event.x(), event.y(), event.button()));
		});

		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.musicdeck.toggle", InputConstants.KEY_P, CATEGORY));
		nextKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.musicdeck.next", InputConstants.KEY_N, CATEGORY));
		previousKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.musicdeck.prev", InputConstants.KEY_O, CATEGORY));
		hudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.musicdeck.hud", InputConstants.KEY_K, CATEGORY));
		moveKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.musicdeck.move", InputConstants.KEY_U, CATEGORY));
		settingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.musicdeck.settings", InputConstants.KEY_EQUALS, CATEGORY));

		MusicDeckCommand.register(this);
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> bridge.stop());

		if (!isWindows()) {
			MusicDeck.LOGGER.warn("Music Deck needs Windows SMTC; media control is off on this OS");
		} else if (!config.bridgeEnabled) {
			MusicDeck.LOGGER.info("Music Deck disabled via musicdeck.properties");
		} else {
			bridge.tick();
		}
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().contains("windows");
	}

	private void onClientTick(Minecraft minecraft) {
		while (toggleKey.consumeClick()) {
			send("toggle", "play/pause");
		}

		while (nextKey.consumeClick()) {
			send("next", "next track");
		}

		while (previousKey.consumeClick()) {
			send("prev", "previous track");
		}

		while (hudKey.consumeClick()) {
			config.showHud = !config.showHud;
			config.save();
			tell(minecraft, "Now-playing panel " + (config.showHud ? "on" : "off"));
		}

		while (moveKey.consumeClick()) {
			move(minecraft);
		}

		while (settingsKey.consumeClick()) {
			settings(minecraft);
		}

		if (pendingAck != null) {
			if (++pendingAt >= ACK_DELAY_TICKS) {
				tell(minecraft, bridge.lastAck().isEmpty() ? pendingAck + " sent" : bridge.lastAck());
				pendingAck = null;
				pendingAt = 0;
			}
		}

		if (enabled() && ++ticks % TICKS_BETWEEN_CHECKS == 0) {
			bridge.tick();
		}
	}

	/** Open the placement editor. Only reachable with the world running, not from a menu. */
	public void move(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null) {
			tell(minecraft, "Start a world first; the panel needs somewhere to be.");
			return;
		}

		minecraft.setScreen(new LayoutScreen(config, null));
	}

	/**
	 * Open the settings screen.
	 *
	 * No world check, unlike {@link #move}: every other switch on this screen works from
	 * the pause menu too, and the card itself is drawn over whatever screen is open.
	 * Whatever is open right now becomes the screen Done returns to, which is what lets
	 * Mod Menu open this one from its own list and land back there.
	 */
	public void settings(Minecraft minecraft) {
		minecraft.setScreen(new SettingsScreen(config, minecraft.screen));
	}

	private void send(String command, String friendly) {
		if (!enabled()) {
			return;
		}

		if (bridge.send(command)) {
			pendingAck = friendly;
			pendingAt = 0;
		} else {
			bridge.tick();
			pendingAck = null;
		}
	}

	/** Queue an ack readout for the next command; used by /deck verbs. */
	public void announceLater(String command) {
		pendingAck = command;
		pendingAt = 0;
	}

	private void tell(Minecraft minecraft, String message) {
		if (minecraft.player != null) {
			minecraft.player.displayClientMessage(Component.literal(message), true);
		}
	}
}
