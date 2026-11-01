package dev.linguacraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.linguacraft.challenge.PlayerTitle;
import org.junit.jupiter.api.Test;

class PlayerTitleTest {
	@Test
	void titlesChangeExactlyAtTheirThresholds() {
		assertEquals(PlayerTitle.NOVICE, PlayerTitle.of(0));
		assertEquals(PlayerTitle.NOVICE, PlayerTitle.of(24));
		assertEquals(PlayerTitle.APPRENTICE, PlayerTitle.of(25));
		assertEquals(PlayerTitle.STUDENT, PlayerTitle.of(100));
		assertEquals(PlayerTitle.POLYGLOT, PlayerTitle.of(250));
		assertEquals(PlayerTitle.TRANSLATOR, PlayerTitle.of(999));
		assertEquals(PlayerTitle.MASTER, PlayerTitle.of(1000));
		assertEquals(PlayerTitle.MASTER, PlayerTitle.of(1_000_000));
	}

	@Test
	void theLastTitleHasNoNext() {
		assertEquals(PlayerTitle.APPRENTICE, PlayerTitle.NOVICE.next());
		assertNull(PlayerTitle.MASTER.next());
	}

	@Test
	void thresholdsAlwaysRise() {
		PlayerTitle[] titles = PlayerTitle.values();
		for (int i = 1; i < titles.length; i++) {
			assertTrue(titles[i].requiredCorrect() > titles[i - 1].requiredCorrect());
		}
	}
}
