package dev.linguacraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.linguacraft.client.lang.DistractorPicker;
import dev.linguacraft.client.match.AnswerMatcher;
import dev.linguacraft.client.match.KanaNormalizer;
import dev.linguacraft.data.PlayerProgress;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AnswerMatcherTest {
	private static final String KO = PlayerProgress.KOREAN;
	private static final String JA = PlayerProgress.JAPANESE;
	private static final double LIMIT = 0.75;

	@Test
	void koreanIgnoresSpacing() {
		assertTrue(AnswerMatcher.matchesSpoken("다이아몬드 검", "다이아몬드검", KO, LIMIT));
		assertTrue(AnswerMatcher.matchesTyped("다이아몬드 검", "다이아몬드검", KO));
	}

	@Test
	void koreanSpokenAcceptsSmallErrors() {
		assertTrue(AnswerMatcher.matchesSpoken("다이아몬드 곡괭이", "다이야몬드 곡괭이", KO, LIMIT));
	}

	@Test
	void koreanRejectsOtherWords() {
		assertFalse(AnswerMatcher.matchesSpoken("다이아몬드 검", "철 곡괭이", KO, LIMIT));
		assertFalse(AnswerMatcher.matchesTyped("다이아몬드 검", "다이아몬드 곡괭이", KO));
	}

	@Test
	void koreanSpokenAcceptsOnlyTheWord() {
		assertFalse(AnswerMatcher.matchesSpoken("사과", "광부 사과", KO, LIMIT));
		assertFalse(AnswerMatcher.matchesSpoken("사과", "사과 사과", KO, LIMIT));
		assertFalse(AnswerMatcher.matchesSpoken("다이아몬드", "다이아몬드 다이아몬드", KO, LIMIT));
		assertTrue(AnswerMatcher.matchesSpoken("사과", "사과", KO, LIMIT));
		assertTrue(AnswerMatcher.matchesSpoken("종이", "종 이", KO, LIMIT));
		assertTrue(AnswerMatcher.matchesSpoken("참나무 원목", "참나무 원목", KO, LIMIT));
		assertTrue(AnswerMatcher.matchesSpoken("참나무 원목", "참나무원목", KO, LIMIT));
	}

	@Test
	void emptyAnswerNeverMatches() {
		assertFalse(AnswerMatcher.matchesSpoken("검", "   ", KO, 0.0));
		assertFalse(AnswerMatcher.matchesTyped("剣", "", JA));
	}

	@Test
	void japaneseReadingConvertsKanji() {
		assertEquals(KanaNormalizer.toKatakana("剣"), KanaNormalizer.toKatakana("けん"));
	}

	@Test
	void japaneseHomophonesCountAsTheSameSound() {
		assertTrue(AnswerMatcher.matchesSpoken("ダイヤモンドの剣", "ダイヤモンド の 県", JA, LIMIT));
	}

	@Test
	void japaneseTypedAcceptsKana() {
		assertTrue(AnswerMatcher.matchesTyped("ダイヤモンドの剣", "だいやもんどのけん", JA));
		assertTrue(AnswerMatcher.matchesTyped("ダイヤモンドの剣", "ダイヤモンドの剣", JA));
	}

	@Test
	void japaneseRejectsOtherWords() {
		assertFalse(AnswerMatcher.matchesSpoken("ダイヤモンドの剣", "鉄のツルハシ", JA, LIMIT));
		assertFalse(AnswerMatcher.matchesTyped("ダイヤモンドの剣", "鉄の剣", JA));
	}

	@Test
	void distractorsAreUniqueAndExcludeTheAnswer() {
		List<String> names = List.of("검", "검", "활", "방패", "곡괭이", "도끼", "다이아몬드 검", "삽");
		for (int seed = 0; seed < 200; seed++) {
			List<String> picked = DistractorPicker.pick("검", names, 3, new Random(seed));
			assertEquals(3, picked.size());
			assertEquals(3, new HashSet<>(picked).size());
			assertFalse(picked.contains("검"));
		}
	}

	@Test
	void optionsContainTheAnswerExactlyOnce() {
		List<String> names = List.of("활", "방패", "곡괭이", "도끼", "삽", "검");
		List<String> options = DistractorPicker.options("검", names, 3, new Random(1));
		assertEquals(4, options.size());
		assertEquals(1, options.stream().filter("검"::equals).count());
	}

	@Test
	void distractorsPreferSimilarLengths() {
		List<String> names = new java.util.ArrayList<>();
		for (int i = 0; i < 40; i++) {
			names.add("가" + (char) ('가' + i));
		}
		names.add("아주아주아주아주아주아주긴이름");
		for (int seed = 0; seed < 50; seed++) {
			assertFalse(DistractorPicker.pick("나다", names, 3, new Random(seed)).contains("아주아주아주아주아주아주긴이름"));
		}
	}

	@Test
	void microphoneLevelReflectsVolume() {
		assertEquals(0f, dev.linguacraft.client.voice.Microphones.level(new byte[4096], 4096));
		assertEquals(0f, dev.linguacraft.client.voice.Microphones.level(new byte[0], 0));
		byte[] loud = new byte[4096];
		byte[] quiet = new byte[4096];
		for (int i = 0; i < loud.length; i += 2) {
			short sign = (short) (i % 4 == 0 ? 1 : -1);
			writeSample(loud, i, (short) (sign * 30000));
			writeSample(quiet, i, (short) (sign * 300));
		}
		float loudLevel = dev.linguacraft.client.voice.Microphones.level(loud, loud.length);
		float quietLevel = dev.linguacraft.client.voice.Microphones.level(quiet, quiet.length);
		assertTrue(loudLevel > 0.9f && loudLevel <= 1f, "alto: " + loudLevel);
		assertTrue(quietLevel > 0f && quietLevel < loudLevel, "baixo: " + quietLevel);
	}

	private static void writeSample(byte[] audio, int index, short sample) {
		audio[index] = (byte) sample;
		audio[index + 1] = (byte) (sample >> 8);
	}

	@Test
	void fewNamesGiveFewerDistractors() {
		assertEquals(1, DistractorPicker.pick("검", List.of("검", "활"), 3, new Random(1)).size());
	}
}
