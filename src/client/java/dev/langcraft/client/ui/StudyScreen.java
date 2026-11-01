package dev.langcraft.client.ui;

import dev.langcraft.challenge.SubjectType;
import dev.langcraft.client.ChallengeClient;
import dev.langcraft.client.lang.Furigana;
import dev.langcraft.client.lang.GameNames;
import dev.langcraft.client.study.FlashcardDeck;
import dev.langcraft.client.study.WorldFlashcards;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Revisão com flashcards das palavras já encontradas nos desafios, com repetição espaçada: a rodada normal só
 * traz as palavras que chegaram na hora. Estudar aqui não muda o placar nem tira nada do sorteio dos desafios.
 */
public final class StudyScreen extends NotebookScreen {
	private static final int MAX_PAGE_WIDTH = 320;
	private static final int MAX_PAGE_HEIGHT = 224;
	private static final int FILTER_HEIGHT = 16;
	private static final int FILTER_GAP = 3;
	private static final int BOTTOM_BUTTON_WIDTH = 110;
	private static final int TOP_BUTTON_WIDTH = 60;
	private static final int PADDING = 8;
	private static final float WORD_SCALE = 2f;
	private static final long MILLIS_PER_HOUR = 60 * 60 * 1000L;
	private static final long HOURS_PER_DAY = 24;

	private final @Nullable Screen parent;
	private final FlashcardDeck deck = Objects.requireNonNull(WorldFlashcards.current(), "Os flashcards só abrem dentro de um mundo");
	private final List<FlashcardDeck.Card> queue = new ArrayList<>();
	private @Nullable String language;
	private boolean nativeFront;
	private boolean revealed;
	private boolean sessionDone;
	private boolean extraReview;
	private int reviewed;
	private int missed;
	private @Nullable FlashcardDeck.Card card;
	private List<Furigana.Segment> segments = List.of();
	private ItemStack icon = ItemStack.EMPTY;

	public StudyScreen(@Nullable Screen parent) {
		super(Component.translatable("langcraft.study.title"));
		this.parent = parent;
		startSession();
	}

	/** A palavra que está na tela. */
	public @Nullable String currentWord() {
		return card == null ? null : card.word;
	}

	private void startSession() {
		queue.clear();
		long now = System.currentTimeMillis();
		queue.addAll(extraReview ? deck.session(scope(language), new Random()) : deck.dueSession(scope(language), now, new Random()));
		reviewed = 0;
		missed = 0;
		sessionDone = false;
		showNext();
	}

	private void showNext() {
		revealed = false;
		if (queue.isEmpty()) {
			card = null;
			sessionDone = reviewed > 0;
			return;
		}
		card = queue.removeFirst();
		segments = ChallengeClient.segments(card.word, card.language);
		icon = GameNames.icon(SubjectType.valueOf(card.type), card.subject);
	}

	private void answer(boolean right) {
		deck.studied(card, right, System.currentTimeMillis());
		reviewed++;
		if (!right) {
			// Volta no fim da rodada, até o jogador acertar.
			missed++;
			queue.add(card);
		}
		showNext();
		rebuildWidgets();
	}

