package dev.linguacraft.client.ui;

import dev.linguacraft.LinguaCraft;
import dev.linguacraft.client.ChallengeClient;
import dev.linguacraft.client.ChallengeView;
import dev.linguacraft.client.lang.DistractorPicker;
import dev.linguacraft.client.lang.Furigana;
import dev.linguacraft.client.lang.GameNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Popup de alternativas. Não fecha com Esc e não pausa o jogo. */
public final class QuizScreen extends Screen {
	private static final int DISTRACTORS = 3;
	private static final int BUTTON_WIDTH = 260;
	private static final int BUTTON_HEIGHT = 22;
	private static final int BUTTON_HEIGHT_WITH_READING = 30;
	private static final int BUTTON_GAP = 4;
	private static final int PANEL_WIDTH = 300;
	private static final int BUTTONS_TOP = 72;
	private static final int PANEL_BOTTOM = 24;

	private final ChallengeView view;
	private final List<String> options;
	private final List<List<Furigana.Segment>> segments = new ArrayList<>();
	private final List<Button> buttons = new ArrayList<>();
	private final List<Boolean> disabled = new ArrayList<>();
	private final int buttonHeight;

	public QuizScreen(ChallengeView view) {
		super(Component.translatable("linguacraft.quiz.title", Component.translatable("linguacraft.language." + view.language)));
		this.view = view;
		this.options = DistractorPicker.options(view.word, GameNames.allNames(view.language, view.type), DISTRACTORS, new Random());
		boolean anyReading = false;
		for (String option : options) {
			List<Furigana.Segment> optionSegments = LinguaCraft.config.showFurigana
					? Furigana.segments(option, view.language)
					: List.of(new Furigana.Segment(option, null));
			segments.add(optionSegments);
			anyReading |= Furigana.hasReadings(optionSegments);
			disabled.add(false);
		}
		this.buttonHeight = anyReading ? BUTTON_HEIGHT_WITH_READING : BUTTON_HEIGHT;
	}

	@Override
	protected void init() {
		buttons.clear();
		int x = (width - BUTTON_WIDTH) / 2;
		int y = panelTop() + BUTTONS_TOP;
		for (int i = 0; i < options.size(); i++) {
			int index = i;
			Button button = new OptionButton(x, y + i * (buttonHeight + BUTTON_GAP), BUTTON_WIDTH, buttonHeight,
					options.get(i), segments.get(i), pressed -> choose(index));
			button.active = !disabled.get(i);
			buttons.add(addRenderableWidget(button));
		}
	}

	private void choose(int index) {
		boolean correct = options.get(index).equals(view.word);
		if (!correct) {
			disabled.set(index, true);
			buttons.get(index).active = false;
		}
		ChallengeClient.submitOption(view, correct);
	}

	private int panelHeight() {
		return BUTTONS_TOP + options.size() * (buttonHeight + BUTTON_GAP) + PANEL_BOTTOM;
	}

	private int panelTop() {
		return Math.max(2, (height - panelHeight()) / 2);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		int left = (width - PANEL_WIDTH) / 2;
		int top = panelTop();
		int panelHeight = panelHeight();
		graphics.fill(left, top, left + PANEL_WIDTH, top + panelHeight, UiColors.panelColor(view));

		graphics.drawCenteredString(font, title, width / 2, top + 8, UiColors.WHITE);

		graphics.pose().pushMatrix();
		graphics.pose().translate(width / 2f - 16, top + 20);
		graphics.pose().scale(2f, 2f);
		graphics.renderItem(view.icon, 0, 0);
		graphics.pose().popMatrix();

		graphics.drawCenteredString(font, view.nativeName, width / 2, top + 56, UiColors.GOLD);

		super.render(graphics, mouseX, mouseY, partialTick);

		UiColors.timerBar(graphics, left + 20, top + panelHeight - 15, PANEL_WIDTH - 40, 5, view.remainingFraction());
		if (view.streakGoal > 0) {
			Component streak = Component.translatable("linguacraft.hud.streak", view.streak, view.streakGoal);
			graphics.drawString(font, streak, left + PANEL_WIDTH - 8 - font.width(streak), top + 8, UiColors.GOLD);
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Sem desfoque: o tempo continua correndo e o jogador precisa ver o que acontece em volta.
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
