package dev.langcraft.client.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class PipeWireTest {
	private static final String DUMP = """
			[
			  {"id": 30, "type": "PipeWire:Interface:Core", "info": {"props": {"core.name": "pipewire-0"}}},
			  {"id": 31, "type": "PipeWire:Interface:Node", "info": {"props": {"media.class": "Audio/Sink", "node.name": "saida", "node.description": "Alto-falantes"}}},
			  {"id": 32, "type": "PipeWire:Interface:Node", "info": {"props": {"media.class": "Audio/Source", "node.name": "alsa_input.digital", "node.description": "Digital Microphone"}}},
			  {"id": 33, "type": "PipeWire:Interface:Node", "info": {"props": {"media.class": "Audio/Source", "node.name": "sem_descricao"}}},
			  {"id": 34, "type": "PipeWire:Interface:Metadata", "info": null},
			  {"id": 35, "type": "PipeWire:Interface:Module"}
			]
			""";

	@Test
	void keepsOnlyAudioSources() {
		assertEquals(List.of(
				new Microphones.Device("pipewire:alsa_input.digital", "Digital Microphone"),
				new Microphones.Device("pipewire:sem_descricao", "sem_descricao")),
				PipeWire.parseDump(DUMP));
	}

	@Test
	void findsTheAudioAfterAWavHeader() {
		byte[] wav = "RIFF....WAVEfmt ................data....AUDIO".getBytes(StandardCharsets.US_ASCII);
		assertEquals(wav.length - "AUDIO".length(), PipeWire.wavHeaderLength(wav, wav.length));
	}

	@Test
	void rawAudioHasNoHeader() {
		byte[] raw = new byte[64];
		assertEquals(0, PipeWire.wavHeaderLength(raw, raw.length));
	}
}
