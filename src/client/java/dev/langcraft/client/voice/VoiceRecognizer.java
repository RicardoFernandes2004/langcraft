package dev.langcraft.client.voice;

import dev.langcraft.LangCraft;
import dev.langcraft.data.StudyLanguage;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.sound.sampled.LineUnavailableException;
import org.vosk.Model;

/**
 * Ouve o microfone durante um desafio de fala. Roda em uma thread própria e nunca segura o jogo.
 *
 * O reconhecimento em si fica em {@link SpeechPipeline}.
 */
public final class VoiceRecognizer {
	public enum Status {
		PREPARING,
		DOWNLOADING,
		LISTENING,
		NO_MIC,
		FAILED
	}

	private static final float[] SAMPLE_RATES = {16000f, 48000f, 44100f};
	private static final int OPEN_ATTEMPTS = 10;
	private static final int OPEN_RETRY_MILLIS = 300;

	private final String language;
	private final Supplier<String> grammar;
	private final Supplier<String> whisperPrompt;
	private final Predicate<String> accepts;
	private final Consumer<String> onHeard;
	private volatile boolean stopped;
	private volatile Status status = Status.PREPARING;
	private volatile int downloadPercent;
	private volatile float level;
	private volatile boolean usingFallback;

	/** Os parâmetros são chamados nas threads de reconhecimento. */
	public VoiceRecognizer(String language, Supplier<String> grammar, Supplier<String> whisperPrompt, Predicate<String> accepts, Consumer<String> onHeard) {
		this.language = language;
		this.grammar = grammar;
		this.whisperPrompt = whisperPrompt;
		this.accepts = accepts;
		this.onHeard = onHeard;
	}

	public void start() {
		Thread thread = new Thread(this::run, "LangCraft Voice");
		thread.setDaemon(true);
		thread.start();
	}

	public void stop() {
		stopped = true;
	}

	public Status status() {
		return status;
	}

	public int downloadPercent() {
		return downloadPercent;
	}

	/** Verdadeiro se o microfone escolhido não abriu e o padrão do sistema está sendo usado. */
	public boolean usingFallback() {
		return usingFallback;
	}

	/** Volume atual do microfone, de 0 a 1. */
	public float level() {
		return level;
	}

	private void run() {
		try {
			Model model = awaitModel();
			if (model != null && !stopped) {
				listen(model);
			}
		} catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
			if (!stopped) {
				LangCraft.LOGGER.warn("Microfone indisponível", e);
				status = Status.NO_MIC;
			}
		} catch (IOException | ExecutionException | RuntimeException | LinkageError e) {
			// Qualquer falha aparece na tela. Sem isto, a thread morreria em silêncio com o alerta dizendo "Ouvindo...".
			LangCraft.LOGGER.error("Falha no reconhecimento de voz", e);
			status = Status.FAILED;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private Model awaitModel() throws ExecutionException, InterruptedException {
		Future<Model> future = ModelManager.model(language);
		while (!stopped) {
			if (ModelManager.phase(language) == ModelManager.Phase.DOWNLOADING) {
				status = Status.DOWNLOADING;
				downloadPercent = ModelManager.downloadPercent(language);
			}
			try {
				return future.get(200, TimeUnit.MILLISECONDS);
			} catch (TimeoutException e) {
				// Continua esperando até o modelo ficar pronto ou o desafio acabar.
			}
		}
		return null;
	}

	/**
	 * O sistema pode recusar o microfone logo depois de outro desafio fechá-lo, e alguns dispositivos não
	 * capturam a 16 kHz. Por isso, tenta outras taxas e insiste por alguns segundos. O Vosk converte a taxa.
	 */
	private Microphones.Input openMicrophone(String id) throws LineUnavailableException, InterruptedException {
		Exception lastError = null;
		for (int attempt = 0; attempt < OPEN_ATTEMPTS && !stopped; attempt++) {
			for (float sampleRate : SAMPLE_RATES) {
				try {
					return Microphones.open(id, sampleRate);
				} catch (LineUnavailableException | IllegalArgumentException e) {
					lastError = e;
				}
			}
			Thread.sleep(OPEN_RETRY_MILLIS);
		}
		throw new LineUnavailableException(String.valueOf(lastError));
	}

	/** Se o microfone escolhido não abrir, por exemplo porque foi desconectado, usa o padrão do sistema. */
	private Microphones.Input openChosenOrDefault() throws LineUnavailableException, InterruptedException {
		String chosen = LangCraft.config.microphone;
		try {
			return openMicrophone(chosen);
		} catch (LineUnavailableException e) {
			if (chosen.equals(Microphones.SYSTEM_DEFAULT) || stopped) {
				throw e;
			}
			LangCraft.LOGGER.warn("O microfone {} não abriu. Usando o padrão do sistema.", chosen, e);
			Microphones.Input input = openMicrophone(Microphones.SYSTEM_DEFAULT);
			usingFallback = true;
			return input;
		}
	}

	private void listen(Model model) throws IOException, LineUnavailableException, InterruptedException {
		StudyLanguage study = StudyLanguage.byCode(language);
		String whisperCode = study == null ? null : study.whisperLanguage();
		try (Microphones.Input input = openChosenOrDefault();
				SpeechPipeline pipeline = new SpeechPipeline(model, input.sampleRate(), grammar.get(), whisperCode == null ? null : ModelManager.whisper(), whisperCode,
						whisperPrompt.get(), accepts, this::report)) {
			status = Status.LISTENING;
			byte[] buffer = new byte[4096];
			while (!stopped) {
				int read = input.read(buffer);
				if (read < 0) {
					if (stopped) {
						return;
					}
					throw new LineUnavailableException("A captura do microfone terminou");
				}
				if (read > 0) {
					level = Microphones.level(buffer, read);
					pipeline.feed(buffer, read);
				}
			}
		}
	}

	private void report(String text) {
		if (!text.isBlank() && !stopped) {
			onHeard.accept(text);
		}
	}
}
