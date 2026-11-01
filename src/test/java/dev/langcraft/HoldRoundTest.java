package dev.langcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langcraft.challenge.HoldRound;
import dev.langcraft.challenge.HoldRound.Verdict;
import dev.langcraft.challenge.HoldTracker;
import org.junit.jupiter.api.Test;

class HoldRoundTest {
	private static Verdict hold(HoldRound<String> round, String item, int ticks) {
		Verdict verdict = Verdict.NONE;
		for (int i = 0; i < ticks && verdict == Verdict.NONE; i++) {
			verdict = round.tick(item);
		}
		return verdict;
	}

	@Test
	void holdingTheTargetForASecondWins() {
		assertEquals(Verdict.WON, hold(new HoldRound<>("maçã", "pedra"), "maçã", HoldTracker.REQUIRED_TICKS));
	}

	@Test
	void holdingAnotherItemForASecondLoses() {
		assertEquals(Verdict.LOST, hold(new HoldRound<>("maçã", "pedra"), "terra", HoldTracker.REQUIRED_TICKS));
	}

	@Test
	void theStartingItemAndAnEmptyHandNeverLose() {
		HoldRound<String> round = new HoldRound<>("maçã", "pedra");
		assertEquals(Verdict.NONE, hold(round, "pedra", 200));
		assertEquals(Verdict.NONE, hold(round, null, 200));
	}

	@Test
	void passingThroughItemsDoesNotAddUp() {
		HoldRound<String> round = new HoldRound<>("maçã", null);
		for (int i = 0; i < 20; i++) {
			assertEquals(Verdict.NONE, hold(round, "item " + i, HoldTracker.REQUIRED_TICKS - 1));
		}
	}

	@Test
	void leavingTheTargetEarlyRestartsItsCount() {
		HoldRound<String> round = new HoldRound<>("maçã", null);
		assertEquals(Verdict.NONE, hold(round, "maçã", HoldTracker.REQUIRED_TICKS - 1));
		assertEquals(Verdict.NONE, round.tick(null));
		assertEquals(Verdict.NONE, hold(round, "maçã", HoldTracker.REQUIRED_TICKS - 1));
		assertEquals(Verdict.WON, round.tick("maçã"));
	}
}
