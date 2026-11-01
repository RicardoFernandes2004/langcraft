package dev.langcraft.client.ui;

import dev.langcraft.client.ChallengeClient;
import dev.langcraft.client.VoiceSetup;
import dev.langcraft.client.lang.Furigana;
import dev.langcraft.client.study.Alphabet;
import dev.langcraft.client.study.AlphabetProgress;
import dev.langcraft.client.study.WorldFlashcards;
import dev.langcraft.data.PlayerProgress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

/**
 * O treino do alfabeto: uma tabela com as letras de cada grupo e o quanto cada uma já foi dominada, e uma
 * rodada de perguntas. Nada aqui tem punição nem muda o placar.
 */
public final class AlphabetScreen extends NotebookScreen {
	private static final int MAX_PAGE_WIDTH = 400;
	private static final int MAX_PAGE_HEIGHT = 224;
	private static final int PADDING = 8;
	private static final int TAB_WIDTH = 70;
	private static final int TAB_HEIGHT = 14;
	private static final int GROUP_HEIGHT = 16;
	private static final int GROUP_GAP = 2;
	private static final int GRID_TOP = 53;
	private static final int GRID_BOTTOM_MARGIN = 38;
	private static final int PREFERRED_CELL_WIDTH = 38;
	private static final int MIN_COLUMNS = 5;
	private static final int BOTTOM_BUTTON_WIDTH = 110;
	private static final int SESSION_SIZE = 10;
	private static final int OPTION_COUNT = 4;
	private static final int OPTION_WIDTH = 150;
	private static final int OPTION_HEIGHT = 28;
	private static final int OPTIONS_TOP = 96;
	private static final int RIGHT_PAUSE_TICKS = 14;
	private static final int WRONG_PAUSE_TICKS = 45;
	private static final float PROMPT_SCALE = 4f;

	private enum Mode {
		TABLE,
		PRACTICE,
		RESULT
	}

	private final @Nullable Screen parent;
	private final AlphabetProgress progress = Objects.requireNonNull(WorldFlashcards.alphabet(), "O alfabeto só abre dentro de um mundo");
	private final Random random = new Random();
	private Mode mode = Mode.TABLE;
	private Alphabet.Group group;
	private @Nullable Alphabet.Letter selected;

	private final List<Alphabet.Letter> queue = new ArrayList<>();
	private final List<OptionButton> optionButtons = new ArrayList<>();
	private List<Alphabet.Letter> options = List.of();
	private @Nullable Alphabet.Letter question;
	private boolean showRomajiFirst;
	private boolean answered;
	private int pauseTicks;
	private int asked;
	private int rightAnswers;

	public AlphabetScreen(@Nullable Screen parent) {
		super(Component.translatable("langcraft.alphabet.title"));
		this.parent = parent;
		Alphabet.Group last = Alphabet.group(progress.lastGroup());
		List<String> available = availableLanguages();
		this.group = available.contains(last.language()) ? last : Alphabet.groupsOf(available.get(0)).get(0);
	}

	/** Os idiomas escolhidos pelo jogador que têm um alfabeto para treinar. */
	private static List<String> availableLanguages() {
		return VoiceSetup.currentLanguages().stream().filter(language -> !Alphabet.groupsOf(language).isEmpty()).toList();
	}

	public static boolean canOpen() {
		return !availableLanguages().isEmpty();
	}

	@Override
	protected void init() {
		switch (mode) {
			case TABLE -> initTable();
			case PRACTICE -> initPractice();
			case RESULT -> initResult();
		}
	}

	private void initTable() {
		int left = pageLeft(MAX_PAGE_WIDTH);
		int top = pageTop(MAX_PAGE_HEIGHT);
		int center = pageCenter(MAX_PAGE_WIDTH);
		int inner = pageWidth(MAX_PAGE_WIDTH) - PADDING * 2;

		// Com um idioma só, não há o que trocar.
		if (availableLanguages().size() > 1) {
			addLanguageTab(center - TAB_WIDTH - 2, top + 18, "langcraft.study.korean", PlayerProgress.KOREAN);
			addLanguageTab(center + 2, top + 18, "langcraft.study.japanese", PlayerProgress.JAPANESE);
		}

		List<Alphabet.Group> groups = Alphabet.groupsOf(group.language());
		int groupWidth = (inner - GROUP_GAP * (groups.size() - 1)) / groups.size();
		for (int i = 0; i < groups.size(); i++) {
			Alphabet.Group option = groups.get(i);
			Component name = Component.translatable("langcraft.alphabet.group." + option.id());
			Component label = option == group ? Component.literal("✔ ").append(name) : name;
			addRenderableWidget(new ThemedButton(left + PADDING + (groupWidth + GROUP_GAP) * i, top + 34, groupWidth, GROUP_HEIGHT, label, button -> selectGroup(option)));
		}

		int columns = Math.max(MIN_COLUMNS, Math.min(inner / PREFERRED_CELL_WIDTH, group.letters().size()));
		int rows = (group.letters().size() + columns - 1) / columns;
		int cellWidth = inner / columns;
		int available = pageHeight(MAX_PAGE_HEIGHT) - GRID_TOP - GRID_BOTTOM_MARGIN;
		int cellHeight = Math.min(26, available / rows);
		for (int i = 0; i < group.letters().size(); i++) {
			Alphabet.Letter letter = group.letters().get(i);
			int x = left + PADDING + cellWidth * (i % columns);
			int y = top + GRID_TOP + cellHeight * (i / columns);
			addRenderableWidget(new LetterCell(x, y, cellWidth - 1, cellHeight - 1, letter, progress.level(letter), letter == selected, button -> selectLetter(letter)));
		}

		int bottom = top + pageHeight(MAX_PAGE_HEIGHT) - 25;
		addBottom(center - BOTTOM_BUTTON_WIDTH - 3, bottom, "langcraft.alphabet.practice", this::startPractice);
		addBottom(center + 3, bottom, "langcraft.study.close", this::onClose);
	}

