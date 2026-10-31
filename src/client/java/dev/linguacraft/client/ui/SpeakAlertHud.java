package dev.linguacraft.client.ui;

import dev.linguacraft.challenge.Minigame;
import dev.linguacraft.challenge.SubjectType;
import dev.linguacraft.client.ChallengeClient;
import dev.linguacraft.client.ChallengeView;
import dev.linguacraft.client.LinguaCraftClient;
import dev.linguacraft.client.voice.VoiceRecognizer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** O alerta do topo da tela, usado pela fala e pelo Pegue na mão. Não bloqueia o jogo. */
public final class SpeakAlertHud {
	private static final int WIDTH = 280;
	private static final int BASE_HEIGHT = 62;
	private static final int TOP = 6;
	private static final int PADDING = 8;
	private static final float WORD_SCALE = 2f;

	private SpeakAlertHud() {
	}

	public static void render(GuiGraphics graphics, DeltaTracker delta) {
		ChallengeView view = ChallengeClient.current();
		if (view == null || view.minigame == Minigame.QUIZ) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;

		int shake = view.wrongFlashTicks > 0 ? (int) Math.round(Math.sin(view.wrongFlashTicks * 1.8) * 3) : 0;
		int x = (graphics.guiWidth() - WIDTH) / 2 + shake;
		int contentX = x + PADDING;
		int y = TOP;

		int iconWidth = view.minigame == Minigame.SPEAK ? 22 : 0;
		int wordWidth = WIDTH - PADDING * 2 - iconWidth;
		float scale = FuriganaText.width(font, view.segments, WORD_SCALE) <= wordWidth ? WORD_SCALE : 1f;
		int readingRow = FuriganaText.readingRowHeight(font, view.segments, scale);
		int height = BASE_HEIGHT + readingRow;
		int rowY = y + readingRow;

		graphics.fill(x, y, x + WIDTH, y + height, UiColors.panelColor(view));

		if (view.minigame == Minigame.SPEAK) {
			graphics.renderItem(view.icon, contentX, y + PADDING + readingRow / 2);
			contentX += iconWidth;
		}

		FuriganaText.draw(graphics, font, view.segments, contentX, y + PADDING + 2, scale, UiColors.WHITE, true);

		if (view.correctTicks > 0) {
			graphics.drawString(font, Component.translatable("linguacraft.hud.correct"), x + PADDING, rowY + 30, UiColors.GREEN, true);
		} else {
			graphics.drawString(font, instruction(view), x + PADDING, rowY + 28, UiColors.MUTED, true);
			Component status = status(view);
			if (status != null) {
				// A sequência de acertos fica na mesma linha, à direita.
				int streakWidth = view.streakGoal > 0 ? font.width(Component.translatable("linguacraft.hud.streak", view.streak, view.streakGoal)) + PADDING : 0;
				String text = UiColors.fit(font, status, WIDTH - PADDING * 2 - streakWidth);
				graphics.drawString(font, text, x + PADDING, rowY + 39, view.wrongFlashTicks > 0 || view.wrongItem ? UiColors.RED : UiColors.GOLD, true);
			}
			if (view.minigame == Minigame.SPEAK) {
				Component hint = Component.translatable("linguacraft.hud.type_hint", LinguaCraftClient.typeAnswerKey.getTranslatedKeyMessage());
				graphics.drawString(font, hint, x + WIDTH - PADDING - font.width(hint), rowY + 28, UiColors.MUTED, true);
			}
		}

		if (view.streakGoal > 0) {
			Component streak = Component.translatable("linguacraft.hud.streak", view.streak, view.streakGoal);
			graphics.drawString(font, streak, x + WIDTH - PADDING - font.width(streak), rowY + 39, UiColors.GOLD, true);
		}

		if (view.correctTicks <= 0) {
			UiColors.timerBar(graphics, x + PADDING, y + height - 9, WIDTH - PADDING * 2, 4, view.remainingFraction());
		}
	}

	private static Component instruction(ChallengeView view) {
		if (view.minigame == Minigame.HOLD) {
			return Component.translatable("linguacraft.hud.hold");
		}
		return Component.translatable(view.type == SubjectType.MOB ? "linguacraft.hud.speak_mob" : "linguacraft.hud.speak");
	}

	private static Component status(ChallengeView view) {
		if (view.minigame == Minigame.HOLD) {
			return view.wrongItem ? Component.translatable("linguacraft.hud.wrong_item") : null;
		}
		if (view.heard != null) {
			return Component.translatable("linguacraft.hud.heard", view.heard);
		}
		return view.voice == null ? null : voiceStatus(view.voice);
	}

	static Component voiceStatus(VoiceRecognizer voice) {
		return switch (voice.status()) {
			case PREPARING -> Component.translatable("linguacraft.voice.preparing");
			case DOWNLOADING -> Component.translatable("linguacraft.voice.downloading", voice.downloadPercent());
			case LISTENING -> Component.translatable(voice.usingFallback() ? "linguacraft.voice.listening_default" : "linguacraft.voice.listening");
			case NO_MIC -> Component.translatable("linguacraft.voice.no_mic");
			case FAILED -> Component.translatable("linguacraft.voice.failed");
		};
	}
}
