package dev.linguacraft.client.study;

import dev.linguacraft.data.PlayerProgress;
import java.util.ArrayList;
import java.util.List;

/** As letras de cada idioma, em grupos para treinar: o hangul do coreano e os dois silabários do japonês. */
public final class Alphabet {
	public record Letter(String glyph, String romaji) {
	}

	public record Group(String id, String language, List<Letter> letters) {
	}

	private static final String[] BASIC_ROMAJI = {
			"a", "i", "u", "e", "o", "ka", "ki", "ku", "ke", "ko", "sa", "shi", "su", "se", "so", "ta", "chi", "tsu", "te", "to",
			"na", "ni", "nu", "ne", "no", "ha", "hi", "fu", "he", "ho", "ma", "mi", "mu", "me", "mo", "ya", "yu", "yo",
			"ra", "ri", "ru", "re", "ro", "wa", "wo", "n"};
	private static final String[] MARKED_ROMAJI = {
			"ga", "gi", "gu", "ge", "go", "za", "ji", "zu", "ze", "zo", "da", "ji", "zu", "de", "do",
			"ba", "bi", "bu", "be", "bo", "pa", "pi", "pu", "pe", "po"};
	private static final String[] COMBINED_ROMAJI = {"ky", "sh", "ch", "ny", "hy", "my", "ry", "gy", "j", "by", "py"};
	private static final String[] COMBINED_VOWELS = {"a", "u", "o"};

	public static final List<Group> GROUPS = List.of(
			pairs("ko_consonants", "ㄱ g/k ㄴ n ㄷ d/t ㄹ r/l ㅁ m ㅂ b/p ㅅ s ㅇ ng ㅈ j ㅊ ch ㅋ k ㅌ t ㅍ p ㅎ h"),
			pairs("ko_double", "ㄲ kk ㄸ tt ㅃ pp ㅆ ss ㅉ jj"),
			pairs("ko_vowels", "ㅏ a ㅑ ya ㅓ eo ㅕ yeo ㅗ o ㅛ yo ㅜ u ㅠ yu ㅡ eu ㅣ i"),
			pairs("ko_compound", "ㅐ ae ㅒ yae ㅔ e ㅖ ye ㅘ wa ㅙ wae ㅚ oe ㅝ wo ㅞ we ㅟ wi ㅢ ui"),
			kana("hiragana_basic", "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん", BASIC_ROMAJI),
			kana("hiragana_marked", "がぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽ", MARKED_ROMAJI),
			combined("hiragana_combined", "きしちにひみりぎじびぴ", "ゃゅょ"),
			kana("katakana_basic", "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン", BASIC_ROMAJI),
			kana("katakana_marked", "ガギグゲゴザジズゼゾダヂヅデドバビブベボパピプペポ", MARKED_ROMAJI),
			combined("katakana_combined", "キシチニヒミリギジビピ", "ャュョ"));

	private Alphabet() {
	}

	public static List<Group> groupsOf(String language) {
		return GROUPS.stream().filter(group -> group.language().equals(language)).toList();
	}

	public static Group group(String id) {
		return GROUPS.stream().filter(group -> group.id().equals(id)).findFirst().orElse(GROUPS.getFirst());
	}

	/** A chave da dica de pronúncia. Nem toda letra tem uma. */
	public static String noteKey(Group group, Letter letter) {
		String language = group.language().equals(PlayerProgress.KOREAN) ? "ko" : "ja";
		return "linguacraft.alphabet.note." + language + "." + letter.romaji().replace('/', '_');
	}

	private static Group pairs(String id, String text) {
		String[] parts = text.split(" ");
		List<Letter> letters = new ArrayList<>();
		for (int i = 0; i < parts.length; i += 2) {
			letters.add(new Letter(parts[i], parts[i + 1]));
		}
		return new Group(id, PlayerProgress.KOREAN, List.copyOf(letters));
	}

	private static Group kana(String id, String glyphs, String[] romaji) {
		List<Letter> letters = new ArrayList<>();
		for (int i = 0; i < romaji.length; i++) {
			letters.add(new Letter(String.valueOf(glyphs.charAt(i)), romaji[i]));
		}
		return new Group(id, PlayerProgress.JAPANESE, List.copyOf(letters));
	}

	private static Group combined(String id, String bases, String smallKana) {
		List<Letter> letters = new ArrayList<>();
		for (int base = 0; base < COMBINED_ROMAJI.length; base++) {
			for (int vowel = 0; vowel < COMBINED_VOWELS.length; vowel++) {
				letters.add(new Letter("" + bases.charAt(base) + smallKana.charAt(vowel), COMBINED_ROMAJI[base] + COMBINED_VOWELS[vowel]));
			}
		}
		return new Group(id, PlayerProgress.JAPANESE, List.copyOf(letters));
	}
}
