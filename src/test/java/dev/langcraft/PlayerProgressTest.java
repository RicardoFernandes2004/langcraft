package dev.langcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langcraft.challenge.HoldTracker;
import dev.langcraft.data.PlayerProgress;
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
	void scoreCountsWinsLossesAndTheBestStreak() {
		PlayerProgress progress = PlayerProgress.initial(PlayerProgress.BOTH).afterWin().afterWin().afterWin();
		assertEquals(3, progress.bestStreak());
		progress = progress.afterLoss().afterWin().afterReward();
		assertEquals(0, progress.streak());
		assertEquals(3, progress.bestStreak(), "o recorde sobrevive a derrota e a recompensa");
		assertEquals(4, progress.totalCorrect());
		assertEquals(1, progress.totalWrong());
		PlayerProgress changed = progress.withLanguage(PlayerProgress.KOREAN).withEnabled(false, false);
		assertEquals(4, changed.totalCorrect(), "trocar preferencias mantem o placar");
		assertEquals(3, changed.bestStreak());
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
		assertTrue(PlayerProgress.isValidLanguage("de_de"));
		assertFalse(PlayerProgress.isValidLanguage("fr_fr"));
		assertFalse(PlayerProgress.isValidLanguage(""));
	}

	@Test
	void aChoiceCanHoldAnyMixOfLanguages() {
		String choice = PlayerProgress.choiceOf(java.util.List.of(PlayerProgress.KOREAN, PlayerProgress.GERMAN, PlayerProgress.SPANISH));
		assertEquals("ko_kr,de_de,es_es", choice);
		assertTrue(PlayerProgress.isValidLanguage(choice));
		assertEquals(java.util.List.of(PlayerProgress.KOREAN, PlayerProgress.GERMAN, PlayerProgress.SPANISH), PlayerProgress.languagesOf(choice));
		assertFalse(PlayerProgress.isValidLanguage("ko_kr,fr_fr"));
	}

	@Test
	void eachLanguageHasItsOwnVoiceModel() {
		java.util.List<String> models = java.util.Arrays.stream(dev.langcraft.data.StudyLanguage.values()).map(dev.langcraft.data.StudyLanguage::voskModel).toList();
		assertEquals(5, models.stream().distinct().count());
		assertEquals(dev.langcraft.data.StudyLanguage.GERMAN, dev.langcraft.data.StudyLanguage.byCode(PlayerProgress.GERMAN));
		assertTrue(dev.langcraft.data.StudyLanguage.isLatin(PlayerProgress.SPANISH));
		assertFalse(dev.langcraft.data.StudyLanguage.isLatin(PlayerProgress.KOREAN));
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
