package dev.langcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langcraft.challenge.Consequences;
import dev.langcraft.challenge.Consequences.LossPenalty;
import dev.langcraft.challenge.Minigame;
import org.junit.jupiter.api.Test;

class ConsequencesTest {
	@Test
	void theFirstMistakeIsTheLightest() {
		assertEquals(new LossPenalty(2, 20, 0, 1), Consequences.lossPenalty(1, 2, 20));
	}

	@Test
	void mistakesInARowCostMore() {
		assertEquals(new LossPenalty(4, 40, 1, 2), Consequences.lossPenalty(2, 2, 20));
		assertEquals(new LossPenalty(10, 100, 2, 3), Consequences.lossPenalty(5, 2, 20));
	}

	@Test
	void aLongStreakHasACeiling() {
		assertEquals(new LossPenalty(20, 120, 2, 3), Consequences.lossPenalty(100, 2, 20));
	}

	@Test
	void zeroTurnsThePenaltyOff() {
		LossPenalty penalty = Consequences.lossPenalty(3, 0, 0);
		assertEquals(0, penalty.levels());
		assertEquals(0, penalty.debuffSeconds());
	}

	@Test
	void theStreakAndSpeakingRaiseTheExperience() {
		assertEquals(12, Consequences.winExperience(10, 1, Minigame.QUIZ));
		assertEquals(24, Consequences.winExperience(10, 1, Minigame.SPEAK));
		assertEquals(30, Consequences.winExperience(10, 10, Minigame.HOLD));
		assertEquals(30, Consequences.winExperience(10, 500, Minigame.QUIZ), "o bonus da sequencia tem teto");
		assertEquals(0, Consequences.winExperience(0, 5, Minigame.SPEAK));
		assertTrue(Consequences.winExperience(10, 3, Minigame.SPEAK) > Consequences.winExperience(10, 3, Minigame.QUIZ));
	}
}
