package dev.linguacraft.client.match;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Descobre a leitura de textos japoneses, para a furigana e para que homófonos como 剣 e 県 sejam iguais. */
public final class KanaNormalizer {
	/** {@code reading} é nulo quando a leitura é desconhecida. */
	public record Reading(String surface, @Nullable String reading) {
	}

	private static final String UNKNOWN_READING = "*";

	/**
	 * Correções para nomes do Minecraft que o dicionário lê errado. As chaves de um caractere só valem
	 * quando ele aparece sozinho: 金 é corrigido, mas 金網 continua com a própria leitura.
	 */
	private static final Map<String, String> OVERRIDES = Map.ofEntries(
			Map.entry("黄緑色", "きみどりいろ"),
			Map.entry("青緑色", "あおみどりいろ"),
			Map.entry("緑色", "みどりいろ"),
			Map.entry("白色", "しろいろ"),
			Map.entry("黒色", "くろいろ"),
			Map.entry("桜色", "さくらいろ"),
			Map.entry("黄土色", "おうどいろ"),
			Map.entry("新緑色", "しんりょくいろ"),
			Map.entry("砂岩", "さがん"),
			Map.entry("閃緑岩", "せんりょくがん"),
			Map.entry("感圧板", "かんあつばん"),
			Map.entry("彩釉", "さいゆう"),
			Map.entry("鍛冶", "かじ"),
			Map.entry("矢細工", "やざいく"),
			Map.entry("粉雪", "こなゆき"),
			Map.entry("宝物", "たからもの"),
			Map.entry("宝物庫", "ほうもつこ"),
			Map.entry("金床", "かなとこ"),
			Map.entry("石化", "せきか"),
			Map.entry("縁取り", "ふちどり"),
			Map.entry("釣り人", "つりびと"),
			Map.entry("歪ん", "ゆがん"),
			Map.entry("金", "きん"),
			Map.entry("土", "つち"),
			Map.entry("的", "まと"),
			Map.entry("色", "いろ"),
			Map.entry("波", "なみ"));

	private static final List<String> PHRASES = OVERRIDES.keySet().stream()
			.filter(key -> key.length() > 1)
			.sorted(Comparator.comparingInt(String::length).reversed())
			.toList();

	private static volatile Tokenizer tokenizer;

	private KanaNormalizer() {
	}

	/** Carregar o dicionário leva cerca de 1 segundo, então convém chamar isto fora da thread do jogo. */
	public static void preload() {
		tokenizer();
	}

	public static String toKatakana(String text) {
		StringBuilder result = new StringBuilder();
		for (Reading part : read(text)) {
			result.append(part.reading() == null ? part.surface() : part.reading());
		}
		return hiraganaToKatakana(result.toString());
	}

	/** Separa o texto em palavras com suas leituras, aplicando as correções. */
	public static List<Reading> read(String text) {
		List<Reading> parts = new ArrayList<>();
		int plainStart = 0;
		int position = 0;
		while (position < text.length()) {
			String phrase = phraseAt(text, position);
			if (phrase == null) {
				position++;
				continue;
			}
			appendTokens(parts, text.substring(plainStart, position));
			parts.add(new Reading(phrase, OVERRIDES.get(phrase)));
			position += phrase.length();
			plainStart = position;
		}
		appendTokens(parts, text.substring(plainStart));
		return parts;
	}

	private static @Nullable String phraseAt(String text, int position) {
		for (String phrase : PHRASES) {
			if (text.startsWith(phrase, position)) {
				return phrase;
			}
		}
		return null;
	}

	private static void appendTokens(List<Reading> parts, String text) {
		if (text.isEmpty()) {
			return;
		}
		for (Token token : tokenizer().tokenize(text)) {
			String surface = token.getSurface();
			String reading = OVERRIDES.getOrDefault(surface, token.getReading());
			parts.add(new Reading(surface, reading == null || reading.equals(UNKNOWN_READING) ? null : reading));
		}
	}

	private static Tokenizer tokenizer() {
		if (tokenizer == null) {
			synchronized (KanaNormalizer.class) {
				if (tokenizer == null) {
					tokenizer = new Tokenizer();
				}
			}
		}
		return tokenizer;
	}

	public static String katakanaToHiragana(String text) {
		StringBuilder result = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			result.append(c >= 'ァ' && c <= 'ヶ' ? (char) (c - 0x60) : c);
		}
		return result.toString();
	}

	private static String hiraganaToKatakana(String text) {
		StringBuilder result = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			result.append(c >= 'ぁ' && c <= 'ゖ' ? (char) (c + 0x60) : c);
		}
		return result.toString();
	}
}
