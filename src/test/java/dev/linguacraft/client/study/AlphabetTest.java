package dev.linguacraft.client.study;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AlphabetTest {
	@TempDir
	Path folder;

	private AlphabetProgress progress() {
		return new AlphabetProgress(folder.resolve("sub/alphabet.json"));
	}

	private static int size(String id) {
		return Alphabet.group(id).letters().size();
	}

	@Test
	void eachGroupHasTheExpectedLetters() {
		assertEquals(14, size("ko_consonants"));
		assertEquals(5, size("ko_double"));
		assertEquals(10, size("ko_vowels"));
		assertEquals(11, size("ko_compound"));
		assertEquals(46, size("hiragana_basic"));
		assertEquals(25, size("hiragana_marked"));
		assertEquals(33, size("hiragana_combined"));
		assertEquals(46, size("katakana_basic"));
		assertEquals(25, size("katakana_marked"));
		assertEquals(33, size("katakana_combined"));
	}

	@Test
	void theGlyphsLineUpWithTheirReadings() {
		assertEquals(new Alphabet.Letter("し", "shi"), Alphabet.group("hiragana_basic").letters().get(11));
		assertEquals(new Alphabet.Letter("ん", "n"), Alphabet.group("hiragana_basic").letters().getLast());
		assertEquals(new Alphabet.Letter("ヲ", "wo"), Alphabet.group("katakana_basic").letters().get(44));
		assertEquals(new Alphabet.Letter("ぽ", "po"), Alphabet.group("hiragana_marked").letters().getLast());
		assertEquals(new Alphabet.Letter("じょ", "jo"), Alphabet.group("hiragana_combined").letters().get(26));
		assertEquals(new Alphabet.Letter("ピョ", "pyo"), Alphabet.group("katakana_combined").letters().getLast());
		assertEquals(new Alphabet.Letter("ㅢ", "ui"), Alphabet.group("ko_compound").letters().getLast());
	}

	@Test
	void noGlyphRepeats() {
		long total = Alphabet.GROUPS.stream().mapToLong(group -> group.letters().size()).sum();
		Set<String> glyphs = Alphabet.GROUPS.stream().flatMap(group -> group.letters().stream()).map(Alphabet.Letter::glyph).collect(Collectors.toSet());
		assertEquals(total, glyphs.size());
	}

	@Test
	void optionsNeverHaveTwoRightAnswers() {
		Alphabet.Group marked = Alphabet.group("hiragana_marked");
		Alphabet.Letter ji = marked.letters().get(6);
		for (int seed = 0; seed < 200; seed++) {
			List<Alphabet.Letter> options = AlphabetProgress.options(ji, marked, 4, new Random(seed));
			assertEquals(4, options.size());
			assertTrue(options.contains(ji));
			assertEquals(4, options.stream().map(Alphabet.Letter::romaji).distinct().count());
		}
	}

	@Test
	void theSmallestGroupStillHasFourOptions() {
		Alphabet.Group doubles = Alphabet.group("ko_double");
		assertEquals(4, AlphabetProgress.options(doubles.letters().getFirst(), doubles, 4, new Random(1)).size());
	}

	@Test
	void levelsRiseSlowlyFallFastAndAreSaved() {
		Alphabet.Group vowels = Alphabet.group("ko_vowels");
		Alphabet.Letter letter = vowels.letters().getFirst();
		AlphabetProgress progress = progress();
		for (int i = 0; i < 3; i++) {
			progress.answered(letter, true);
		}
		assertEquals(3, progress.level(letter));
		assertEquals(1, progress.learned(vowels));
		progress.answered(letter, false);
		assertEquals(1, progress.level(letter));
		assertEquals(0, progress.learned(vowels));
		for (int i = 0; i < 20; i++) {
			progress.answered(letter, true);
		}
		assertEquals(AlphabetProgress.MAX_LEVEL, progress.level(letter));
		progress.rememberGroup("ko_vowels");

		AlphabetProgress reloaded = progress();
		assertEquals(AlphabetProgress.MAX_LEVEL, reloaded.level(letter));
		assertEquals("ko_vowels", reloaded.lastGroup());
	}

	@Test
	void practicePrefersTheLettersLeastKnown() {
		Alphabet.Group vowels = Alphabet.group("ko_vowels");
		AlphabetProgress progress = progress();
		for (Alphabet.Letter letter : vowels.letters().subList(0, 8)) {
			progress.answered(letter, true);
		}
		List<Alphabet.Letter> session = progress.session(vowels, 2, new Random(3));
		assertEquals(Set.copyOf(vowels.letters().subList(8, 10)), Set.copyOf(session));
	}
}
