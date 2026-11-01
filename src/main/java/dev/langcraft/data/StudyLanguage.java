package dev.langcraft.data;

import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Os idiomas que dá para praticar, e tudo o que muda de um para outro. Para adicionar um idioma, comece por aqui. */
public enum StudyLanguage {
	KOREAN("ko_kr", "korean", "vosk-model-small-ko-0.22", "160 MB", "ko", false),
	JAPANESE("ja_jp", "japanese", "vosk-model-small-ja-0.22", "48 MB", null, false),
	ENGLISH("en_us", "english", "vosk-model-small-en-us-0.15", "41 MB", null, true),
	GERMAN("de_de", "german", "vosk-model-small-de-0.15", "47 MB", null, true),
	SPANISH("es_es", "spanish", "vosk-model-small-es-0.42", "40 MB", null, true);

	private final String code;
	private final String commandName;
	private final String voskModel;
	private final String downloadSize;
	private final @Nullable String whisperLanguage;
	private final boolean latin;

	StudyLanguage(String code, String commandName, String voskModel, String downloadSize, @Nullable String whisperLanguage, boolean latin) {
		this.code = code;
		this.commandName = commandName;
		this.voskModel = voskModel;
		this.downloadSize = downloadSize;
		this.whisperLanguage = whisperLanguage;
		this.latin = latin;
	}

	public static @Nullable StudyLanguage byCode(String code) {
		for (StudyLanguage language : values()) {
			if (language.code.equals(code)) {
				return language;
			}
		}
		return null;
	}

	public static List<String> codes() {
		return Arrays.stream(values()).map(StudyLanguage::code).toList();
	}

	public static boolean isLatin(String code) {
		StudyLanguage language = byCode(code);
		return language != null && language.latin;
	}

	/** O código usado pelos arquivos de idioma do jogo. */
	public String code() {
		return code;
	}

	public String commandName() {
		return commandName;
	}

	public String voskModel() {
		return voskModel;
	}

	public String downloadSize() {
		return downloadSize;
	}

	/** O idioma para o Whisper, ou nulo se só o Vosk ouve. Soma-se o Whisper quando as medições mostram ganho. */
	public @Nullable String whisperLanguage() {
		return whisperLanguage;
	}
}