	private void addLanguageTab(int x, int y, String key, String language) {
		Component name = Component.translatable(key);
		Component label = group.language().equals(language) ? Component.literal("✔ ").append(name) : name;
		addRenderableWidget(new ThemedButton(x, y, TAB_WIDTH, TAB_HEIGHT, label, button -> {
			if (!group.language().equals(language)) {
				selectGroup(Alphabet.groupsOf(language).get(0));
			}
		}));
	}

	private void addBottom(int x, int y, String key, Runnable action) {
		addRenderableWidget(new ThemedButton(x, y, BOTTOM_BUTTON_WIDTH, 20, Component.translatable(key), button -> action.run()));
	}

	private void selectGroup(Alphabet.Group chosen) {
		group = chosen;
		selected = null;
		progress.rememberGroup(chosen.id());
		rebuildWidgets();
	}

	private void selectLetter(Alphabet.Letter letter) {
		selected = letter;
		rebuildWidgets();
	}

	private void startPractice() {
		queue.clear();
		queue.addAll(progress.session(group, SESSION_SIZE, random));
		asked = 0;
		rightAnswers = 0;
		mode = Mode.PRACTICE;
		nextQuestion();
	}

	private void nextQuestion() {
		answered = false;
		if (queue.isEmpty()) {
			question = null;
			mode = Mode.RESULT;
		} else {
			question = queue.remove(0);
			options = AlphabetProgress.options(question, group, OPTION_COUNT, random);
			showRomajiFirst = random.nextBoolean();
		}
		rebuildWidgets();
	}

	private void initPractice() {
		optionButtons.clear();
		int center = pageCenter(MAX_PAGE_WIDTH);
		int top = pageTop(MAX_PAGE_HEIGHT);
		int columnWidth = Math.min(OPTION_WIDTH, (pageWidth(MAX_PAGE_WIDTH) - PADDING * 2 - 4) / 2);
		for (int i = 0; i < options.size(); i++) {
			Alphabet.Letter option = options.get(i);
			String text = showRomajiFirst ? option.glyph() : option.romaji();
			int x = i % 2 == 0 ? center - columnWidth - 2 : center + 2;
			int y = top + OPTIONS_TOP + (OPTION_HEIGHT + 4) * (i / 2);
			OptionButton button = new OptionButton(x, y, columnWidth, OPTION_HEIGHT, text, List.of(new Furigana.Segment(text, null)), pressed -> answer(option));
			optionButtons.add(addRenderableWidget(button));
		}
	}