	@Override
	protected void init() {
		int left = pageLeft(MAX_PAGE_WIDTH);
		int top = pageTop(MAX_PAGE_HEIGHT);
		int pageWidth = pageWidth(MAX_PAGE_WIDTH);
		int center = pageCenter(MAX_PAGE_WIDTH);
		addRenderableWidget(new ThemedButton(left + PADDING, top + 4, TOP_BUTTON_WIDTH, 14,
				Component.translatable("langcraft.study.open_list"), button -> minecraft.setScreen(new WordListScreen(this))));
		ThemedButton alphabet = new ThemedButton(left + pageWidth - PADDING - TOP_BUTTON_WIDTH, top + 4, TOP_BUTTON_WIDTH, 14,
				Component.translatable("langcraft.study.open_alphabet"), button -> minecraft.setScreen(new AlphabetScreen(this)));
		alphabet.active = AlphabetScreen.canOpen();
		if (!alphabet.active) {
			alphabet.setTooltip(Tooltip.create(Component.translatable("langcraft.alphabet.unavailable")));
		}
		addRenderableWidget(alphabet);
		int inner = pageWidth - PADDING * 2;
		int filterWidth = (inner - FILTER_GAP) / 2;
		int x = left + PADDING;
		int y = top + 20;
		ThemedButton filter = new ThemedButton(x, y, filterWidth, FILTER_HEIGHT, languageFilterLabel(language), button -> {
			language = nextLanguage(language);
			extraReview = false;
			startSession();
			rebuildWidgets();
		});
		filter.active = hasLanguageChoice();
		addRenderableWidget(filter);
		Component front = Component.translatable(nativeFront ? "langcraft.study.front_native" : "langcraft.study.front_word");
		addRenderableWidget(new ThemedButton(x + filterWidth + FILTER_GAP, y, filterWidth, FILTER_HEIGHT, front, button -> {
			nativeFront = !nativeFront;
			revealed = false;
			rebuildWidgets();
		}));

		int bottom = top + pageHeight(MAX_PAGE_HEIGHT) - 28;
		int leftX = center - BOTTOM_BUTTON_WIDTH - 3;
		int rightX = center + 3;
		if (card == null) {
			if (deck.size(scope(language)) > 0) {
				addBottom(leftX, bottom, "langcraft.study.extra", this::startExtraReview);
				addBottom(rightX, bottom, "langcraft.study.close", this::onClose);
			} else {
				addBottom(center - BOTTOM_BUTTON_WIDTH / 2, bottom, "langcraft.study.close", this::onClose);
			}
		} else if (revealed) {
			addBottom(leftX, bottom, "langcraft.study.wrong", () -> answer(false));
			Component right = Component.translatable("langcraft.study.right");
			if (card.isDue(System.currentTimeMillis())) {
				right = Component.translatable("langcraft.study.right_in", right, daysText(deck.nextReviewDays(card)));
			}
			addRenderableWidget(new ThemedButton(rightX, bottom, BOTTOM_BUTTON_WIDTH, 20, right, button -> answer(true)));
		} else {
			addBottom(center - BOTTOM_BUTTON_WIDTH / 2, bottom, "langcraft.study.show", () -> {
				revealed = true;
				rebuildWidgets();
			});
		}
	}

	private void addBottom(int x, int y, String key, Runnable action) {
		addRenderableWidget(new ThemedButton(x, y, BOTTOM_BUTTON_WIDTH, 20, Component.translatable(key), button -> action.run()));
	}

	/** Revê todas as palavras, mesmo sem ter chegado a hora delas. */
	private void startExtraReview() {
		extraReview = true;
		startSession();
		rebuildWidgets();
	}

	private static Component daysText(int days) {
		return days == 1 ? Component.translatable("langcraft.study.one_day") : Component.translatable("langcraft.study.days", days);
	}

	private static Component timeUntil(long millis) {
		long hours = Math.max(1, (millis + MILLIS_PER_HOUR - 1) / MILLIS_PER_HOUR);
		return hours < HOURS_PER_DAY ? Component.translatable("langcraft.study.hours", hours) : daysText((int) ((hours + HOURS_PER_DAY - 1) / HOURS_PER_DAY));
	}

	@Override
	protected void renderPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		UiTheme.Palette palette = UiDraw.palette();
		int pageWidth = pageWidth(MAX_PAGE_WIDTH);
		int top = pageTop(MAX_PAGE_HEIGHT);
		int center = pageCenter(MAX_PAGE_WIDTH);
		drawPage(graphics, MAX_PAGE_WIDTH, MAX_PAGE_HEIGHT);
		renderWidgets(graphics, mouseX, mouseY, partialTick);

