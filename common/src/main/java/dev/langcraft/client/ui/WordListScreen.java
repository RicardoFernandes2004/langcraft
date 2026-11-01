package dev.langcraft.client.ui;

import dev.langcraft.challenge.SubjectType;
import dev.langcraft.client.ChallengeClient;
import dev.langcraft.client.lang.Furigana;
import dev.langcraft.client.lang.GameNames;
import dev.langcraft.client.study.AnswerMethod;
import dev.langcraft.client.study.FlashcardDeck;
import dev.langcraft.client.study.WorldFlashcards;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** A lista das palavras encontradas neste mundo, com o significado e os acertos e erros de cada tipo de resposta. */
public final class WordListScreen extends NotebookScreen {
	private static final int MAX_PAGE_WIDTH = 400;
	private static final int MAX_PAGE_HEIGHT = 224;
	private static final int PADDING = 8;
	private static final int FILTER_HEIGHT = 16;
	private static final int FILTER_WIDTH = 150;
	private static final int HEADER_TOP = 40;
	private static final int LIST_TOP = 51;
	private static final int LIST_BOTTOM_MARGIN = 30;
	private static final int ROW_HEIGHT = 18;
	private static final int ICON_WIDTH = 19;
	private static final int METHOD_WIDTH = 34;
	private static final int NAV_WIDTH = 24;
	private static final int STUDY_BUTTON_WIDTH = 90;
	private static final AnswerMethod[] METHODS = AnswerMethod.values();

	private record Row(FlashcardDeck.Card card, ItemStack icon, List<Furigana.Segment> segments) {
	}

	private final @Nullable Screen parent;
	private final FlashcardDeck deck = Objects.requireNonNull(WorldFlashcards.current(), "A lista só abre dentro de um mundo");
	private final List<Row> rows = new ArrayList<>();
	private List<FlashcardDeck.Card> words = List.of();
	private @Nullable String language;
	private int page;

	public WordListScreen(@Nullable Screen parent) {
		super(Component.translatable("langcraft.words.title"));
		this.parent = parent;
	}

	/** Quantas palavras estão na lista. */
	public int wordCount() {
		return words.size();
	}

	private int rowsPerPage() {
		return Math.max(1, (pageHeight(MAX_PAGE_HEIGHT) - LIST_TOP - LIST_BOTTOM_MARGIN) / ROW_HEIGHT);
	}

	private int pageCount() {
		return Math.max(1, (words.size() + rowsPerPage() - 1) / rowsPerPage());
	}

	@Override
	protected void init() {
		words = deck.words(scope(language));
		page = Math.min(page, pageCount() - 1);
		loadRows();

		int inner = pageWidth(MAX_PAGE_WIDTH) - PADDING * 2;
		int x = pageLeft(MAX_PAGE_WIDTH) + PADDING;
		int y = pageTop(MAX_PAGE_HEIGHT) + 20;
		ThemedButton filter = new ThemedButton(x, y, FILTER_WIDTH, FILTER_HEIGHT, languageFilterLabel(language), button -> {
			language = nextLanguage(language);
			page = 0;
			rebuildWidgets();
		});
		filter.active = hasLanguageChoice();
		addRenderableWidget(filter);

		int bottom = pageTop(MAX_PAGE_HEIGHT) + pageHeight(MAX_PAGE_HEIGHT) - 25;
		addRenderableWidget(new ThemedButton(x, bottom, STUDY_BUTTON_WIDTH, 20, Component.translatable("langcraft.words.study"), button -> openStudy()));
		if (pageCount() > 1) {
			int right = x + inner;
			addRenderableWidget(new ThemedButton(right - NAV_WIDTH * 2 - 3, bottom, NAV_WIDTH, 20, Component.literal("<"), button -> changePage(-1))).active = page > 0;
			addRenderableWidget(new ThemedButton(right - NAV_WIDTH, bottom, NAV_WIDTH, 20, Component.literal(">"), button -> changePage(1))).active = page < pageCount() - 1;
		}
	}

	/** Só calcula a leitura da página aberta, porque um mundo antigo pode ter centenas de palavras. */
	private void loadRows() {
		rows.clear();
		int first = page * rowsPerPage();
		for (int i = first; i < Math.min(words.size(), first + rowsPerPage()); i++) {
			FlashcardDeck.Card card = words.get(i);
			rows.add(new Row(card, GameNames.icon(SubjectType.valueOf(card.type), card.subject), ChallengeClient.segments(card.word, card.language)));
		}
	}

	private void changePage(int delta) {
		page += delta;
		rebuildWidgets();
	}

	private void openStudy() {
		minecraft.setScreen(parent instanceof StudyScreen ? parent : new StudyScreen(parent));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		int target = page + (scrollY < 0 ? 1 : -1);
		if (target >= 0 && target < pageCount()) {
			changePage(target - page);
		}
		return true;
	}

