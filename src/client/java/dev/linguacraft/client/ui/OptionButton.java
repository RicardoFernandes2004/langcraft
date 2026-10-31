package dev.linguacraft.client.ui;

import dev.linguacraft.client.lang.Furigana;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Alternativa do popup, com furigana. Depois de errada, fica vermelha e riscada. */
final class OptionButton extends Button {
	private static final float SCALE = 2f;
	private static final float SMALL_SCALE = 1f;
	private static final int MARGIN = 6;

	private final List<Furigana.Segment> segments;

	OptionButton(int x, int y, int width, int height, String word, List<Furigana.Segment> segments, OnPress onPress) {
		super(x, y, width, height, Component.literal(word), onPress, DEFAULT_NARRATION);
		this.segments = segments;
	}

	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderDefaultSprite(graphics);
		Font font = Minecraft.getInstance().font;
		float scale = FuriganaText.width(font, segments, SCALE) <= width - MARGIN * 2 ? SCALE : SMALL_SCALE;
		int textWidth = FuriganaText.width(font, segments, scale);
		int textX = getX() + (width - textWidth) / 2;
		int textY = getY() + (height - FuriganaText.height(font, segments, scale)) / 2 + 1;
		FuriganaText.draw(graphics, font, segments, textX, textY, scale, active ? UiColors.WHITE : UiColors.RED, active);
		if (!active) {
			int lineY = textY + FuriganaText.readingRowHeight(font, segments, scale) + (int) (font.lineHeight * scale / 2);
			graphics.fill(textX - 2, lineY, textX + textWidth + 2, lineY + 1, UiColors.RED);
		}
	}
}
