package dev.langcraft.client.voice;

import com.sun.jna.Platform;
import dev.langcraft.LangCraft;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/**
 * Lista e abre os microfones do sistema. Um identificador vazio significa o microfone padrão.
 *
 * No Linux, usa o PulseAudio ou o PipeWire quando existem: o Java só enxerga as placas de som pelo ALSA,
 * com nomes como "[plughw:0,0]", e não vê microfones Bluetooth nem respeita o padrão escolhido no sistema.
 */
public final class Microphones {
	public record Device(String id, String label) {
	}

	/** Uma captura de áudio aberta, em 16 bits mono. */
	public interface Input extends AutoCloseable {
		/** Retorna a quantidade de bytes lidos, ou um número negativo se a captura terminou. */
		int read(byte[] buffer) throws IOException;

		float sampleRate();

		@Override
		void close();
	}

	public static final String SYSTEM_DEFAULT = "";
	private static final String LINUX_DEFAULT_PREFIX = "default [";
	private static final AudioFormat PROBE_FORMAT = new AudioFormat(16000f, 16, 1, true, false);
	private static final Map<String, String> LABELS = new ConcurrentHashMap<>();

	private Microphones() {
	}

	/** Chave de tradução do nome da opção padrão. No Linux, ela é a entrada escolhida nas configurações do sistema. */
	public static String defaultLabelKey() {
		return Platform.isLinux() ? "langcraft.microphone.system_input" : "langcraft.microphone.default";
	}

	/** Os microfones disponíveis, sem o padrão do sistema, que as telas oferecem à parte. */
	public static List<Device> devices() {
		List<Device> devices = PulseAudio.available() ? PulseAudio.sources() : PipeWire.available() ? PipeWire.sources() : javaDevices();
		devices.forEach(device -> LABELS.put(device.id(), device.label()));
		return devices;
	}

	/** Nome para mostrar na tela. Não abre processos a cada quadro: o resultado fica guardado. */
	public static String label(String id) {
		String label = LABELS.get(id);
		if (label == null) {
			devices();
			label = LABELS.computeIfAbsent(id, unknown -> unknown.replaceFirst("^(" + PulseAudio.ID_PREFIX + "|" + PipeWire.ID_PREFIX + ")", ""));
		}
		return label;
	}

	private static List<Device> javaDevices() {
		List<Device> devices = new ArrayList<>();
		DataLine.Info capture = new DataLine.Info(TargetDataLine.class, PROBE_FORMAT);
		for (Mixer.Info info : AudioSystem.getMixerInfo()) {
			if (AudioSystem.getMixer(info).isLineSupported(capture) && !info.getName().startsWith(LINUX_DEFAULT_PREFIX)) {
				devices.add(new Device(info.getName(), info.getName()));
			}
		}
		// No Windows, a primeira entrada é o padrão do sistema, com um nome que muda com o idioma.
		if (Platform.isWindows() && !devices.isEmpty()) {
			devices.remove(0);
		}
		return devices;
	}

	/** Abre um microfone. Quem chama decide o que fazer se ele não abrir. */
	public static Input open(String id, float sampleRate) throws LineUnavailableException {
		if (id.startsWith(PulseAudio.ID_PREFIX)) {
			return PulseAudio.record(id.substring(PulseAudio.ID_PREFIX.length()), sampleRate);
		}
		if (id.startsWith(PipeWire.ID_PREFIX)) {
			return PipeWire.record(id.substring(PipeWire.ID_PREFIX.length()), sampleRate);
		}
		if (id.equals(SYSTEM_DEFAULT) && PulseAudio.available()) {
			return PulseAudio.record(null, sampleRate);
		}
		if (id.equals(SYSTEM_DEFAULT) && PipeWire.available()) {
			return PipeWire.record(null, sampleRate);
		}
		AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
		Mixer.Info mixer = findMixer(id);
		// Sem um mixer, é preciso deixar o Java procurar: o mixer padrão dele pode ser o de saída de som.
		TargetDataLine line = mixer == null ? AudioSystem.getTargetDataLine(format) : AudioSystem.getTargetDataLine(format, mixer);
		line.open(format);
		line.start();
		return new LineInput(line);
	}

	private static Mixer.Info findMixer(String name) throws LineUnavailableException {
		if (name.equals(SYSTEM_DEFAULT)) {
			return linuxDefaultMixer();
		}
		for (Mixer.Info info : AudioSystem.getMixerInfo()) {
			if (info.getName().equals(name)) {
				return info;
			}
		}
		throw new LineUnavailableException("Microfone não encontrado: " + name);
	}

	/** Sem o PulseAudio, a entrada do sistema é o dispositivo "default" do ALSA, e não a primeira placa de som. */
	private static Mixer.Info linuxDefaultMixer() {
		DataLine.Info capture = new DataLine.Info(TargetDataLine.class, PROBE_FORMAT);
		for (Mixer.Info info : AudioSystem.getMixerInfo()) {
			if (info.getName().startsWith(LINUX_DEFAULT_PREFIX) && AudioSystem.getMixer(info).isLineSupported(capture)) {
				return info;
			}
		}
		return null;
	}

	/** Volume de um trecho de áudio de 16 bits, de 0 a 1. */
	public static float level(byte[] audio, int length) {
		int samples = length / 2;
		if (samples == 0) {
			return 0f;
		}
		double sum = 0;
		for (int i = 0; i < samples; i++) {
			int sample = (short) ((audio[i * 2] & 0xFF) | (audio[i * 2 + 1] << 8));
			sum += (double) sample * sample;
		}
		// Fala normal fica bem abaixo do volume máximo, então a raiz deixa a barra mais visível.
		return (float) Math.min(1.0, Math.sqrt(Math.sqrt(sum / samples) / Short.MAX_VALUE));
	}

	private record LineInput(TargetDataLine line) implements Input {
		@Override
		public int read(byte[] buffer) {
			return line.read(buffer, 0, buffer.length);
		}

		@Override
		public float sampleRate() {
			return line.getFormat().getSampleRate();
		}

		@Override
		public void close() {
			line.close();
			LangCraft.LOGGER.debug("Microfone fechado");
		}
	}
}
