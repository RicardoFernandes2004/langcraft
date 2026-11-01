package dev.langcraft.client.voice;

import com.sun.jna.Platform;
import dev.langcraft.LangCraft;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.LineUnavailableException;

/**
 * Microfones pelo PulseAudio ou pelo PipeWire, que também atende aos comandos pactl e parec.
 * Usa esses programas porque o Java não tem como falar diretamente com esses servidores de áudio.
 */
final class PulseAudio {
	static final String ID_PREFIX = "pulse:";

	private static final int COMMAND_TIMEOUT_SECONDS = 3;
	/** Tempo para o parec recusar um microfone inválido, antes de considerá-lo aberto. */
	private static final int STARTUP_CHECK_MILLIS = 200;
	private static final String MONITOR_SUFFIX = ".monitor";
	private static final String NOT_A_MONITOR = "n/a";

	private PulseAudio() {
	}

	static boolean available() {
		return Platform.isLinux() && run("pactl", "info") != null;
	}

	static List<Microphones.Device> sources() {
		String output = run("pactl", "list", "sources");
		return output == null ? new ArrayList<>() : parseSources(output);
	}

	/** Lê a saída de "pactl list sources". Monitores ficam de fora, porque gravam o som que sai do computador. */
	static List<Microphones.Device> parseSources(String output) {
		List<Microphones.Device> devices = new ArrayList<>();
		String name = null;
		String description = null;
		boolean monitor = false;
		for (String rawLine : (output + "\nSource #end").split("\n")) {
			String line = rawLine.strip();
			if (line.startsWith("Source #")) {
				if (name != null && !monitor && !name.endsWith(MONITOR_SUFFIX)) {
					devices.add(new Microphones.Device(ID_PREFIX + name, description == null ? name : description));
				}
				name = null;
				description = null;
				monitor = false;
			} else if (line.startsWith("Name: ")) {
				name = line.substring("Name: ".length());
			} else if (line.startsWith("Description: ")) {
				description = line.substring("Description: ".length());
			} else if (line.startsWith("Monitor of Sink: ")) {
				monitor = !line.substring("Monitor of Sink: ".length()).equals(NOT_A_MONITOR);
			}
		}
		return devices;
	}

	/** Sem {@code source}, grava do microfone padrão escolhido no sistema. */
	static Microphones.Input record(String source, float sampleRate) throws LineUnavailableException {
		List<String> command = new ArrayList<>(List.of("parec", "--format=s16le", "--channels=1", "--rate=" + Math.round(sampleRate), "--latency-msec=100"));
		if (source != null) {
			command.add("--device=" + source);
		}
		try {
			Process process = new ProcessBuilder(command).start();
			Thread.sleep(STARTUP_CHECK_MILLIS);
			if (!process.isAlive()) {
				String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).strip();
				throw new LineUnavailableException("parec terminou: " + error);
			}
			return new ProcessInput(process, sampleRate);
		} catch (IOException e) {
			throw new LineUnavailableException("Não foi possível iniciar o parec: " + e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new LineUnavailableException("Interrompido ao abrir o microfone");
		}
	}

	/** Retorna a saída do comando, ou nulo se ele não existir, falhar ou demorar demais. */
	private static String run(String... command) {
		try {
			ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
			// A saída precisa vir em inglês para ser lida, em qualquer idioma do sistema.
			builder.environment().put("LC_ALL", "C");
			Process process = builder.start();
			CompletableFuture<byte[]> output = CompletableFuture.supplyAsync(() -> {
				try (InputStream in = process.getInputStream()) {
					return in.readAllBytes();
				} catch (IOException e) {
					return new byte[0];
				}
			});
			if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				LangCraft.LOGGER.warn("O comando {} demorou demais", command[0]);
				return null;
			}
			return process.exitValue() == 0 ? new String(output.join(), StandardCharsets.UTF_8) : null;
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	private record ProcessInput(Process process, float sampleRate) implements Microphones.Input {
		@Override
		public int read(byte[] buffer) throws IOException {
			return process.getInputStream().read(buffer);
		}

		@Override
		public void close() {
			process.destroy();
		}
	}
}
