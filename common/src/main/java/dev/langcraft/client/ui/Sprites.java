package dev.langcraft.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.langcraft.LangCraft;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/** Desenha as texturas dos temas conforme o {@code .mcmeta} de cada uma: em nove partes, repetida ou esticada. */
final class Sprites {
	private enum Mode {
		STRETCH,
		TILE,
		NINE_SLICE
	}

	private record Scaling(Mode mode, int width, int height, int left, int top, int right, int bottom) {
		private static final Scaling NONE = new Scaling(Mode.STRETCH, 0, 0, 0, 0, 0, 0);
	}

	private static final Map<String, Scaling> CACHE = new HashMap<>();

	private Sprites() {
	}

	static void draw(GuiGraphics graphics, String sprite, int x, int y, int width, int height) {
		ResourceLocation texture = LangCraft.id("textures/gui/sprites/" + sprite + ".png");
		Scaling scaling = CACHE.computeIfAbsent(sprite, unused -> read(texture));
		RenderSystem.enableBlend();
		switch (scaling.mode()) {
			case STRETCH -> graphics.blit(texture, x, y, width, height, 0f, 0f, width, height, width, height);
			case TILE -> tile(graphics, texture, scaling, x, y, width, height, 0, 0, scaling.width(), scaling.height());
			case NINE_SLICE -> nineSlice(graphics, texture, scaling, x, y, width, height);
		}
		RenderSystem.disableBlend();
	}

	private static void nineSlice(GuiGraphics graphics, ResourceLocation texture, Scaling s, int x, int y, int width, int height) {
		int left = Math.min(s.left(), width / 2);
		int right = Math.min(s.right(), width - left);
		int top = Math.min(s.top(), height / 2);
		int bottom = Math.min(s.bottom(), height - top);
		int innerU = s.width() - s.left() - s.right();
		int innerV = s.height() - s.top() - s.bottom();
		int innerW = width - left - right;
		int innerH = height - top - bottom;
		int rightU = s.width() - right;
		int bottomV = s.height() - bottom;
		int rightX = x + width - right;
		int bottomY = y + height - bottom;
		tile(graphics, texture, s, x, y, left, top, 0, 0, left, top);
		tile(graphics, texture, s, rightX, y, right, top, rightU, 0, right, top);
		tile(graphics, texture, s, x, bottomY, left, bottom, 0, bottomV, left, bottom);
		tile(graphics, texture, s, rightX, bottomY, right, bottom, rightU, bottomV, right, bottom);
		tile(graphics, texture, s, x + left, y, innerW, top, s.left(), 0, innerU, top);
		tile(graphics, texture, s, x + left, bottomY, innerW, bottom, s.left(), bottomV, innerU, bottom);
		tile(graphics, texture, s, x, y + top, left, innerH, 0, s.top(), left, innerV);
		tile(graphics, texture, s, rightX, y + top, right, innerH, rightU, s.top(), right, innerV);
		tile(graphics, texture, s, x + left, y + top, innerW, innerH, s.left(), s.top(), innerU, innerV);
	}

	/** Repete um pedaço da textura para cobrir a área, cortando a última repetição. */
	private static void tile(GuiGraphics graphics, ResourceLocation texture, Scaling s, int x, int y, int width, int height, int u, int v, int uWidth, int vHeight) {
		if (width <= 0 || height <= 0 || uWidth <= 0 || vHeight <= 0) {
			return;
		}
		for (int dy = 0; dy < height; dy += vHeight) {
			int h = Math.min(vHeight, height - dy);
			for (int dx = 0; dx < width; dx += uWidth) {
				int w = Math.min(uWidth, width - dx);
				graphics.blit(texture, x + dx, y + dy, w, h, u, v, w, h, s.width(), s.height());
			}
		}
	}

	private static Scaling read(ResourceLocation texture) {
		ResourceLocation meta = new ResourceLocation(texture.getNamespace(), texture.getPath() + ".mcmeta");
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(meta);
		if (resource.isEmpty()) {
			return Scaling.NONE;
		}
		try (Reader reader = resource.get().openAsReader()) {
			JsonObject scaling = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("gui").getAsJsonObject("scaling");
			int width = scaling.get("width").getAsInt();
			int height = scaling.get("height").getAsInt();
			if (scaling.get("type").getAsString().equals("tile")) {
				return new Scaling(Mode.TILE, width, height, 0, 0, 0, 0);
			}
			JsonElement border = scaling.get("border");
			if (border.isJsonObject()) {
				JsonObject sides = border.getAsJsonObject();
				return new Scaling(Mode.NINE_SLICE, width, height, sides.get("left").getAsInt(), sides.get("top").getAsInt(), sides.get("right").getAsInt(), sides.get("bottom").getAsInt());
			}
			int all = border.getAsInt();
			return new Scaling(Mode.NINE_SLICE, width, height, all, all, all, all);
		} catch (IOException | RuntimeException e) {
			LangCraft.LOGGER.warn("Não foi possível ler {}", meta, e);
			return Scaling.NONE;
		}
	}
}
