package dev.linguacraft.client.voice;

import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperFullParams;
import io.github.givimad.whisperjni.WhisperJNI;
import java.io.IOException;
import java.nio.file.Path;

/** O Whisper, usado no coreano porque resiste muito melhor a ruído e a volume baixo do que o Vosk. */
public final class WhisperEngine implements AutoCloseable {
	private static final int MAX_THREADS = 4;

	private final WhisperJNI whisper;
	private final WhisperContext context;

	private WhisperEngine(WhisperJNI whisper, WhisperContext context) {
		this.whisper = whisper;
		this.context = context;
	}

	public static WhisperEngine load(Path model) throws IOException {
		WhisperJNI.loadLibrary();
		WhisperJNI.setLibraryLogger(message -> {
		});
		WhisperJNI whisper = new WhisperJNI();
		return new WhisperEngine(whisper, whisper.init(model));
	}

	/** Transcreve áudio a 16 kHz. A dica aproxima o resultado das palavras esperadas. */
	public synchronized String transcribe(float[] audio, String languageCode, String prompt) {
		WhisperFullParams params = new WhisperFullParams();
		params.language = languageCode;
		params.nThreads = Math.max(2, Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors() / 2));
		params.noContext = true;
		params.initialPrompt = prompt;
		if (whisper.full(context, params, audio, audio.length) != 0) {
			return "";
		}
		StringBuilder text = new StringBuilder();
		int segments = whisper.fullNSegments(context);
		for (int i = 0; i < segments; i++) {
			text.append(whisper.fullGetSegmentText(context, i));
		}
		return text.toString().strip();
	}

	@Override
	public void close() {
		context.close();
	}
}