		long now = System.currentTimeMillis();
		UiDraw.centered(graphics, font, Component.translatable("langcraft.study.due", deck.dueCount(scope(language), now)), center, top + 7, palette.ink());
		int cardTop = top + 44;
		int textWidth = pageWidth - PADDING * 2;
		if (card != null) {
			renderCard(graphics, palette, center, cardTop, textWidth);
		} else if (sessionDone) {
			UiDraw.centered(graphics, font, Component.translatable("langcraft.study.done"), center, cardTop + 30, palette.green());
			UiDraw.centered(graphics, font, Component.translatable("langcraft.study.done_stats", reviewed, missed), center, cardTop + 44, palette.muted());
			if (deck.dueCount(scope(language), now) == 0 && deck.size(scope(language)) > 0) {
				UiDraw.centered(graphics, font, Component.translatable("langcraft.study.next", timeUntil(deck.nextDueAt(scope(language)) - now)), center, cardTop + 58, palette.muted());
			}
		} else if (deck.size(scope(language)) > 0) {
			UiDraw.centered(graphics, font, Component.translatable("langcraft.study.nothing_due"), center, cardTop + 30, palette.ink());
			UiDraw.centered(graphics, font, Component.translatable("langcraft.study.next", timeUntil(deck.nextDueAt(scope(language)) - now)), center, cardTop + 44, palette.muted());
		} else {
			UiDraw.centered(graphics, font, Component.translatable("langcraft.study.empty"), center, cardTop + 30, palette.ink());
			UiDraw.centered(graphics, font, UiDraw.fit(font, Component.translatable("langcraft.study.empty_hint"), textWidth), center, cardTop + 44, palette.muted());
		}
	}

	private void renderCard(GuiGraphics graphics, UiTheme.Palette palette, int center, int top, int textWidth) {
		UiDraw.centered(graphics, font, Component.translatable("langcraft.study.remaining", queue.size() + 1), center, top, palette.muted());
		int y = top + 14;
		if (nativeFront) {
			y = drawMeaning(graphics, palette, center, y);
			if (revealed) {
				drawWord(graphics, palette, center, y + 8, textWidth);
			}
		} else {
			y = drawWord(graphics, palette, center, y, textWidth);
			if (revealed) {
				drawMeaning(graphics, palette, center, y + 8);
			}
		}
		if (revealed) {
			int statsY = pageTop(MAX_PAGE_HEIGHT) + pageHeight(MAX_PAGE_HEIGHT) - 54;
			if (GameNames.isCommunityName(card.language, SubjectType.valueOf(card.type), card.subject)) {
				UiDraw.centered(graphics, font, Component.translatable("langcraft.study.community"), center, statsY - 11, palette.muted());
			}
			UiDraw.centered(graphics, font, Component.translatable("langcraft.study.stats", card.correct, card.wrong), center, statsY, palette.muted());
			if (card.lastWrongAnswer != null) {
				UiDraw.centered(graphics, font, UiDraw.fit(font, Component.translatable("langcraft.study.confused", card.lastWrongAnswer), textWidth), center, statsY + 11, palette.red());
			}
		}
	}

	private int drawWord(GuiGraphics graphics, UiTheme.Palette palette, int center, int y, int textWidth) {
		float scale = FuriganaText.width(font, segments, WORD_SCALE) <= textWidth ? WORD_SCALE : 1f;
		int wordWidth = FuriganaText.width(font, segments, scale);
		FuriganaText.draw(graphics, font, segments, center - wordWidth / 2, y, scale, palette.ink(), palette.shadow());
		return y + FuriganaText.height(font, segments, scale);
	}

	private int drawMeaning(GuiGraphics graphics, UiTheme.Palette palette, int center, int y) {
		graphics.renderItem(icon, center - 8, y);
		UiDraw.centered(graphics, font, card.nativeName, center, y + 19, palette.accent());
		return y + 30;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
