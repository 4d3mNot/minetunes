package com.musicdeck.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import com.musicdeck.MusicDeck;

/**
 * The album tile: a file the PowerShell helper writes, uploaded as a live texture.
 *
 * The game side cannot reach WinRT, so the artwork arrives as a PNG on disk and this
 * class polls it. {@code DynamicTexture} uploads with a NEAREST sampler, which decides
 * everything below: the helper writes the tile at the exact size the card blits, and the
 * pixelated style is made by handing the GPU a genuinely small texture rather than by
 * asking it to scale one, because point sampling a 48px cover into 48px of screen is
 * soft while 12px into 48px is a deliberate block.
 */
public final class AlbumArt {
	private static final long CHECK_INTERVAL_MILLIS = 250L;
	/** How many source pixels become one block in the pixelated style. */
	private static final int PIXEL_BLOCK = 4;
	private static final Supplier<String> LABEL = () -> "Music Deck album art";

	private final Path file;
	private final Identifier location;
	private long nextCheck;
	private long stamp;
	private int accent;
	private int[] source;
	private String style = "";
	private int texSize;
	private DynamicTexture texture;
	private boolean warned;

	public AlbumArt(Path file) {
		this.file = file;
		this.location = Identifier.fromNamespaceAndPath(MusicDeck.MOD_ID, "textures/gui/cover.png");
	}

	/** True when there is a tile to blit. Touches the disk at most four times a second. */
	public boolean ready(Minecraft minecraft, String wanted) {
		long now = System.currentTimeMillis();
		if (now < nextCheck) {
			return texture != null && style.equals(wanted);
		}

		nextCheck = now + CHECK_INTERVAL_MILLIS;
		try {
			if (!Files.isRegularFile(file)) {
				drop();
				return false;
			}

			long current = Files.size(file) ^ Files.getLastModifiedTime(file).toMillis();
			if (current != stamp || texture == null || !style.equals(wanted)) {
				load(minecraft, current, wanted);
			}
		} catch (IOException e) {
			drop();
		}

		return texture != null && style.equals(wanted);
	}

	private void drop() {
		texture = null;
		stamp = 0L;
		source = null;
		// A card with no tile is tinted by the player's own colour, not by the album that
		// happened to be playing before this one.
		accent = 0;
	}

	private void load(Minecraft minecraft, long current, String wanted) {
		if (current != stamp || source == null) {
			readSource(current);
		}

		if (source == null) {
			texture = null;
			return;
		}

		NativeImage image = null;
		try {
			int size = HudLayout.TILE;
			int step = wanted.equals(MediaConfig.PIXEL) ? PIXEL_BLOCK : 1;
			size /= step;
			image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					int from = (y * step) * HudLayout.TILE + (x * step);
					image.setPixelABGR(x, y, from < source.length ? source[from] : 0);
				}
			}

			DynamicTexture uploaded = new DynamicTexture(LABEL, image);
			image = null;
			// Registering the same key closes the previous texture, so a track change or a
			// style switch does not leave a GPU allocation behind.
			minecraft.getTextureManager().register(location, uploaded);
			texture = uploaded;
			style = wanted;
			texSize = size;
		} catch (RuntimeException e) {
			if (image != null) {
				image.close();
			}

			texture = null;
			if (!warned) {
				warned = true;
				MusicDeck.LOGGER.warn("Could not upload the album tile", e);
			}
		}
	}

	private void readSource(long current) {
		NativeImage image = null;
		try (InputStream in = Files.newInputStream(file)) {
			image = NativeImage.read(NativeImage.Format.RGBA, in);
			if (image.getWidth() != HudLayout.TILE || image.getHeight() != HudLayout.TILE) {
				// Refusing a tile has to forget the last good one as well. Left uploaded, the
				// previous cover keeps painting and reads as the new track's artwork.
				drop();
				if (!warned) {
					warned = true;
					MusicDeck.LOGGER.warn("Album tile is {}x{}, the card needs {}x{}",
						image.getWidth(), image.getHeight(), HudLayout.TILE, HudLayout.TILE);
				}

				return;
			}

			source = image.getPixelsABGR();
			accent = Palette.accent(source, 0);
			stamp = current;
			warned = false;
		} catch (IOException | RuntimeException e) {
			drop();
			if (!warned) {
				warned = true;
				MusicDeck.LOGGER.warn("Could not read the album tile", e);
			}
		} finally {
			if (image != null) {
				image.close();
			}
		}
	}

	public Identifier location() {
		return location;
	}

	/** The uploaded texture's own edge, which is not the drawn edge in the pixelated style. */
	public int texSize() {
		return texture == null ? HudLayout.TILE : texSize;
	}

	/** The colour sampled from the current tile, or 0 when nothing has been read yet. */
	public int accent() {
		return accent;
	}
}
