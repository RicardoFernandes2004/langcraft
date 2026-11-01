package dev.linguacraft.client.study;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.linguacraft.data.PlayerProgress;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlashcardDeckTest {
	@TempDir
	Path folder;

	private Path file() {
		return folder.resolve("sub/flashcards.json");
	}

	private static void record(FlashcardDeck deck, String language, String subject, boolean won, String wrong) {
		deck.record(language, "ITEM", subject, "palavra " + subject, "nome " + subject, AnswerMethod.QUIZ, won, wrong);
	}

	@Test
	void eachAnswerMethodIsCountedApart() {
		FlashcardDeck deck = new FlashcardDeck(file());
		deck.record(PlayerProgress.KOREAN, "ITEM", "maçã", "사과", "Maçã", AnswerMethod.SPEAK, true, null);
		deck.record(PlayerProgress.KOREAN, "ITEM", "maçã", "사과", "Maçã", AnswerMethod.SPEAK, false, null);
		deck.record(PlayerProgress.KOREAN, "ITEM", "maçã", "사과", "Maçã", AnswerMethod.TYPED, true, null);
		deck.record(PlayerProgress.KOREAN, "ITEM", "maçã", "사과", "Maçã", AnswerMethod.QUIZ, false, null);

		FlashcardDeck.Card card = new FlashcardDeck(file()).words(null).getFirst();
		assertEquals(1, card.tally(AnswerMethod.SPEAK).right);
		assertEquals(1, card.tally(AnswerMethod.SPEAK).wrong);
		assertEquals(1, card.tally(AnswerMethod.TYPED).right);
		assertEquals(0, card.tally(AnswerMethod.TYPED).wrong);
		assertEquals(1, card.tally(AnswerMethod.QUIZ).wrong);
		assertEquals(0, card.tally(AnswerMethod.HOLD).right);
		assertEquals(2, card.correct);
		assertEquals(2, card.wrong);
	}

	@Test
	void savesAndReloadsWithTheAnswers() {
		FlashcardDeck deck = new FlashcardDeck(file());
		record(deck, PlayerProgress.KOREAN, "minecraft:apple", false, "화강암");
		record(deck, PlayerProgress.KOREAN, "minecraft:apple", true, null);

		List<FlashcardDeck.Card> cards = new FlashcardDeck(file()).session(null, new Random(1));
		assertEquals(1, cards.size());
		FlashcardDeck.Card card = cards.getFirst();
		assertEquals(1, card.correct);
		assertEquals(1, card.wrong);
		assertEquals("화강암", card.lastWrongAnswer, "um acerto depois nao apaga a anotacao");
	}

	@Test
	void theSameItemInEachLanguageIsItsOwnCard() {
		FlashcardDeck deck = new FlashcardDeck(file());
		record(deck, PlayerProgress.KOREAN, "minecraft:apple", true, null);
		record(deck, PlayerProgress.JAPANESE, "minecraft:apple", true, null);
		assertEquals(2, deck.size(null));
		assertEquals(1, deck.size(PlayerProgress.KOREAN));
		assertEquals(PlayerProgress.JAPANESE, deck.session(PlayerProgress.JAPANESE, new Random(1)).getFirst().language);
	}

	@Test
	void hardestWordsComeFirst() {
		FlashcardDeck deck = new FlashcardDeck(file());
		record(deck, PlayerProgress.KOREAN, "fácil", true, null);
		record(deck, PlayerProgress.KOREAN, "difícil", false, null);
		record(deck, PlayerProgress.KOREAN, "difícil", false, null);
		record(deck, PlayerProgress.KOREAN, "médio", false, null);
		List<String> order = deck.session(null, new Random(1)).stream().map(card -> card.subject).toList();
		assertEquals(List.of("difícil", "médio", "fácil"), order);

		FlashcardDeck.Card easy = deck.session(null, new Random(1)).getLast();
		deck.studied(easy, false, 0);
		deck.studied(easy, false, 0);
		deck.studied(easy, false, 0);
		deck.studied(easy, false, 0);
		assertEquals("fácil", deck.session(null, new Random(1)).getFirst().subject, "errar no estudo tambem conta");
	}

	private static final long DAY = 24 * 60 * 60 * 1000L;
	private static final long START = 1_000 * DAY;

	private FlashcardDeck.Card onlyCard(FlashcardDeck deck) {
		return deck.words(null).getFirst();
	}

	@Test
	void newWordsAreDueRightAway() {
		FlashcardDeck deck = new FlashcardDeck(file());
		record(deck, PlayerProgress.KOREAN, "minecraft:apple", true, null);
		assertEquals(1, deck.dueCount(null, START));
		assertEquals(1, deck.dueSession(null, START, new Random(1)).size());
	}

	@Test
	void eachRightAnswerSpacesTheNextReviewFurther() {
		FlashcardDeck deck = new FlashcardDeck(file());
		record(deck, PlayerProgress.KOREAN, "minecraft:apple", true, null);
		FlashcardDeck.Card card = onlyCard(deck);
		long now = START;
		for (int days : new int[] {1, 3, 7, 14, 30, 60, 60}) {
			assertEquals(days, deck.nextReviewDays(card));
			deck.studied(card, true, now);
			assertEquals(now + days * DAY, card.dueAt);
			assertEquals(0, deck.dueCount(null, card.dueAt - 1), "antes da hora, nao aparece");
			assertEquals(1, deck.dueCount(null, card.dueAt), "na hora, aparece");
			now = card.dueAt;
		}
	}

	@Test
	void aMissStartsTheSpacingOver() {
		FlashcardDeck deck = new FlashcardDeck(file());
		record(deck, PlayerProgress.KOREAN, "minecraft:apple", true, null);
		FlashcardDeck.Card card = onlyCard(deck);
		deck.studied(card, true, START);
		deck.studied(card, true, card.dueAt);
		deck.studied(card, false, card.dueAt);
		assertEquals(1, deck.dueCount(null, START));
		assertEquals(1, deck.nextReviewDays(card));
	}

	@Test
	void extraReviewDoesNotShortenTheSpacing() {
		FlashcardDeck deck = new FlashcardDeck(file());
		record(deck, PlayerProgress.KOREAN, "minecraft:apple", true, null);
		FlashcardDeck.Card card = onlyCard(deck);
		deck.studied(card, true, START);
		long due = card.dueAt;
		deck.studied(card, true, START + 1);
		assertEquals(due, card.dueAt);
		assertEquals(3, deck.nextReviewDays(card));
		assertEquals(1, deck.session(null, new Random(1)).size(), "a revisao extra ainda alcanca a carta");
	}

	@Test
	void challengesOnlyMoveTheReviewWhenLost() {
		FlashcardDeck deck = new FlashcardDeck(file());
		record(deck, PlayerProgress.KOREAN, "minecraft:apple", true, null);
		FlashcardDeck.Card card = onlyCard(deck);
		deck.studied(card, true, START);
		deck.studied(card, true, card.dueAt);
		long due = card.dueAt;
		record(deck, PlayerProgress.KOREAN, "minecraft:apple", true, null);
		assertEquals(due, onlyCard(deck).dueAt, "vencer um desafio nao adia a revisao");
		record(deck, PlayerProgress.KOREAN, "minecraft:apple", false, null);
		assertEquals(1, deck.dueCount(null, START), "perder traz a revisao para agora");
		assertEquals(3, deck.nextReviewDays(onlyCard(deck)), "perder volta um passo, sem zerar");
	}

	@Test
	void theScheduleIsSavedAndNextDueIsTheEarliest() {
		FlashcardDeck deck = new FlashcardDeck(file());
		record(deck, PlayerProgress.KOREAN, "a", true, null);
		record(deck, PlayerProgress.KOREAN, "b", true, null);
		for (FlashcardDeck.Card card : deck.words(null)) {
			deck.studied(card, true, START);
		}
		FlashcardDeck.Card a = deck.words(null).getLast();
		deck.studied(a, true, a.dueAt);

		FlashcardDeck reloaded = new FlashcardDeck(file());
		assertEquals(START + DAY, reloaded.nextDueAt(null));
		assertEquals(0, reloaded.dueCount(null, START));
		assertEquals(1, reloaded.dueCount(null, START + DAY));
	}

	@Test
	void eachFileIsItsOwnWorld() {
		FlashcardDeck first = new FlashcardDeck(folder.resolve("mundo1/flashcards.json"));
		record(first, PlayerProgress.KOREAN, "minecraft:apple", true, null);
		assertEquals(0, new FlashcardDeck(folder.resolve("mundo2/flashcards.json")).size(null));
		assertEquals(1, new FlashcardDeck(folder.resolve("mundo1/flashcards.json")).size(null));
	}

	@Test
	void theListShowsTheNewestWordsFirst() {
		FlashcardDeck deck = new FlashcardDeck(file());
		record(deck, PlayerProgress.KOREAN, "primeira", true, null);
		record(deck, PlayerProgress.JAPANESE, "segunda", false, null);
		record(deck, PlayerProgress.KOREAN, "terceira", true, null);
		assertEquals(List.of("terceira", "segunda", "primeira"), deck.words(null).stream().map(card -> card.subject).toList());
		assertEquals(List.of("terceira", "primeira"), deck.words(PlayerProgress.KOREAN).stream().map(card -> card.subject).toList());
	}

	@Test
	void serverAddressesBecomeSafeFileNames() {
		assertEquals("play.exemplo.com_25565", WorldFlashcards.safeName("play.exemplo.com:25565"));
		assertEquals("servidor", WorldFlashcards.safeName(""));
		assertTrue(!WorldFlashcards.safeName("../../segredo").contains("/"));
	}

	@Test
	void longAnswersAreCut() {
		FlashcardDeck deck = new FlashcardDeck(file());
		record(deck, PlayerProgress.KOREAN, "minecraft:apple", false, "가".repeat(500));
		assertEquals(40, deck.session(null, new Random(1)).getFirst().lastWrongAnswer.length());
	}

	@Test
	void aBrokenFileIsKeptAside() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), "isto nao e json");
		FlashcardDeck deck = new FlashcardDeck(file());
		assertEquals(0, deck.size(null));
		assertTrue(Files.exists(file().resolveSibling("flashcards.json.invalido")));
	}
}
