package dev.linguacraft.client.voice;

import com.google.gson.Gson;
import dev.linguacraft.client.match.KanaNormalizer;
import dev.linguacraft.data.PlayerProgress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Monta o vocabulário restrito de um desafio: a resposta, outros nomes do jogo e [unk] para o resto.
 * Os outros nomes dão ao reconhecedor alternativas para o que não for a resposta, o que evita aceitar qualquer fala.
 */
public final class VoskGrammar {
	private static final String UNKNOWN = "[unk]";
	private static final int OTHER_NAMES = 30;
	private static final int WHISPER_PROMPT_NAMES = 8;

	private VoskGrammar() {
	}

	public static String build(String target, Collection<String> names, String language, Random random) {
		List<String> others = new ArrayList<>(names);
		others.remove(target);
		Collections.shuffle(others, random);
		List<String> phrases = new ArrayList<>();
		phrases.add(phrase(target, language));
		for (String name : others.subList(0, Math.min(OTHER_NAMES, others.size()))) {
			phrases.add(phrase(name, language));
		}
		// O modelo coreano não tem a palavra [unk], e o Vosk só registraria um aviso.
		if (!language.equals(PlayerProgress.KOREAN)) {
			phrases.add(UNKNOWN);
		}
		return new Gson().toJson(phrases);
	}

	/** Dica para o Whisper: a resposta, misturada com outros nomes para não a entregar de graça. */
	public static String whisperPrompt(String target, Collection<String> names, Random random) {
		List<String> words = new ArrayList<>(names);
		words.remove(target);
		Collections.shuffle(words, random);
		words = new ArrayList<>(words.subList(0, Math.min(WHISPER_PROMPT_NAMES, words.size())));
		words.add(target);
		Collections.shuffle(words, random);
		return String.join(", ", words);
	}

	/** Tira a marca de "palavra desconhecida" do texto reconhecido. */
	public static String clean(String heard) {
		return heard.replace(UNKNOWN, " ").strip().replaceAll("\\s+", " ");
	}

	/** O modelo japonês conhece palavras, não nomes inteiros: ダイヤモンドの剣 vira ダイヤモンド の 剣. */
	private static String phrase(String name, String language) {
		if (!language.equals(PlayerProgress.JAPANESE)) {
			return name;
		}
		return KanaNormalizer.read(name).stream().map(KanaNormalizer.Reading::surface).collect(Collectors.joining(" "));
	}
}
