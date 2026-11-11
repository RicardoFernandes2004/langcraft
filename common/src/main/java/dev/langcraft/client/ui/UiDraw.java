package dev.langcraft.client.ui;

import dev.langcraft.client.ChallengeView;
import dev.langcraft.data.PlayerProgress;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Desenhos usados por várias telas, sempre com as cores do tema atual. */
final class UiDraw {
	static final int PIP_SIZE = 5;
	private static final int PIP_GAP = 2;
	private static final int PIP_COUNT = 5;
	private static final int TIMER_SEGMENT = 12;
	private static final String ELLIPSIS = "...";

	enum PanelState {
		NORMAL,
		WRONG,
		CORRECT
	}

	private UiDraw() {
	}

	static UiTheme.Palette palette() {
		return UiTheme.current().palette();
	}

	/** Todo texto do mod passa por {@link StudyFont}, para coreano e japonês não saírem na fonte de um modpack. */
	static void text(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
		graphics.drawString(font, StudyFont.apply(text), x, y, color, palette().shadow());
	}

	static void text(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
		text(graphics, font, Component.literal(text), x, y, color);
	}

	static void centered(GuiGraphics graphics, Font font, Component text, int centerX, int y, int color) {
		text(graphics, font, text, centerX - font.width(StudyFont.apply(text)) / 2, y, color);
	}

	static void centered(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
		centered(graphics, font, Component.literal(text), centerX, y, color);
	}

	static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
		panel(graphics, x, y, width, height, PanelState.NORMAL);
	}

	static void panel(GuiGraphics graphics, int x, int y, int width, int height, PanelState state) {
		plainPanel(graphics, x, y, width, height, state);
		UiTheme.current().decorate(graphics, x, y, width, height);
	}

	/** Sem os enfeites, que cobririam o texto de um painel pequeno. */
	static void plainPanel(GuiGraphics graphics, int x, int y, int width, int height, PanelState state) {
		UiTheme theme = UiTheme.current();
		UiTheme.Palette palette = theme.palette();
		boolean sprite = theme.drawPanelBackground(graphics, x, y, width, height);
		if (!sprite) {
			graphics.fill(x, y, x + width, y + height, switch (state) {
				case NORMAL -> palette.panel();
				case WRONG -> palette.wrong();
				case CORRECT -> palette.correct();
			});
		} else if (state != PanelState.NORMAL) {
			int inset = theme.frameInset();
			graphics.fill(x + inset, y + inset, x + width - inset, y + height - inset, state == PanelState.WRONG ? palette.wrong() : palette.correct());
		}
	}

	static PanelState panelState(ChallengeView view) {
		if (view.correctTicks > 0) {
			return PanelState.CORRECT;
		}
		return view.wrongFlashTicks > 0 ? PanelState.WRONG : PanelState.NORMAL;
	}

	/** Barra de tempo: passa pelas três cores do tema, da mais calma até a de alerta. */
	static void timerBar(GuiGraphics graphics, int x, int y, int width, int height, float fraction) {
		UiTheme theme = UiTheme.current();
		UiTheme.Palette palette = theme.palette();
		graphics.fill(x, y, x + width, y + height, palette.track());
		int color = fraction > 0.5f ? palette.timerHigh() : fraction > 0.2f ? palette.timerMid() : palette.timerLow();
		graphics.fill(x, y, x + Math.round(width * fraction), y + height, color);
		if (theme.segmentedTimer()) {
			for (int tick = x + TIMER_SEGMENT - 1; tick < x + width; tick += TIMER_SEGMENT) {
				graphics.fill(tick, y, tick + 1, y + height, palette.track());
			}
		}
	}

	static int streakWidth(Font font, int streak, int goal) {
		if (UiTheme.current().hasPips() && goal <= PIP_COUNT) {
			return goal * PIP_SIZE + (goal - 1) * PIP_GAP;
		}
		return font.width(Component.translatable("langcraft.hud.streak", streak, PlayerProgress.nextMilestone(streak, goal)));
	}

	/** Mostra quanto falta para a próxima recompensa, terminando em {@code right}. Metas longas não cabem nos marcadores. */
	static void streak(GuiGraphics graphics, Font font, int streak, int goal, int right, int y) {
		UiTheme theme = UiTheme.current();
		int width = streakWidth(font, streak, goal);
		if (theme.hasPips() && goal <= PIP_COUNT) {
			for (int i = 0; i < goal; i++) {
				theme.drawPip(graphics, i < streak % goal, right - width + i * (PIP_SIZE + PIP_GAP), y + 1);
			}
		} else {
			text(graphics, font, Component.translatable("langcraft.hud.streak", streak, PlayerProgress.nextMilestone(streak, goal)), right - width, y, theme.palette().accent());
		}
	}

	/** Corta o texto com reticências para caber na largura. O reconhecimento pode ouvir frases inteiras. */
	static String fit(Font font, Component text, int maxWidth) {
		return fit(font, text.getString(), maxWidth);
	}

	static String fit(Font font, String plain, int maxWidth) {
		if (font.width(StudyFont.of(plain)) <= maxWidth) {
			return plain;
		}
		String cut = plain;
		while (!cut.isEmpty() && font.width(StudyFont.of(cut + ELLIPSIS)) > maxWidth) {
			cut = cut.substring(0, cut.offsetByCodePoints(cut.length(), -1));
		}
		return cut + ELLIPSIS;
	}
}
