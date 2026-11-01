package dev.langcraft.client.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PulseAudioTest {
	private static final String OUTPUT = """
			Source #55
				State: SUSPENDED
				Name: alsa_output.pci-0000_00_1f.3.analog-stereo.monitor
				Description: Monitor of Built-in Audio Analog Stereo
				Driver: PipeWire
				Monitor of Sink: alsa_output.pci-0000_00_1f.3.analog-stereo
			Source #56
				State: RUNNING
				Name: alsa_input.pci-0000_00_1f.3.analog-stereo
				Description: Áudio interno Estéreo analógico
				Driver: PipeWire
				Monitor of Sink: n/a
			Source #57
				Name: bluez_input.AA_BB_CC.0
				Description: Fone Bluetooth
				Monitor of Sink: n/a
			Source #58
				Name: virtual_mic
				Monitor of Sink: n/a
			Source #59
				Name: loopback.monitor
				Description: Sem marca de monitor
			""";

	@Test
	void keepsMicrophonesAndUsesTheirDescriptions() {
		assertEquals(List.of(
				new Microphones.Device("pulse:alsa_input.pci-0000_00_1f.3.analog-stereo", "Áudio interno Estéreo analógico"),
				new Microphones.Device("pulse:bluez_input.AA_BB_CC.0", "Fone Bluetooth"),
				new Microphones.Device("pulse:virtual_mic", "virtual_mic")),
				PulseAudio.parseSources(OUTPUT));
	}

	@Test
	void emptyOutputHasNoMicrophones() {
		assertEquals(List.of(), PulseAudio.parseSources(""));
	}
}
