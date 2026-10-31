package dev.linguacraft;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.linguacraft.client.match.AnswerMatcher;
import dev.linguacraft.client.voice.SpeechPipeline;
import dev.linguacraft.client.voice.VoskGrammar;
import dev.linguacraft.client.voice.WhisperEngine;
import java.io.BufferedInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.vosk.Model;

/**
 * Mede o mesmo código de reconhecimento do jogo: o áudio chega em pedaços, com ruído e volume variados, e
 * termina em ruído em vez de um fim de arquivo. Não falha: só grava o relatório em build/voice-bench.txt.
 * É ignorado quando as amostras ou os modelos não existem.
 */
class VoiceBenchmarkTest {
	private static final Path ROOT = Path.of("build/run/clientGameTest");
	private static final Path MODELS = ROOT.resolve("config/linguacraft/models");
	private static final Path WHISPER_MODEL = ROOT.resolve("whisper/ggml-tiny.bin");
	private static final double LIMIT = 0.75;
	private static final int SAMPLE_RATE = 16000;
	private static final int CHUNK_BYTES = 4096;
	private static final double LEAD_SECONDS = 0.5;
	private static final double TAIL_SECONDS = 2.0;

	private record Condition(String name, double gain, double noise) {
	}

	private static final List<Condition> CONDITIONS = List.of(
			new Condition("limpo", 1.0, 0.0),
			new Condition("ruido leve", 1.0, 0.01),
			new Condition("ruido forte", 1.0, 0.04),
			new Condition("volume baixo", 0.15, 0.003));

	@Test
	void measurePipeline() throws Exception {
		assumeTrue(Files.exists(ROOT.resolve("names-ko_kr.txt")));
		assumeTrue(Files.exists(WHISPER_MODEL));
		assumeTrue(Files.isDirectory(MODELS.resolve("vosk-model-small-ko-0.22")));
		assumeTrue(Files.isDirectory(MODELS.resolve("vosk-model-small-ja-0.22")));
		List<String> report = new ArrayList<>();
		try (WhisperEngine whisper = WhisperEngine.load(WHISPER_MODEL)) {
			benchmark("voice-human", "ko_kr", "vosk-model-small-ko-0.22", whisper, "ko", report);
			benchmark("voice-human", "ja_jp", "vosk-model-small-ja-0.22", null, null, report);
		}
		Files.write(Path.of("build/voice-bench.txt"), report, StandardCharsets.UTF_8);
	}

	private static void benchmark(String dataset, String language, String modelName, WhisperEngine whisper, String whisperCode, List<String> report) throws Exception {
		Path dir = ROOT.resolve(dataset).resolve(language);
		if (!Files.exists(dir.resolve("expected.txt"))) {
			return;
		}
		List<String> words = Files.readAllLines(dir.resolve("expected.txt"), StandardCharsets.UTF_8).stream().filter(word -> !word.isBlank()).toList();
		List<String> names = Files.readAllLines(ROOT.resolve("names-" + language + ".txt"), StandardCharsets.UTF_8).stream().filter(name -> !name.isBlank()).toList();
		try (Model model = new Model(MODELS.resolve(modelName).toString())) {
			for (Condition condition : CONDITIONS) {
				int right = 0;
				int wrong = 0;
				for (int i = 0; i < words.size(); i++) {
					byte[] audio = prepare(dir.resolve(i + ".wav"), condition, i);
					String wrongTarget = words.get((i + 7) % words.size());
					right += accepted(model, audio, words.get(i), names, language, whisper, whisperCode, i) ? 1 : 0;
					wrong += accepted(model, audio, wrongTarget, names, language, whisper, whisperCode, i) ? 1 : 0;
				}
				report.add(String.format("%s %s, %s (%d): %d aceitas, %d falsos positivos", dataset, language, condition.name(), words.size(), right, wrong));
			}
		}
	}

	private static boolean accepted(Model model, byte[] audio, String target, List<String> names, String language, WhisperEngine whisper, String whisperCode, int seed) throws Exception {
		AtomicBoolean accepted = new AtomicBoolean();
		Predicate<String> accepts = text -> AnswerMatcher.matchesSpoken(target, text, language, LIMIT);
		try (SpeechPipeline pipeline = new SpeechPipeline(model, SAMPLE_RATE, VoskGrammar.build(target, names, language, new Random(seed)), whisper, whisperCode,
				VoskGrammar.whisperPrompt(target, names, new Random(seed)), accepts, text -> {
					if (accepts.test(text)) {
						accepted.set(true);
					}
				})) {
			byte[] chunk = new byte[CHUNK_BYTES];
			for (int position = 0; position + CHUNK_BYTES <= audio.length; position += CHUNK_BYTES) {
				System.arraycopy(audio, position, chunk, 0, CHUNK_BYTES);
				pipeline.feed(chunk, CHUNK_BYTES);
			}
			pipeline.finish();
		}
		return accepted.get();
	}

	/** Aplica o ganho e o ruído, com ruído também antes e depois da fala, como em um microfone aberto. */
	private static byte[] prepare(Path wav, Condition condition, int seed) throws Exception {
		byte[] speech;
		try (AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(wav)))) {
			speech = audio.readAllBytes();
		}
		int lead = (int) (LEAD_SECONDS * SAMPLE_RATE);
		int tail = (int) (TAIL_SECONDS * SAMPLE_RATE);
		int samples = speech.length / 2;
		byte[] result = new byte[(lead + samples + tail) * 2];
		Random random = new Random(seed);
		for (int i = 0; i < lead + samples + tail; i++) {
			double value = 0;
			if (i >= lead && i < lead + samples) {
				int index = (i - lead) * 2;
				value = (short) ((speech[index] & 0xFF) | (speech[index + 1] << 8)) * condition.gain();
			}
			value += random.nextGaussian() * condition.noise() * Short.MAX_VALUE;
			int sample = (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
			result[i * 2] = (byte) sample;
			result[i * 2 + 1] = (byte) (sample >> 8);
		}
		return result;
	}
}
