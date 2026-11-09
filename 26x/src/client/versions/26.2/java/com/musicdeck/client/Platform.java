package com.musicdeck.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The handful of Minecraft calls that do not exist in the same place on every 26.x target.
 *
 * Only the back end differs; every caller reads the same three members. From 26.2 the open
 * screen and its setter live on {@code Minecraft.gui}; {@code InputConstants.Type.KEYSYM}
 * kept its 1.21 name until 26.3 renamed it to {@code KEYBOARD}.
 */
final class Platform {
	static final InputConstants.Type KEYS = InputConstants.Type.KEYSYM;

	private Platform() {
	}

	static Screen screen(Minecraft minecraft) {
		return minecraft.gui.screen();
	}

	static void open(Minecraft minecraft, Screen screen) {
		minecraft.gui.setScreen(screen);
	}
}
