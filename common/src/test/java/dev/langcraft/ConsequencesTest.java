package dev.langcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langcraft.challenge.Consequences;
import dev.langcraft.challenge.Consequences.Debuff;
import dev.langcraft.challenge.Consequences.LossPenalty;
import dev.langcraft.challenge.Minigame;
import org.junit.jupiter.api.Test;

class ConsequencesTest {
	@Test
	void theFirstMistakeIsTheLightest() {
		assertEquals(new LossPenalty(2, 1), Consequences.lossPenalty(1, 2));
	}

	@Test
	void mistakesInARowCostMore() {
		assertEquals(new LossPenalty(4, 2), Consequences.lossPenalty(2, 2));
		assertEquals(new LossPenalty(10, 3), Consequences.lossPenalty(5, 2));
	}

	@Test
	void aLongStreakHasACeiling() {
		assertEquals(new LossPenalty(20, 3), Consequences.lossPenalty(100, 2));
	}

	@Test
	void zeroTurnsThePenaltyOff() {
		assertEquals(0, Consequences.lossPenalty(3, 0).levels());
		assertEquals(0, Consequences.disasterDebuff(3, 3, 0).seconds());
	}

	@Test
	void eachDisasterInTheSameStreakSlowsYouMore() {
		assertEquals(new Debuff(60, 0), Consequences.disasterDebuff(3, 3, 60));
		assertEquals(new Debuff(120, 1), Consequences.disasterDebuff(6, 3, 60));
		assertEquals(new Debuff(180, 2), Consequences.disasterDebuff(9, 3, 60));
		assertEquals(new Debuff(180, 2), Consequences.disasterDebuff(90, 3, 60), "o efeito tem teto");
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
