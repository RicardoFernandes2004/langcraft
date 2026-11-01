package dev.linguacraft;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.linguacraft.client.match.AnswerMatcher;
import dev.linguacraft.data.PlayerProgress;
import org.junit.jupiter.api.Test;

class LatinAnswerTest {
	private static final double SIMILARITY = 0.75;

	@Test
	void typingIgnoresCaseSpacesAndAccents() {
		assertTrue(AnswerMatcher.matchesTyped("Iron Sword", "iron sword", PlayerProgress.ENGLISH));
		assertTrue(AnswerMatcher.matchesTyped("Iron Sword", "IRONSWORD", PlayerProgress.ENGLISH));
		assertTrue(AnswerMatcher.matchesTyped("Piñón", "pinon", PlayerProgress.SPANISH));
		assertTrue(AnswerMatcher.matchesTyped("Piñón", "piñón", PlayerProgress.SPANISH));
		assertTrue(AnswerMatcher.matchesTyped("Pingüino", "pinguino", PlayerProgress.SPANISH));
	}

	@Test
	void germanAcceptsItsStandardReplacements() {
		assertTrue(AnswerMatcher.matchesTyped("Größe", "groesse", PlayerProgress.GERMAN));
		assertTrue(AnswerMatcher.matchesTyped("Größe", "Größe", PlayerProgress.GERMAN));
		assertTrue(AnswerMatcher.matchesTyped("Äpfel", "aepfel", PlayerProgress.GERMAN));
		assertFalse(AnswerMatcher.matchesTyped("Apfel", "Birne", PlayerProgress.GERMAN));
	}

	@Test
	void typingStillRequiresTheRightWord() {
		assertFalse(AnswerMatcher.matchesTyped("Iron Sword", "iron axe", PlayerProgress.ENGLISH));
		assertFalse(AnswerMatcher.matchesTyped("Iron Sword", "", PlayerProgress.ENGLISH));
	}

	@Test
	void speechToleratesSmallDifferencesButNotOtherItems() {
		assertTrue(AnswerMatcher.matchesSpoken("Iron Sword", "iron sward", PlayerProgress.ENGLISH, SIMILARITY));
		assertTrue(AnswerMatcher.matchesSpoken("Eisenschwert", "eisen schwert", PlayerProgress.GERMAN, SIMILARITY));
		assertFalse(AnswerMatcher.matchesSpoken("Iron Sword", "golden apple", PlayerProgress.ENGLISH, SIMILARITY));
		assertFalse(AnswerMatcher.matchesSpoken("Eisenschwert", "Goldschwert", PlayerProgress.GERMAN, SIMILARITY));
	}
}
