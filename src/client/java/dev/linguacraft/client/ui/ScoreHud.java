package dev.linguacraft.client.ui;

import dev.linguacraft.LinguaCraft;
import dev.linguacraft.challenge.Minigame;
import dev.linguacraft.challenge.PlayerTitle;
import dev.linguacraft.client.ChallengeClient;
import dev.linguacraft.client.ChallengeView;
import dev.linguacraft.net.ScorePayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** O placar do canto da tela: título, sequência atual, recorde, acertos e erros. Fica sempre visível. */
public final class ScoreHud {
	private static final int MARGIN = 4;
	private static final int PADDING = 5;
	private static final int ROW_HEIGHT = 10;
	private static final int ROWS = 4;

	private static int lastBottom;

	private ScoreHud() {
	}

	/** Onde o placar terminou neste quadro, para a barra de download ficar logo abaixo. Zero se ele não apareceu. */
	static int lastBottom() {
		return lastBottom;
	}

	public static void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		ScorePayload score = ChallengeClient.score();
		lastBottom = 0;
		if (score == null || !LinguaCraft.config.showScoreboard || minecraft.options.hideGui) {
			return;
		}
		Font font = minecraft.font;
		UiTheme.Palette palette = UiDraw.palette();
		Component streak = score.streakGoal() > 0
				? Component.translatable("linguacraft.hud.streak", score.streak(), score.streakGoal())
				: Component.translatable("linguacraft.score.streak", score.streak());
		Component best = Component.translatable("linguacraft.score.best", score.bestStreak());
		Component title = PlayerTitle.of(score.totalCorrect()).displayName();
		String correct = "✔ " + score.totalCorrect();
		String wrong = "✖ " + score.totalWrong();
		int countsGap = font.width("  ");
		int countsWidth = font.width(correct) + countsGap + font.width(wrong);
		int width = Math.max(font.width(title), Math.max(Math.max(font.width(streak), font.width(best)), countsWidth)) + PADDING * 2;
		int height = ROWS * ROW_HEIGHT + PADDING * 2 - 2;

		int x = MARGIN;
		int y = belowAlert(graphics, x + width) ? SpeakAlertHud.bottom() + MARGIN : MARGIN;
		UiDraw.plainPanel(graphics, x, y, width, height, UiDraw.PanelState.NORMAL);
		lastBottom = y + height;
		int textX = x + PADDING;
		int textY = y + PADDING;
		UiDraw.text(graphics, font, title, textX, textY, palette.accent());
		UiDraw.text(graphics, font, streak, textX, textY + ROW_HEIGHT, palette.ink());
		UiDraw.text(graphics, font, best, textX, textY + ROW_HEIGHT * 2, palette.muted());
		UiDraw.text(graphics, font, correct, textX, textY + ROW_HEIGHT * 3, palette.green());
		UiDraw.text(graphics, font, wrong, textX + font.width(correct) + countsGap, textY + ROW_HEIGHT * 3, palette.red());
	}

	/** Em janelas estreitas, o placar encostaria no alerta do topo. */
	private static boolean belowAlert(GuiGraphics graphics, int right) {
		ChallengeView view = ChallengeClient.current();
		return view != null && view.minigame != Minigame.QUIZ && right + MARGIN > SpeakAlertHud.left(graphics.guiWidth());
	}
}