	private void answer(Alphabet.Letter chosen) {
		if (answered) {
			return;
		}
		answered = true;
		asked++;
		boolean right = chosen == question;
		progress.answered(question, right);
		optionButtons.get(options.indexOf(question)).markCorrect();
		if (right) {
			rightAnswers++;
			pauseTicks = RIGHT_PAUSE_TICKS;
			ChallengeClient.play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4f);
		} else {
			optionButtons.get(options.indexOf(chosen)).active = false;
			// Volta no fim da rodada, até o jogador acertar.
			queue.add(question);
			pauseTicks = WRONG_PAUSE_TICKS;
			ChallengeClient.play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f);
		}
	}

	private void initResult() {
		int center = pageCenter(MAX_PAGE_WIDTH);
		int bottom = pageTop(MAX_PAGE_HEIGHT) + pageHeight(MAX_PAGE_HEIGHT) - 25;
		addBottom(center - BOTTOM_BUTTON_WIDTH - 3, bottom, "langcraft.study.again", this::startPractice);
		addBottom(center + 3, bottom, "langcraft.alphabet.back", this::backToTable);
	}

	private void backToTable() {
		mode = Mode.TABLE;
		rebuildWidgets();
	}

	@Override
	public void tick() {
		if (pauseTicks > 0 && --pauseTicks == 0) {
			nextQuestion();
		}
	}

	@Override
	protected void renderPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		drawPage(graphics, MAX_PAGE_WIDTH, MAX_PAGE_HEIGHT);
		renderWidgets(graphics, mouseX, mouseY, partialTick);
		UiTheme.Palette palette = UiDraw.palette();
		int center = pageCenter(MAX_PAGE_WIDTH);
		int top = pageTop(MAX_PAGE_HEIGHT);
		switch (mode) {
			case TABLE -> renderTable(graphics, palette, center, top);
			case PRACTICE -> renderPractice(graphics, palette, center, top);
			case RESULT -> {
				UiDraw.centered(graphics, font, Component.translatable("langcraft.study.done"), center, top + 80, palette.green());
				UiDraw.centered(graphics, font, Component.translatable("langcraft.alphabet.result", rightAnswers, asked), center, top + 94, palette.muted());
				UiDraw.centered(graphics, font, Component.translatable("langcraft.alphabet.learned", progress.learned(group), group.letters().size()), center, top + 108, palette.muted());
			}
		}
	}

	private void renderTable(GuiGraphics graphics, UiTheme.Palette palette, int center, int top) {
		UiDraw.centered(graphics, font, Component.translatable("langcraft.alphabet.heading", title, progress.learned(group), group.letters().size()), center, top + 7, palette.ink());
		int noteY = top + pageHeight(MAX_PAGE_HEIGHT) - 36;
		int textWidth = pageWidth(MAX_PAGE_WIDTH) - PADDING * 2;
		if (selected == null) {
			UiDraw.centered(graphics, font, UiDraw.fit(font, Component.translatable("langcraft.alphabet.hint"), textWidth), center, noteY, palette.muted());
		} else {
			UiDraw.centered(graphics, font, UiDraw.fit(font, describe(selected), textWidth), center, noteY, palette.accent());
		}
	}

	private void renderPractice(GuiGraphics graphics, UiTheme.Palette palette, int center, int top) {
		UiDraw.centered(graphics, font, Component.translatable("langcraft.alphabet.question", title, queue.size() + 1), center, top + 7, palette.ink());
		String prompt = showRomajiFirst ? question.romaji() : question.glyph();
		int promptWidth = Math.round(font.width(prompt) * PROMPT_SCALE);
		FuriganaText.draw(graphics, font, List.of(new Furigana.Segment(prompt, null)), center - promptWidth / 2, top + 28, PROMPT_SCALE, palette.ink(), false);
		if (answered) {
			int textWidth = pageWidth(MAX_PAGE_WIDTH) - PADDING * 2;
			UiDraw.centered(graphics, font, UiDraw.fit(font, describe(question), textWidth), center, top + OPTIONS_TOP - 13, palette.accent());
		}
	}

	/** A letra, a leitura e a dica de pronúncia, quando existe. */
	private Component describe(Alphabet.Letter letter) {
		String noteKey = Alphabet.noteKey(group, letter);
		String head = letter.glyph() + " = " + letter.romaji();
		return Language.getInstance().has(noteKey)
				? Component.literal(head + ". ").append(Component.translatable(noteKey))
				: Component.literal(head);
	}

	@Override
	public void onClose() {
		if (mode == Mode.TABLE) {
			minecraft.setScreen(parent);
		} else {
			pauseTicks = 0;
			backToTable();
		}
	}

	/** Uma letra da tabela: o símbolo, a leitura e uma barra com o quanto ela já foi dominada. */
	private static final class LetterCell extends ThemedButton {
		private static final float GLYPH_SCALE = 2f;

		private final Alphabet.Letter letter;
		private final int level;
		private final boolean chosen;

		LetterCell(int x, int y, int width, int height, Alphabet.Letter letter, int level, boolean chosen, OnPress onPress) {
			super(x, y, width, height, Component.literal(letter.glyph()), onPress);
			this.letter = letter;
			this.level = level;
			this.chosen = chosen;
		}

		@Override
		public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			renderBackground(graphics);
			Font font = Minecraft.getInstance().font;
			UiTheme.Palette palette = UiDraw.palette();
			UiTheme.Buttons style = UiTheme.current().buttons();
			List<Furigana.Segment> glyph = List.of(new Furigana.Segment(letter.glyph(), null));
			float scale = height >= 22 && FuriganaText.width(font, glyph, GLYPH_SCALE) <= width - 2 ? GLYPH_SCALE : 1f;
			int glyphWidth = FuriganaText.width(font, glyph, scale);
			FuriganaText.draw(graphics, font, glyph, getX() + (width - glyphWidth) / 2, getY() + 1, scale, style.ink(), false);
			int romajiY = getY() + (scale == GLYPH_SCALE ? 15 : 9);
			graphics.drawString(font, letter.romaji(), getX() + (width - font.width(letter.romaji())) / 2, romajiY, palette.muted(), false);

			int barY = getY() + height - 2;
			graphics.fill(getX() + 2, barY, getX() + width - 2, barY + 1, palette.track());
			graphics.fill(getX() + 2, barY, getX() + 2 + (width - 4) * level / AlphabetProgress.MAX_LEVEL, barY + 1, palette.green());
			if (chosen) {
				graphics.renderOutline(getX(), getY(), width, height, palette.ink());
			}
		}
	}
}
