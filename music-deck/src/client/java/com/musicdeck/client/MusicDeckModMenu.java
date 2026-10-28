package com.musicdeck.client;

import net.minecraft.client.gui.screens.Screen;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * The bridge to Mod Menu, and the only reason this mod knows that mod exists.
 *
 * It is a {@code modCompileOnly} dependency: nothing here is loaded unless Mod Menu is
 * installed, and the jar does not carry it. The whole integration is one method that
 * hands Mod Menu a screen, which is why it gets its own class instead of a hook inside
 * the client initializer that would have to be written again for Cloth Config.
 */
public final class MusicDeckModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return MusicDeckModMenu::settings;
	}

	/**
	 * Build the settings screen with Mod Menu's own screen behind it.
	 *
	 * The parent matters: Done and ESC have to return to the mod list the player came
	 * from, not drop them straight into the world.
	 */
	private static Screen settings(Screen parent) {
		MusicDeckClient client = MusicDeckClient.instance();
		return new SettingsScreen(client == null ? MediaConfig.load() : client.config(), parent);
	}
}
