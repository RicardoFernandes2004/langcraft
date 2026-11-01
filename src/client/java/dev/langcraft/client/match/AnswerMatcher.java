package dev.langcraft.client.match;

import dev.langcraft.data.PlayerProgress;
import dev.langcraft.data.StudyLanguage;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Compara a resposta do jogador com o nome do item. */
public final class AnswerMatcher {
	private static final int MAX_EXTRA_SYLLABLES_DIVISOR = 4;
	private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

	private AnswerMatcher() {
	}

	/** Voz: aceita pequenas diferenças, porque o reconhecimento raramente é perfeito. */
	public static boolean matchesSpoken(String expected, String heard, String language, double minSimilarity) {
		if (language.equals(PlayerProgress.KOREAN) && !similarSyllableCount(expected, heard)) {
			return false;
		}
		String a = comparable(expected, language);
		String b = comparable(heard, language);
		return !b.isEmpty() && similarity(a, b) >= minSimilarity;
	}

	/**
	 * Só a palavra vale: 광부 사과 não acerta 사과. A regra conta sílabas, e não palavras, porque o
	 * reconhecimento separa uma palavra só em pedaços, como 종 이, e um nome pode ter espaço, como 참나무 원목.
	 */
	private static boolean similarSyllableCount(String expected, String heard) {
		int expectedLength = clean(expected).length();
		return Math.abs(expectedLength - clean(heard).length()) <= expectedLength / MAX_EXTRA_SYLLABLES_DIVISOR;
	}

	/** Digitação: exige a escrita certa. Em japonês, a escrita em kana também vale. */
	public static boolean matchesTyped(String expected, String typed, String language) {
		String a = comparable(expected, language);
		String b = comparable(typed, language);
		return !b.isEmpty() && a.equals(b);
	}

	static String comparable(String text, String language) {
		String clean = clean(text);
		if (language.equals(PlayerProgress.JAPANESE) && !clean.isEmpty()) {
			return clean(KanaNormalizer.toKatakana(clean));
		}
		if (language.equals(PlayerProgress.KOREAN)) {
			// Compara letra a letra (ㄱ, ㅓ, ㅁ), para que 검 e 겁 fiquem mais perto do que 검 e 돌.
			return Normalizer.normalize(clean, Normalizer.Form.NFD);
		}
		if (StudyLanguage.isLatin(language)) {
			return withoutAccents(clean, language);
		}
		return clean;
	}

	/** Nem todo teclado tem ä, ß ou ñ. Em alemão, a escrita sem o sinal é ae, e nos outros é só a letra sem acento. */
	private static String withoutAccents(String text, String language) {
		String plain = language.equals(PlayerProgress.GERMAN)
				? text.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
				: text;
		return COMBINING_MARKS.matcher(Normalizer.normalize(plain, Normalizer.Form.NFD)).replaceAll("");
	}

	/** Unifica formas equivalentes e remove espaços e pontuação. */
	static String clean(String text) {
		String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		StringBuilder result = new StringBuilder(normalized.length());
		normalized.codePoints()
				.filter(c -> Character.isLetterOrDigit(c) || c == 'ー')
				.forEach(result::appendCodePoint);
		return result.toString();
	}

	/** 1 menos a distância de Levenshtein dividida pelo tamanho do maior texto. */
	static double similarity(String a, String b) {
		if (a.isEmpty() && b.isEmpty()) {
			return 1.0;
		}
		return 1.0 - (double) levenshtein(a, b) / Math.max(a.length(), b.length());
	}

	private static int levenshtein(String a, String b) {
		int[] previous = new int[b.length() + 1];
		int[] current = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			previous[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			current[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
			}
			int[] swap = previous;
			previous = current;
			current = swap;
		}
		return previous[b.length()];
	}
}
