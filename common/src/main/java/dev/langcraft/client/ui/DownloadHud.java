package dev.langcraft.client.ui;

import dev.langcraft.client.voice.ModelManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A barra de download durante o jogo. A tela de download não abre no meio de um desafio e pode ser dispensada
 * com "Continuar sem esperar", e o modelo continua baixando. Assim, o jogador sempre vê o progresso.
 */
public final class DownloadHud {
	private static final int MARGIN = 4;
	private static final int WIDTH = 130;
	private static final int ROW_HEIGHT = 18;
	private static final int PADDING = 4;
	private static final int BAR_HEIGHT = 3;

	private DownloadHud() {
	}

	public static void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.options.hideGui || minecraft.screen instanceof VoiceSetupScreen) {
			return;
		}
		var languages = ModelManager.downloading();
		if (languages.isEmpty()) {
			return;
		}
		Font font = minecraft.font;
		UiTheme.Palette palette = UiDraw.palette();
		int y = ScoreHud.lastBottom() + MARGIN;
		UiDraw.plainPanel(graphics, MARGIN, y, WIDTH, languages.size() * ROW_HEIGHT + PADDING, UiDraw.PanelState.NORMAL);
		int rowY = y + PADDING;
		int inner = WIDTH - PADDING * 2 - 2;
		for (String language : languages) {
			int percent = ModelManager.downloadPercent(language);
			Component text = Component.translatable("langcraft.download.hud", Component.translatable("langcraft.language." + language), percent);
			UiDraw.text(graphics, font, UiDraw.fit(font, text, inner), MARGIN + PADDING + 1, rowY, palette.ink());
			int barY = rowY + 10;
			graphics.fill(MARGIN + PADDING + 1, barY, MARGIN + PADDING + 1 + inner, barY + BAR_HEIGHT, palette.track());
			graphics.fill(MARGIN + PADDING + 1, barY, MARGIN + PADDING + 1 + inner * percent / 100, barY + BAR_HEIGHT, palette.green());
			rowY += ROW_HEIGHT;
		}
	}
}
