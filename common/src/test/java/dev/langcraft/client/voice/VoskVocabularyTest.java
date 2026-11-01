package dev.langcraft.client.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langcraft.data.PlayerProgress;
import java.util.List;
import org.junit.jupiter.api.Test;

class VoskVocabularyTest {
	@Test
	void latinNamesAreSplitAndLowered() {
		assertEquals(List.of("iron", "sword"), VoskVocabulary.words("Iron Sword", PlayerProgress.ENGLISH));
		assertEquals(List.of("eisenschwert"), VoskVocabulary.words("Eisenschwert", PlayerProgress.GERMAN));
		assertEquals(List.of("espada", "de", "hierro"), VoskVocabulary.words("Espada de  hierro", PlayerProgress.SPANISH));
	}

	@Test
	void japaneseNamesAreSplitByTheDictionary() {
		List<String> words = VoskVocabulary.words("ダイヤモンドの剣", PlayerProgress.JAPANESE);
		assertEquals(List.of("ダイヤモンド", "の", "剣"), words);
		assertTrue(words.stream().noneMatch(String::isBlank));
	}
}
