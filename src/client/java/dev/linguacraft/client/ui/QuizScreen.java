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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Popup de alternativas. Uma resposta errada já é a derrota. Não fecha com Esc e não pausa o jogo. */
public final class QuizScreen extends Screen {
	private static final int DISTRACTORS = 3;
	private static final int BUTTON_WIDTH = 260;
	private static final int BUTTON_HEIGHT = 22;
	private static final int BUTTON_HEIGHT_WITH_READING = 30;
	private static final int BUTTON_GAP = 4;
	private static final int PANEL_WIDTH = 300;
	private static final int BUTTONS_TOP = 72;
	private static final int PANEL_BOTTOM = 24;
	private static final int WRONG_REVEAL_TICKS = 30;

	private final ChallengeView view;
	private final List<String> options;
	private final List<List<Furigana.Segment>> segments = new ArrayList<>();
	private final List<OptionButton> buttons = new ArrayList<>();
	private final List<Boolean> disabled = new ArrayList<>();
	private final int buttonHeight;
	private int revealTicks;

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
			OptionButton button = new OptionButton(x, y + i * (buttonHeight + BUTTON_GAP), BUTTON_WIDTH, buttonHeight,
					options.get(i), segments.get(i), pressed -> choose(index));
			button.active = !disabled.get(i);
			if (revealTicks > 0 && options.get(i).equals(view.word)) {
				button.markCorrect();
			}
			buttons.add(addRenderableWidget(button));
		}
	}

	private void choose(int index) {
		if (revealTicks > 0) {
			return;
		}
		String option = options.get(index);
		boolean correct = option.equals(view.word);
		if (!correct) {
			disabled.set(index, true);
			buttons.get(index).active = false;
			buttons.get(options.indexOf(view.word)).markCorrect();
			revealTicks = WRONG_REVEAL_TICKS;
		}
		ChallengeClient.submitOption(view, correct, option);
		// O painel não fica vermelho: o destaque desse momento é a resposta certa, em verde.
		view.wrongFlashTicks = 0;
	}

	/** Mostra por um instante qual era a certa. Só depois a derrota vai para o servidor e a punição começa. */
	@Override
	public void tick() {
		if (revealTicks > 0 && --revealTicks == 0) {
			ChallengeClient.submitWrong(view);
		}
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
		UiTheme.Palette palette = UiDraw.palette();
		UiDraw.panel(graphics, left, top, PANEL_WIDTH, panelHeight, UiDraw.panelState(view));

		UiDraw.centered(graphics, font, title, width / 2, top + 8, palette.ink());

		graphics.pose().pushMatrix();
		graphics.pose().translate(width / 2f - 16, top + 20);
		graphics.pose().scale(2f, 2f);
		graphics.renderItem(view.icon, 0, 0);
		graphics.pose().popMatrix();

		UiDraw.centered(graphics, font, view.nativeName, width / 2, top + 56, palette.accent());

		super.render(graphics, mouseX, mouseY, partialTick);

		UiDraw.timerBar(graphics, left + 20, top + panelHeight - 15, PANEL_WIDTH - 40, 5, view.remainingFraction());
		if (view.streakGoal > 0) {
			UiDraw.streak(graphics, font, view.streak, view.streakGoal, left + PANEL_WIDTH - 8, top + 8);
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
