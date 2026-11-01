package dev.langcraft.client.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommunityNamesTest {
	private static final String KEY = "item.botania.manasteel_ingot";

	@TempDir
	Path folder;

	private Path file() {
		return folder.resolve("traducoes/ko_kr.json");
	}

	@Test
	void emptyOrBlankTranslationsAreIgnored() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), """
				{"a": {"name": "A", "translation": ""}, "b": {"name": "B", "translation": "   "}, "c": {"name": "C", "translation": " 마나스틸 "}}
				""");
		CommunityNames names = new CommunityNames(file());
		assertEquals(Optional.empty(), names.translation("a"));
		assertEquals(Optional.empty(), names.translation("b"));
		assertEquals(Optional.of("마나스틸"), names.translation("c"));
		assertEquals(Optional.empty(), names.translation("nao existe"));
		assertEquals(2, names.pending());
	}

	@Test
	void exportingAgainKeepsWhatWasFilledIn() throws IOException {
		CommunityNames names = new CommunityNames(file());
		names.addMissing(Map.of(KEY, "Manasteel Ingot"));
		assertEquals(1, names.pending());

		String filled = Files.readString(file()).replace("\"translation\": \"\"", "\"translation\": \"마나스틸 주괴\"");
		Files.writeString(file(), filled);

		CommunityNames reloaded = new CommunityNames(file());
		reloaded.addMissing(Map.of(KEY, "Outro nome", "item.botania.other", "Other"));
		assertEquals(Optional.of("마나스틸 주괴"), new CommunityNames(file()).translation(KEY));
		assertEquals(1, new CommunityNames(file()).pending());
	}

	@Test
	void theFileIsWrittenInUtf8WithReadableText() throws IOException {
		CommunityNames names = new CommunityNames(file());
		names.addMissing(Map.of("a", "Pão & Queijo's"));
		assertTrue(Files.readString(file()).contains("Pão & Queijo's"));
	}

	@Test
	void aBrokenFileIsKeptAside() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), "isto nao e json");
		CommunityNames names = new CommunityNames(file());
		assertEquals(0, names.pending());
		assertTrue(Files.exists(file().resolveSibling("ko_kr.json.invalid")));
	}
}
