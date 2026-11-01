package dev.langcraft.client.voice;

import com.google.gson.JsonParseException;
import dev.langcraft.LangCraft;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;
import org.vosk.Model;

/**
 * Transforma o áudio do microfone em respostas. Vários reconhecedores ouvem ao mesmo tempo, e qualquer um
 * pode acertar: o Vosk com fala livre, o Vosk restrito a nomes do jogo e, no coreano, o Whisper.
 *
 * O que o Vosk entende por completo sempre é mostrado ao jogador. Hipóteses menos seguras, como resultados
 * parciais e alternativas, só contam quando acertam, para não piscar um erro a cada ruído.
 */
public final class SpeechPipeline implements AutoCloseable {
	private static final int MAX_ALTERNATIVES = 5;
	private static final int WHISPER_SAMPLE_RATE = 16000;
	/** Duração mínima de um trecho de silêncio para encerrar uma frase. */
	private static final double SILENCE_SECONDS = 0.6;
	private static final double MAX_PHRASE_SECONDS = 5;
	private static final double MIN_PHRASE_SECONDS = 0.2;
	private static final double PREROLL_SECONDS = 0.4;
	private static final double SPEECH_OVER_FLOOR = 1.6;
	private static final double MIN_SPEECH_LEVEL = 0.0015;
	/** Quanto o ruído de fundo estimado sobe por segundo, para acompanhar um ambiente que ficou mais barulhento. */
	private static final double FLOOR_RISE_PER_SECOND = 1.15;

	private final Predicate<String> accepts;
	private final Consumer<String> onHeard;
	private final float sampleRate;
	private final AudioCleaner cleaner = new AudioCleaner();
	private final VoskStream restricted;
	private final VoskStream free;
	private String lastRestrictedPartial = "";
	private String lastFreePartial = "";

	private final @Nullable WhisperEngine whisper;
	private final @Nullable String whisperLanguage;
	private final @Nullable String whisperPrompt;
	private final @Nullable ExecutorService whisperWorker;
	private final AtomicBoolean whisperBusy = new AtomicBoolean();
	private final Deque<byte[]> preroll = new ArrayDeque<>();
	private final ByteArrayOutputStream phrase = new ByteArrayOutputStream();
	private double noiseFloor = -1;
	private boolean inPhrase;
	private double silenceSeconds;
	private double phraseSeconds;

