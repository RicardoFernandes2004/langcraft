package dev.langcraft.client.ui;

import dev.langcraft.client.lang.Furigana;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Alternativa do popup, com furigana. A errada fica vermelha e riscada, e a certa fica verde depois do erro. */
final class OptionButton extends ThemedButton {
	private static final float SCALE = 2f;
	private static final float SMALL_SCALE = 1f;
	private static final int MARGIN = 6;
	/** O verde da resposta certa é o mesmo em todos os temas, para nunca ser confundido com a cor do tema. */
	private static final int CORRECT_FILL = 0xFF0E2A18;
	private static final int CORRECT_GREEN = 0xFF5FE07A;

	private final List<Furigana.Segment> segments;
	private boolean markedCorrect;

	OptionButton(int x, int y, int width, int height, String word, List<Furigana.Segment> segments, OnPress onPress) {
		super(x, y, width, height, Component.literal(word), onPress);
		this.segments = segments;
	}

	void markCorrect() {
		markedCorrect = true;
	}

	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		UiTheme.Buttons style = UiTheme.current().buttons();
		if (markedCorrect) {
			graphics.fill(getX(), getY(), getX() + width, getY() + height, CORRECT_GREEN);
			graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, CORRECT_FILL);
		} else {
			renderBackground(graphics);
		}
		Font font = Minecraft.getInstance().font;
		float scale = FuriganaText.width(font, segments, SCALE) <= width - MARGIN * 2 ? SCALE : SMALL_SCALE;
		int textWidth = FuriganaText.width(font, segments, scale);
		int textX = getX() + (width - textWidth) / 2;
		int textY = getY() + (height - FuriganaText.height(font, segments, scale)) / 2 + 1;
		int color = markedCorrect ? CORRECT_GREEN : active ? style.ink() : style.wrongInk();
		FuriganaText.draw(graphics, font, segments, textX, textY, scale, color, !markedCorrect && active && style.shadow());
		if (markedCorrect) {
			graphics.drawString(font, "✔", getX() + MARGIN, getY() + (height - font.lineHeight) / 2 + 1, CORRECT_GREEN, false);
		}
		if (!active) {
			int lineY = textY + FuriganaText.readingRowHeight(font, segments, scale) + (int) (font.lineHeight * scale / 2);
			graphics.fill(textX - 2, lineY, textX + textWidth + 2, lineY + 1, style.wrongInk());
		}
	}
}
