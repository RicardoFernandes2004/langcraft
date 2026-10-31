package dev.linguacraft.client.voice;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Platform;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.LineUnavailableException;

/** Microfones pelos comandos próprios do PipeWire, para sistemas sem o pactl e o parec do PulseAudio. */
final class PipeWire {
	static final String ID_PREFIX = "pipewire:";

	private static final int COMMAND_TIMEOUT_SECONDS = 3;
	private static final int STARTUP_CHECK_MILLIS = 200;
	private static final String SOURCE_CLASS = "Audio/Source";

	private PipeWire() {
	}

	static boolean available() {
		return Platform.isLinux() && dump() != null;
	}

	static List<Microphones.Device> sources() {
		String output = dump();
		return output == null ? new ArrayList<>() : parseDump(output);
	}

	/** Lê a saída de "pw-dump" e mantém só as entradas de áudio. */
	static List<Microphones.Device> parseDump(String json) {
		List<Microphones.Device> devices = new ArrayList<>();
		for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
			JsonObject object = element.getAsJsonObject();
			if (!object.has("info") || !object.get("info").isJsonObject()) {
				continue;
			}
			JsonObject info = object.getAsJsonObject("info");
			if (!info.has("props")) {
				continue;
			}
			JsonObject props = info.getAsJsonObject("props");
			if (SOURCE_CLASS.equals(text(props, "media.class")) && text(props, "node.name") != null) {
				String name = text(props, "node.name");
				String description = text(props, "node.description");
				devices.add(new Microphones.Device(ID_PREFIX + name, description == null ? name : description));
			}
		}
		return devices;
	}

	private static String text(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
	}

	/** Sem {@code target}, grava da entrada padrão escolhida no sistema. */
	static Microphones.Input record(String target, float sampleRate) throws LineUnavailableException {
		List<String> command = new ArrayList<>(List.of("pw-record", "--rate", String.valueOf(Math.round(sampleRate)), "--channels", "1", "--format", "s16"));
		if (target != null) {
			command.add("--target");
			command.add(target);
		}
		command.add("-");
		try {
			Process process = new ProcessBuilder(command).start();
			Thread.sleep(STARTUP_CHECK_MILLIS);
			if (!process.isAlive()) {
				String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).strip();
				throw new LineUnavailableException("pw-record terminou: " + error);
			}
			return new RecordInput(process, sampleRate);
		} catch (IOException e) {
			throw new LineUnavailableException("Não foi possível iniciar o pw-record: " + e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new LineUnavailableException("Interrompido ao abrir o microfone");
		}
	}

	private static String dump() {
		try {
			Process process = new ProcessBuilder("pw-dump").redirectError(ProcessBuilder.Redirect.DISCARD).start();
			CompletableFuture<byte[]> output = CompletableFuture.supplyAsync(() -> {
				try (InputStream in = process.getInputStream()) {
					return in.readAllBytes();
				} catch (IOException e) {
					return new byte[0];
				}
			});
			if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				process.destroyForcibly();
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

	/** Algumas versões do pw-record escrevem um cabeçalho WAV no início, que não pode virar áudio. */
	static int wavHeaderLength(byte[] start, int length) {
		if (length < 12 || start[0] != 'R' || start[1] != 'I' || start[2] != 'F' || start[3] != 'F') {
			return 0;
		}
		for (int i = 12; i + 8 <= length; i++) {
			if (start[i] == 'd' && start[i + 1] == 'a' && start[i + 2] == 't' && start[i + 3] == 'a') {
				return i + 8;
			}
		}
		return 0;
	}

	private static final class RecordInput implements Microphones.Input {
		private final Process process;
		private final float sampleRate;
		private boolean checkedHeader;

		RecordInput(Process process, float sampleRate) {
			this.process = process;
			this.sampleRate = sampleRate;
		}

		@Override
		public int read(byte[] buffer) throws IOException {
			int read = process.getInputStream().read(buffer);
			if (!checkedHeader && read > 0) {
				checkedHeader = true;
				int header = wavHeaderLength(buffer, read);
				if (header > 0) {
					System.arraycopy(buffer, header, buffer, 0, read - header);
					return read - header;
				}
			}
			return read;
		}

		@Override
		public float sampleRate() {
			return sampleRate;
		}

		@Override
		public void close() {
			process.destroy();
		}
	}
}
