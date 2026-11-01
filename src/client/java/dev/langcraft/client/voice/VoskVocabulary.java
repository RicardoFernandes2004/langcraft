package dev.langcraft.client.voice;

import dev.langcraft.client.match.KanaNormalizer;
import dev.langcraft.data.PlayerProgress;
import dev.langcraft.data.StudyLanguage;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.vosk.Model;

/**
 * Confere se o modelo de voz conhece as palavras de um nome. O modelo só entende o que está no vocabulário dele,
 * e um nome inventado por um mod, ou um nome composto longo do alemão, nunca seria reconhecido.
 */
public final class VoskVocabulary {
	private VoskVocabulary() {
	}

	/**
	 * Só responde não quando tem certeza. Com o Whisper, que não depende de vocabulário, ou com o modelo
	 * ainda carregando, o desafio de fala segue normalmente.
	 */
	public static boolean canSpeak(String name, String language) {
		StudyLanguage study = StudyLanguage.byCode(language);
		if (study == null || study.whisperLanguage() != null) {
			return true;
		}
		Model model = ModelManager.model(language).getNow(null);
		if (model == null) {
			return true;
		}
		return words(name, language).stream().allMatch(word -> VoskStream.knowsWord(model, word));
	}

	/** Divide o nome como {@link VoskGrammar} faz na lista enviada ao modelo. */
	static List<String> words(String name, String language) {
		if (language.equals(PlayerProgress.JAPANESE)) {
			return KanaNormalizer.read(name).stream().map(KanaNormalizer.Reading::surface).filter(word -> !word.isBlank()).toList();
		}
		String text = StudyLanguage.isLatin(language) ? name.toLowerCase(Locale.ROOT) : name;
		return Arrays.stream(text.split("\\s+")).filter(word -> !word.isBlank()).toList();
	}
}
