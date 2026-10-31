package dev.linguacraft.client.voice;

import dev.linguacraft.LinguaCraft;
import dev.linguacraft.data.PlayerProgress;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;
import org.vosk.LibVosk;
import org.vosk.Model;

/** Baixa os modelos do Vosk, um de cada vez, e os mantém carregados. */
public final class ModelManager {
	public enum Phase {
		QUEUED,
		DOWNLOADING,
		EXTRACTING,
		LOADING,
		READY,
		FAILED
	}

	private static final int QUIET_LOG_LEVEL = -2;
	private static final int CONNECT_TIMEOUT_MILLIS = 30_000;
	/** Se o servidor parar de enviar dados por este tempo, o download falha em vez de ficar parado. */
	private static final int READ_TIMEOUT_MILLIS = 30_000;
	private static final String WHISPER_FILE = "ggml-tiny.bin";
	/** O modelo do Whisper é cerca de metade do que o coreano precisa baixar. */
	private static final int WHISPER_SHARE_PERCENT = 50;
	private static final Map<String, String> MODEL_NAMES = Map.of(
			PlayerProgress.KOREAN, "vosk-model-small-ko-0.22",
			PlayerProgress.JAPANESE, "vosk-model-small-ja-0.22");

	private static final Map<String, CompletableFuture<Model>> MODELS = new ConcurrentHashMap<>();
	private static final Map<String, Phase> PHASES = new ConcurrentHashMap<>();
	private static final Map<String, Integer> PERCENTS = new ConcurrentHashMap<>();
	private static final Map<String, String> FAILURES = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> FAILED_AFTER_DOWNLOAD = new ConcurrentHashMap<>();
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "LinguaCraft Model Loader");
		thread.setDaemon(true);
		return thread;
	});

	private static volatile @Nullable WhisperEngine whisper;

	private ModelManager() {
	}

	/** O Whisper já carregado, ou nulo se ele não estiver disponível, e aí o Vosk ouve sozinho. */
	public static @Nullable WhisperEngine whisper() {
		return whisper;
	}

	/** Baixa, se preciso, e carrega o modelo. Depois de uma falha, a próxima chamada tenta de novo. */
	public static CompletableFuture<Model> model(String language) {
		return MODELS.compute(language, (key, current) -> {
			if (current != null && !current.isCompletedExceptionally()) {
				return current;
			}
			PHASES.put(language, Phase.QUEUED);
			PERCENTS.put(language, 0);
			FAILURES.remove(language);
			CompletableFuture<Model> future = CompletableFuture.supplyAsync(() -> load(language), EXECUTOR);
			future.whenComplete((model, error) -> {
				if (error != null) {
					LinguaCraft.LOGGER.error("Falha ao preparar o modelo de voz de {}", language, error);
					FAILED_AFTER_DOWNLOAD.put(language, phase(language) == Phase.LOADING);
					FAILURES.put(language, describe(error));
				}
				PHASES.put(language, error == null ? Phase.READY : Phase.FAILED);
			});
			return future;
		});
	}

	public static boolean isDownloaded(String language) {
		return Files.isDirectory(modelsDir().resolve(MODEL_NAMES.get(language)))
				&& (!usesWhisper(language) || Files.isRegularFile(modelsDir().resolve(WHISPER_FILE)));
	}

	public static Phase phase(String language) {
		return PHASES.getOrDefault(language, Phase.QUEUED);
	}

	public static int downloadPercent(String language) {
		return PERCENTS.getOrDefault(language, 0);
	}

	/** Motivo da última falha, para mostrar na tela. Vazio se não houve falha. */
	public static String failureReason(String language) {
		return FAILURES.getOrDefault(language, "");
	}

	/** Verdadeiro se o download deu certo, mas o modelo não carregou neste sistema. */
	public static boolean failedAfterDownload(String language) {
		return FAILED_AFTER_DOWNLOAD.getOrDefault(language, false);
	}

	private static String describe(Throwable error) {
		Throwable root = error;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		return root.getMessage() == null ? root.getClass().getSimpleName() : root.getClass().getSimpleName() + ": " + root.getMessage();
	}

	private static boolean usesWhisper(String language) {
		return language.equals(PlayerProgress.KOREAN);
	}

	private static Path modelsDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("linguacraft").resolve("models");
	}

	private static Model load(String language) {
		try {
			Path modelDir = modelsDir().resolve(MODEL_NAMES.get(language));
			boolean needsVosk = !Files.isDirectory(modelDir);
			boolean needsWhisper = usesWhisper(language) && !Files.isRegularFile(modelsDir().resolve(WHISPER_FILE));
			if (needsWhisper) {
				PHASES.put(language, Phase.DOWNLOADING);
				downloadWhisper(language, needsVosk);
			}
			if (needsVosk) {
				PHASES.put(language, Phase.DOWNLOADING);
				download(language, MODEL_NAMES.get(language), modelsDir(), modelDir, needsWhisper ? WHISPER_SHARE_PERCENT : 0);
			}
			PHASES.put(language, Phase.LOADING);
			// Esconde os avisos de palavras fora do vocabulário, que são normais no modo restrito.
			LibVosk.vosk_set_log_level(QUIET_LOG_LEVEL);
			Model model = new Model(modelDir.toString());
			if (usesWhisper(language)) {
				loadWhisper();
			}
			return model;
		} catch (IOException e) {
			throw new IllegalStateException("Não foi possível preparar o modelo de voz de " + language, e);
		}
	}

	/** Sem o Whisper, a voz ainda funciona, só que pior. Por isso, uma falha aqui não impede o resto. */
	private static void loadWhisper() {
		if (whisper != null) {
			return;
		}
		try {
			whisper = WhisperEngine.load(modelsDir().resolve(WHISPER_FILE));
		} catch (IOException | LinkageError e) {
			LinguaCraft.LOGGER.warn("O Whisper não carregou. O reconhecimento usa só o Vosk.", e);
		}
	}

	private static void downloadWhisper(String language, boolean moreToDownload) throws IOException {
		Files.createDirectories(modelsDir());
		Path part = modelsDir().resolve(WHISPER_FILE + ".part");
		try {
			fetch(language, LinguaCraft.config.whisperModelUrl, part, 0, moreToDownload ? WHISPER_SHARE_PERCENT : 100);
			Files.move(part, modelsDir().resolve(WHISPER_FILE), StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(part);
		}
	}

	private static void download(String language, String modelName, Path modelsDir, Path modelDir, int startPercent) throws IOException {
		Files.createDirectories(modelsDir);
		Path zip = modelsDir.resolve(modelName + ".zip.part");
		Path extractDir = modelsDir.resolve(modelName + ".extracting");
		try {
			fetch(language, LinguaCraft.config.voiceModelBaseUrl + modelName + ".zip", zip, startPercent, 100 - startPercent);
			PHASES.put(language, Phase.EXTRACTING);
			deleteRecursively(extractDir);
			extract(zip, extractDir);
			Path extracted = extractDir.resolve(modelName);
			Files.move(Files.isDirectory(extracted) ? extracted : extractDir, modelDir, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(zip);
			deleteRecursively(extractDir);
		}
	}

	/** O progresso na tela é a soma dos arquivos: este vai de {@code startPercent} a {@code startPercent + spanPercent}. */
	private static void fetch(String language, String url, Path target, int startPercent, int spanPercent) throws IOException {
		LinguaCraft.LOGGER.info("Baixando modelo de voz: {}", url);
		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
		connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
		connection.setReadTimeout(READ_TIMEOUT_MILLIS);
		connection.setRequestProperty("User-Agent", "LinguaCraft");
		try {
			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				throw new IOException("Resposta HTTP " + connection.getResponseCode() + " ao baixar " + url);
			}
			long total = connection.getContentLengthLong();
			long written = 0;
			try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
				byte[] buffer = new byte[1 << 16];
				int read;
				while ((read = in.read(buffer)) >= 0) {
					out.write(buffer, 0, read);
					written += read;
					if (total > 0) {
						PERCENTS.put(language, startPercent + (int) (written * spanPercent / total));
					}
				}
			}
			if (total > 0 && written != total) {
				throw new IOException("Download incompleto: " + written + " de " + total + " bytes");
			}
		} finally {
			connection.disconnect();
		}
	}

	private static void extract(Path zip, Path targetDir) throws IOException {
		Path root = targetDir.toAbsolutePath().normalize();
		try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				Path target = root.resolve(entry.getName()).normalize();
				// Recusa entradas que tentem escrever fora da pasta do modelo.
				if (!target.startsWith(root)) {
					throw new IOException("Entrada inválida no modelo: " + entry.getName());
				}
				if (entry.isDirectory()) {
					Files.createDirectories(target);
				} else {
					Files.createDirectories(target.getParent());
					Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private static void deleteRecursively(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(path)) {
			for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(entry);
			}
		}
	}
}
