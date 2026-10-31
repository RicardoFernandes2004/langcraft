package dev.linguacraft.client.ui;

import dev.linguacraft.client.ChallengeView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class UiColors {
	static final int WHITE = 0xFFFFFFFF;
	static final int MUTED = 0xFFB8BCC4;
	static final int GOLD = 0xFFFFD466;
	static final int RED = 0xFFFF5F56;
	static final int GREEN = 0xFF5FE07A;
	static final int PANEL = 0xE0181A20;
	static final int PANEL_WRONG = 0xE0601A1A;
	static final int PANEL_CORRECT = 0xE01A5A2C;
	static final int TRACK = 0xFF3A3D45;
	private static final String ELLIPSIS = "...";

	private UiColors() {
	}

	/** Barra de tempo: verde no início, amarela no meio e vermelha no fim. */
	static void timerBar(GuiGraphics graphics, int x, int y, int width, int height, float fraction) {
		graphics.fill(x, y, x + width, y + height, TRACK);
		int color = fraction > 0.5f ? GREEN : fraction > 0.2f ? GOLD : RED;
		graphics.fill(x, y, x + Math.round(width * fraction), y + height, color);
	}

	/** Corta o texto com reticências para caber na largura. O reconhecimento pode ouvir frases inteiras. */
	static String fit(Font font, Component text, int maxWidth) {
		String plain = text.getString();
		if (font.width(plain) <= maxWidth) {
			return plain;
		}
		return font.plainSubstrByWidth(plain, maxWidth - font.width(ELLIPSIS)) + ELLIPSIS;
	}

	static int panelColor(ChallengeView view) {
		if (view.correctTicks > 0) {
			return PANEL_CORRECT;
		}
		return view.wrongFlashTicks > 0 ? PANEL_WRONG : PANEL;
	}
}
