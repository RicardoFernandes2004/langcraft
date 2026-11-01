package dev.langcraft.client.lang;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import dev.langcraft.LangCraft;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * As traduções feitas pelos jogadores, para itens e mobs de mods que não trazem o idioma. Cada idioma tem um
 * arquivo, gerado pelo {@code /lang missing}, com a chave de tradução, o nome original e a tradução a preencher.
 */
public final class CommunityNames {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static final class Entry {
		public String name = "";
		public String translation = "";
	}

	private final Path file;
	private final Map<String, Entry> entries = new LinkedHashMap<>();

	public CommunityNames(Path file) {
		this.file = file;
		load();
	}

	public Optional<String> translation(String key) {
		Entry entry = entries.get(key);
		return entry == null || entry.translation == null || entry.translation.isBlank() ? Optional.empty() : Optional.of(entry.translation.strip());
	}

	/** Acrescenta o que falta, sem tocar no que já existe, e salva. Recebe a chave e o nome original. */
	public void addMissing(Map<String, String> originalNames) {
		boolean changed = false;
		for (Map.Entry<String, String> original : originalNames.entrySet()) {
			if (!entries.containsKey(original.getKey())) {
				Entry entry = new Entry();
				entry.name = original.getValue();
				entries.put(original.getKey(), entry);
				changed = true;
			}
		}
		if (changed) {
			save();
		}
	}

	/** Quantas entradas ainda estão sem tradução. */
	public int pending() {
		return (int) entries.values().stream().filter(entry -> entry.translation == null || entry.translation.isBlank()).count();
	}

	public Path file() {
		return file;
	}

	private void load() {
		if (!Files.exists(file)) {
			return;
		}
		try {
			Map<String, Entry> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<LinkedHashMap<String, Entry>>() { }.getType());
			if (loaded != null) {
				entries.putAll(loaded);
			}
		} catch (IOException | JsonParseException e) {
			// Guarda o arquivo ruim, porque o próximo /lang missing escreveria por cima das traduções.
			LangCraft.LOGGER.error("Traduções inválidas em {}", file, e);
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + ".invalid"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException moveFailure) {
				LangCraft.LOGGER.warn("Não foi possível guardar {}", file, moveFailure);
			}
		}
	}

	private void save() {
		try {
			Files.createDirectories(file.getParent());
			Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temporary, GSON.toJson(entries), StandardCharsets.UTF_8);
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LangCraft.LOGGER.warn("Não foi possível salvar {}", file, e);
		}
	}
}
