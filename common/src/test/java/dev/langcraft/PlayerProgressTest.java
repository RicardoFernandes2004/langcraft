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
		assertEquals(5, progress.streak(), "a recompensa nao zera a sequencia");
		for (int i = 0; i < 4; i++) {
			progress = progress.afterWin();
			assertFalse(progress.rewardDue(5));
		}
		assertTrue(progress.afterWin().rewardDue(5), "a proxima vem no multiplo seguinte");
	}

	@Test
	void mistakesInARowOpenTheDisaster() {
		PlayerProgress progress = PlayerProgress.initial(PlayerProgress.BOTH).afterLoss().afterLoss();
		assertEquals(2, progress.lossStreak());
		assertFalse(progress.disasterDue(3));
		progress = progress.afterLoss();
		assertTrue(progress.disasterDue(3));
		assertFalse(progress.afterLoss().disasterDue(3));
		assertTrue(progress.afterLoss().afterLoss().afterLoss().disasterDue(3), "continua a cada tres erros");
		assertEquals(0, progress.afterWin().lossStreak(), "um acerto encerra a sequencia de erros");
		assertFalse(progress.disasterDue(0), "meta zerada desliga");
	}

	@Test
	void nextMilestoneAlwaysLiesAhead() {
		assertEquals(5, PlayerProgress.nextMilestone(0, 5));
		assertEquals(5, PlayerProgress.nextMilestone(4, 5));
		assertEquals(10, PlayerProgress.nextMilestone(5, 5));
		assertEquals(0, PlayerProgress.nextMilestone(3, 0));
	}

	@Test
	void theDailyComesOnceADay() {
		PlayerProgress progress = PlayerProgress.initial(PlayerProgress.BOTH);
		assertTrue(progress.dailyAvailable(0), "pode sair desde o primeiro dia");
		progress = progress.withLastDailyDay(4);
		assertFalse(progress.dailyAvailable(4));
		assertTrue(progress.dailyAvailable(5));
	}

	@Test
	void shieldsAndDailySurviveTheScore() {
		PlayerProgress progress = PlayerProgress.initial(PlayerProgress.BOTH);
		assertEquals(PlayerProgress.NO_DAILY_YET, progress.lastDailyDay());
		progress = progress.withShields(2).withLastDailyDay(7).afterLoss().afterWin().withLanguage(PlayerProgress.KOREAN).withEnabled(false, true);
		assertEquals(2, progress.shields());
		assertEquals(7, progress.lastDailyDay());
		assertEquals(0, progress.withShields(-1).shields());
	}

	@Test
	void scoreCountsWinsLossesAndTheBestStreak() {
		PlayerProgress progress = PlayerProgress.initial(PlayerProgress.BOTH).afterWin().afterWin().afterWin();
		assertEquals(3, progress.bestStreak());
		progress = progress.afterLoss().afterWin();
		assertEquals(1, progress.streak());
		assertEquals(3, progress.bestStreak(), "o recorde sobrevive a derrota");
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
		assertTrue(chosen.afterWin().afterLoss().withEnabled(false, false).languageChosen());
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

	@Test
	void preferencesAndDiscoveriesSurviveTheScore() {
		PlayerProgress progress = PlayerProgress.initial(PlayerProgress.BOTH)
				.withChallenge("item_quiz", false).withBlocked("zombie:cola", true)
				.withDiscoveriesStarted(java.util.List.of("minecraft:dirt")).withVisitedBiome("minecraft:plains").withSeenItem("minecraft:apple");
		progress = progress.afterWin().afterLoss().withShields(1).withLanguage(PlayerProgress.KOREAN).withEnabled(true, false).withLastDailyDay(3);
		assertFalse(progress.allows("item_quiz"));
		assertTrue(progress.allows("item_speak"));
		assertTrue(progress.blocks("zombie:cola"));
		assertTrue(progress.discoveries().started());
		assertEquals(java.util.Set.of("minecraft:dirt", "minecraft:apple"), progress.discoveries().items());
		assertEquals(java.util.Set.of("minecraft:plains"), progress.discoveries().biomes());
		assertTrue(progress.withChallenge("item_quiz", true).allows("item_quiz"));
		assertFalse(progress.withBlocked("zombie:cola", false).blocks("zombie:cola"));
	}

	@Test
	void codecKeepsTheNewFieldsAndReadsOldSaves() {
		PlayerProgress progress = PlayerProgress.initial(PlayerProgress.KOREAN).withChallenge("biome", false).withBlocked("zombie:cola", true)
				.withDiscoveriesStarted(java.util.List.of("minecraft:dirt")).withVisitedBiome("minecraft:desert");
		com.google.gson.JsonElement json = PlayerProgress.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, progress).result().orElseThrow();
		PlayerProgress read = PlayerProgress.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).result().orElseThrow();
		assertEquals(progress, read);

		com.google.gson.JsonObject old = com.google.gson.JsonParser.parseString(
				"{\"language\":\"ko_kr\",\"streak\":2,\"itemsEnabled\":true,\"mobsEnabled\":true}").getAsJsonObject();
		PlayerProgress fromOld = PlayerProgress.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, old).result().orElseThrow();
		assertEquals(2, fromOld.streak());
		assertFalse(fromOld.discoveries().started(), "um mundo antigo comeca a contar as descobertas do zero");
		assertTrue(fromOld.allows("item_speak"));
	}
}
