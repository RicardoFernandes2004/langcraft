package dev.linguacraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.linguacraft.challenge.HoldTracker;
import dev.linguacraft.data.PlayerProgress;
import org.junit.jupiter.api.Test;

class PlayerProgressTest {
	@Test
	void fiveWinsMakeARewardDue() {
		PlayerProgress progress = PlayerProgress.initial(PlayerProgress.BOTH);
		for (int i = 0; i < 4; i++) {
			progress = progress.afterWin();
			assertFalse(progress.rewardDue(5));
		}
		progress = progress.afterWin();
		assertTrue(progress.rewardDue(5));
		assertEquals(0, progress.afterReward().streak());
	}

	@Test
	void lossResetsAndKeepsPreferences() {
		PlayerProgress progress = PlayerProgress.initial(PlayerProgress.KOREAN).withEnabled(false, true).afterWin().afterWin();
		PlayerProgress lost = progress.afterLoss();
		assertEquals(0, lost.streak());
		assertEquals(PlayerProgress.KOREAN, lost.language());
		assertFalse(lost.itemsEnabled());
		assertTrue(lost.mobsEnabled());
	}

	@Test
	void languageCountsAsChosenOnlyAfterPicking() {
		PlayerProgress initial = PlayerProgress.initial(PlayerProgress.BOTH);
		assertFalse(initial.languageChosen());
		assertFalse(initial.afterWin().afterLoss().languageChosen());
		PlayerProgress chosen = initial.withLanguage(PlayerProgress.KOREAN);
		assertTrue(chosen.languageChosen());
		assertTrue(chosen.afterWin().afterLoss().afterReward().withEnabled(false, false).languageChosen());
	}

	@Test
	void bothMeansEachLanguage() {
		assertEquals(java.util.List.of(PlayerProgress.KOREAN, PlayerProgress.JAPANESE), PlayerProgress.languagesOf(PlayerProgress.BOTH));
		assertEquals(java.util.List.of(PlayerProgress.JAPANESE), PlayerProgress.languagesOf(PlayerProgress.JAPANESE));
		assertTrue(PlayerProgress.isValidLanguage(PlayerProgress.BOTH));
		assertFalse(PlayerProgress.isValidLanguage("de_de"));
	}

	@Test
	void disabledGoalNeverGivesReward() {
		assertFalse(PlayerProgress.initial(PlayerProgress.BOTH).afterWin().rewardDue(0));
	}

	@Test
	void holdNeedsConsecutiveTicks() {
		HoldTracker tracker = new HoldTracker();
		for (int i = 0; i < HoldTracker.REQUIRED_TICKS - 1; i++) {
			assertFalse(tracker.tick(true));
		}
		assertFalse(tracker.tick(false));
		for (int i = 0; i < HoldTracker.REQUIRED_TICKS - 1; i++) {
			assertFalse(tracker.tick(true));
		}
		assertTrue(tracker.tick(true));
	}
}