	@Override
	protected void renderPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		UiTheme.Palette palette = UiDraw.palette();
		int pageWidth = pageWidth(MAX_PAGE_WIDTH);
		int left = pageLeft(MAX_PAGE_WIDTH);
		int top = pageTop(MAX_PAGE_HEIGHT);
		int center = pageCenter(MAX_PAGE_WIDTH);
		drawPage(graphics, MAX_PAGE_WIDTH, MAX_PAGE_HEIGHT);
		renderWidgets(graphics, mouseX, mouseY, partialTick);

		UiDraw.centered(graphics, font, Component.translatable("langcraft.words.heading", title, words.size()), center, top + 7, palette.ink());
		int inner = pageWidth - PADDING * 2;
		if (rows.isEmpty()) {
			int y = top + LIST_TOP + 30;
			UiDraw.centered(graphics, font, Component.translatable("langcraft.study.empty"), center, y, palette.ink());
			UiDraw.centered(graphics, font, UiDraw.fit(font, Component.translatable("langcraft.study.empty_hint"), inner), center, y + 14, palette.muted());
			return;
		}
		int x = left + PADDING;
		int methodsLeft = x + inner - METHOD_WIDTH * METHODS.length;
		UiDraw.text(graphics, font, Component.translatable("langcraft.words.legend"), x, top + HEADER_TOP, palette.muted());
		for (int i = 0; i < METHODS.length; i++) {
			Component label = Component.translatable("langcraft.words.method." + METHODS[i].name().toLowerCase(java.util.Locale.ROOT));
			UiDraw.centered(graphics, font, label, methodsLeft + METHOD_WIDTH * i + METHOD_WIDTH / 2, top + HEADER_TOP, palette.muted());
		}
		int y = top + LIST_TOP;
		int textColumns = methodsLeft - x - ICON_WIDTH - 8;
		int wordWidth = textColumns * 3 / 5;
		for (Row row : rows) {
			drawRow(graphics, palette, row, x, y, wordWidth, textColumns - wordWidth, methodsLeft);
			y += ROW_HEIGHT;
		}
		if (pageCount() > 1) {
			Component pages = Component.translatable("langcraft.words.page", page + 1, pageCount());
			int pagesRight = left + pageWidth - PADDING - NAV_WIDTH * 2 - 8;
			UiDraw.text(graphics, font, pages, pagesRight - font.width(pages), top + pageHeight(MAX_PAGE_HEIGHT) - 19, palette.muted());
		}
	}

	private void drawRow(GuiGraphics graphics, UiTheme.Palette palette, Row row, int x, int y, int wordWidth, int nameWidth, int methodsLeft) {
		graphics.renderItem(row.icon(), x, y);
		int wordX = x + ICON_WIDTH;
		int nameX = wordX + wordWidth + 4;
		int textY = y + 8;
		// A leitura fica acima da palavra. Sem espaço para as duas, a linha mostra só a palavra cortada.
		if (FuriganaText.width(font, row.segments(), 1f) <= wordWidth) {
			FuriganaText.draw(graphics, font, row.segments(), wordX, textY - FuriganaText.readingRowHeight(font, row.segments(), 1f), 1f, palette.ink(), palette.shadow());
		} else {
			UiDraw.text(graphics, font, UiDraw.fit(font, row.card().word, wordWidth), wordX, textY, palette.ink());
		}
		UiDraw.text(graphics, font, UiDraw.fit(font, row.card().nativeName, nameWidth), nameX, textY, palette.accent());
		for (int i = 0; i < METHODS.length; i++) {
			drawTally(graphics, palette, row.card().tally(METHODS[i]), methodsLeft + METHOD_WIDTH * i + METHOD_WIDTH / 2, textY);
		}
	}

	/** Mostra acertos / erros, ou um traço para o tipo de resposta que nunca aconteceu. */
	private void drawTally(GuiGraphics graphics, UiTheme.Palette palette, FlashcardDeck.Tally tally, int center, int y) {
		if (tally.right == 0 && tally.wrong == 0) {
			UiDraw.centered(graphics, font, "-", center, y, palette.muted());
			return;
		}
		String right = String.valueOf(tally.right);
		String wrong = String.valueOf(tally.wrong);
		String separator = "/";
		int x = center - (font.width(right) + font.width(separator) + font.width(wrong)) / 2;
		UiDraw.text(graphics, font, right, x, y, palette.green());
		x += font.width(right);
		UiDraw.text(graphics, font, separator, x, y, palette.muted());
		x += font.width(separator);
		UiDraw.text(graphics, font, wrong, x, y, palette.red());
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
