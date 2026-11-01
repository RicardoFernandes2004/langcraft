package dev.langcraft.client.lang;

import dev.langcraft.client.match.KanaNormalizer;
import dev.langcraft.data.PlayerProgress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/** Separa um texto japonês em trechos, com a leitura em hiragana só sobre os kanjis. */
public final class Furigana {
	/** {@code reading} é nulo em trechos sem kanji. */
	public record Segment(String text, @Nullable String reading) {
	}

	private static final Pattern KANJI_RUNS = Pattern.compile("[\\p{IsHan}々〆ヶ]+");

	private Furigana() {
	}

	public static List<Segment> segments(String text, String language) {
		if (!language.equals(PlayerProgress.JAPANESE) || !KANJI_RUNS.matcher(text).find()) {
			return List.of(new Segment(text, null));
		}
		List<Segment> segments = new ArrayList<>();
		for (KanaNormalizer.Reading part : KanaNormalizer.read(text)) {
			appendToken(segments, part.surface(), part.reading());
		}
		return segments;
	}

	/** Forma para chat e títulos, onde não dá para sobrepor texto: 石(いし)の剣(けん). */
	public static String inline(String text, String language) {
		StringBuilder result = new StringBuilder();
		for (Segment segment : segments(text, language)) {
			result.append(segment.text());
			if (segment.reading() != null) {
				result.append('(').append(segment.reading()).append(')');
			}
		}
		return result.toString();
	}

	public static boolean hasReadings(List<Segment> segments) {
		return segments.stream().anyMatch(segment -> segment.reading() != null);
	}

	private static void appendToken(List<Segment> segments, String surface, @Nullable String reading) {
		if (!KANJI_RUNS.matcher(surface).find() || reading == null) {
			appendPlain(segments, surface);
			return;
		}
		String hiragana = KanaNormalizer.katakanaToHiragana(reading);
		// Monta um padrão em que cada grupo de kanji captura a própria leitura e o kana ao redor precisa bater.
		// Assim, 食べる vira 食(た) + べる, e não 食べる(たべる).
		StringBuilder pattern = new StringBuilder("^");
		List<String> parts = new ArrayList<>();
		Matcher kanji = KANJI_RUNS.matcher(surface);
		int position = 0;
		while (kanji.find()) {
			if (kanji.start() > position) {
				String kana = surface.substring(position, kanji.start());
				pattern.append(Pattern.quote(KanaNormalizer.katakanaToHiragana(kana)));
				parts.add(kana);
			}
			pattern.append("(.+?)");
			parts.add(kanji.group());
			position = kanji.end();
		}
		if (position < surface.length()) {
			String kana = surface.substring(position);
			pattern.append(Pattern.quote(KanaNormalizer.katakanaToHiragana(kana)));
			parts.add(kana);
		}
		Matcher aligned = Pattern.compile(pattern.append('$').toString()).matcher(hiragana);
		if (!aligned.matches()) {
			segments.add(new Segment(surface, hiragana));
			return;
		}
		int group = 1;
		for (String part : parts) {
			if (KANJI_RUNS.matcher(part).matches()) {
				segments.add(new Segment(part, aligned.group(group++)));
			} else {
				appendPlain(segments, part);
			}
		}
	}

	private static void appendPlain(List<Segment> segments, String text) {
		if (!segments.isEmpty() && segments.getLast().reading() == null) {
			segments.set(segments.size() - 1, new Segment(segments.getLast().text() + text, null));
		} else {
			segments.add(new Segment(text, null));
		}
	}
}