	/** {@code whisper} é opcional. Sem ele, só o Vosk ouve. */
	public SpeechPipeline(Model model, float sampleRate, String grammar, @Nullable WhisperEngine whisper, @Nullable String whisperLanguage,
			@Nullable String whisperPrompt, Predicate<String> accepts, Consumer<String> onHeard) throws IOException {
		this.sampleRate = sampleRate;
		this.accepts = accepts;
		this.onHeard = onHeard;
		this.restricted = new VoskStream(model, sampleRate, grammar);
		this.restricted.setMaxAlternatives(MAX_ALTERNATIVES);
		this.free = new VoskStream(model, sampleRate, null);
		this.whisper = whisper;
		this.whisperLanguage = whisperLanguage;
		this.whisperPrompt = whisperPrompt;
		this.whisperWorker = whisper == null ? null : Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "LangCraft Whisper");
			thread.setDaemon(true);
			return thread;
		});
	}

	public void feed(byte[] audio, int length) {
		if (whisper != null) {
			detectPhrase(audio, length);
		}
		byte[] cleaned = cleaner.process(audio, length);
		if (cleaned.length == 0) {
			return;
		}
		// O restrito vem primeiro: se ele acertar, o erro do livre nem chega a aparecer.
		if (restricted.accept(cleaned, cleaned.length)) {
			reportFinal(finalResult(restricted));
			lastRestrictedPartial = "";
		} else {
			lastRestrictedPartial = checkPartial(restricted, lastRestrictedPartial);
		}
		if (free.accept(cleaned, cleaned.length)) {
			reportFinal(finalResult(free));
			lastFreePartial = "";
		} else {
			lastFreePartial = checkPartial(free, lastFreePartial);
		}
	}

	/** Um resultado ilegível perde só aquela frase. Ele não pode derrubar o microfone. */
	private static List<String> finalResult(VoskStream stream) {
		try {
			return stream.result();
		} catch (JsonParseException | IllegalStateException e) {
			LangCraft.LOGGER.warn("Vosk devolveu um resultado ilegível", e);
			return List.of();
		}
	}

	/** Espera o Whisper terminar. Só é necessário quando o áudio tem fim, como nos testes. */
	public void finish() throws InterruptedException, ExecutionException {
		if (whisperWorker != null) {
			whisperWorker.submit(() -> {
			}).get();
		}
	}

	private void reportFinal(List<String> hypotheses) {
		if (hypotheses.isEmpty()) {
			return;
		}
		for (String hypothesis : hypotheses) {
			if (!hypothesis.isBlank() && accepts.test(hypothesis)) {
				onHeard.accept(hypothesis);
				return;
			}
		}
		if (!hypotheses.getFirst().isBlank()) {
			onHeard.accept(hypotheses.getFirst());
		}
	}

	/** O Vosk só termina uma frase depois de um silêncio, que pode nunca vir com ruído no microfone. */
	private String checkPartial(VoskStream stream, String last) {
		String partial;
		try {
			partial = stream.partial();
		} catch (JsonParseException | IllegalStateException e) {
			LangCraft.LOGGER.warn("Vosk devolveu um resultado parcial ilegível", e);
			return last;
		}
		if (!partial.isBlank() && !partial.equals(last) && accepts.test(partial)) {
			onHeard.accept(partial);
		}
		return partial;
	}

	/** Separa as frases pelo volume, porque o Whisper precisa da frase inteira, e não de áudio contínuo. */
	private void detectPhrase(byte[] audio, int length) {
		double seconds = length / 2.0 / sampleRate;
		double level = rms(audio, length);
		if (noiseFloor < 0) {
			noiseFloor = level;
		}
		noiseFloor = Math.min(noiseFloor * Math.pow(FLOOR_RISE_PER_SECOND, seconds), Math.max(level, 1e-5));
		boolean speech = level > Math.max(MIN_SPEECH_LEVEL, noiseFloor * SPEECH_OVER_FLOOR);
		byte[] chunk = java.util.Arrays.copyOf(audio, length);

		if (!inPhrase) {
			if (speech) {
				inPhrase = true;
				phrase.reset();
				preroll.forEach(phrase::writeBytes);
				preroll.clear();
				phrase.writeBytes(chunk);
				phraseSeconds = seconds;
				silenceSeconds = 0;
			} else {
				preroll.addLast(chunk);
				while (preroll.size() * seconds > PREROLL_SECONDS) {
					preroll.removeFirst();
				}
			}
			return;
		}
		phrase.writeBytes(chunk);
		phraseSeconds += seconds;
		silenceSeconds = speech ? 0 : silenceSeconds + seconds;
		if (silenceSeconds >= SILENCE_SECONDS || phraseSeconds >= MAX_PHRASE_SECONDS) {
			inPhrase = false;
			if (phraseSeconds - silenceSeconds >= MIN_PHRASE_SECONDS) {
				transcribeLater(phrase.toByteArray());
			}
		}
	}

	/** Se o Whisper ainda estiver ocupado, a frase é descartada: o Vosk continua ouvindo, e o jogo não engasga. */
	private void transcribeLater(byte[] pcm) {
		if (!whisperBusy.compareAndSet(false, true)) {
			return;
		}
		whisperWorker.execute(() -> {
			try {
				String text = whisper.transcribe(toWhisperSamples(pcm), whisperLanguage, whisperPrompt);
				if (!text.isBlank() && accepts.test(text)) {
					onHeard.accept(text);
				}
			} finally {
				whisperBusy.set(false);
			}
		});
	}

	private float[] toWhisperSamples(byte[] pcm) {
		int samples = pcm.length / 2;
		double step = sampleRate / WHISPER_SAMPLE_RATE;
		int outputLength = (int) (samples / step);
		float[] output = new float[outputLength];
		for (int i = 0; i < outputLength; i++) {
			int index = (int) (i * step);
			output[i] = (short) ((pcm[index * 2] & 0xFF) | (pcm[index * 2 + 1] << 8)) / (float) Short.MAX_VALUE;
		}
		return output;
	}

	private static double rms(byte[] audio, int length) {
		int samples = length / 2;
		double sum = 0;
		for (int i = 0; i < samples; i++) {
			double sample = (short) ((audio[i * 2] & 0xFF) | (audio[i * 2 + 1] << 8)) / (double) Short.MAX_VALUE;
			sum += sample * sample;
		}
		return samples == 0 ? 0 : Math.sqrt(sum / samples);
	}

	@Override
	public void close() {
		if (whisperWorker != null) {
			whisperWorker.shutdown();
		}
		restricted.close();
		free.close();
	}
}
