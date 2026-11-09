package com.musicdeck.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The handful of Minecraft calls that do not exist in the same place on every 26.x target.
 *
 * Only the back end differs; every caller reads the same three members. On 26.1 the open
 * screen is still a {@code Minecraft} field with a {@code Minecraft} setter, which is where
 * 26.2 moved both onto {@code Minecraft.gui}.
 */
final class Platform {
	static final InputConstants.Type KEYS = InputConstants.Type.KEYSYM;

	private Platform() {
	}

	static Screen screen(Minecraft minecraft) {
		return minecraft.screen;
	}

	static void open(Minecraft minecraft, Screen screen) {
		minecraft.setScreen(screen);
	}
}
