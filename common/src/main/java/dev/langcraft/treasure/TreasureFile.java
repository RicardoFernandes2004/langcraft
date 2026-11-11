package dev.langcraft.treasure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.langcraft.LangCraft;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code config/langcraft/treasure.json}. A parte de cima é do admin e nunca é apagada. A lista {@code items}
 * é refeita a cada classificação, com a faixa e o motivo de cada item, só para consulta.
 */
public final class TreasureFile {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public String help = "overrides: force a tier for an item (common, rare, epic, legendary, mythic). modBonus: points added to every item of a mod, "
			+ "negative to lower. banned and bannedMods: never given. items: the automatic result, rewritten on every reload.";
	public Map<String, String> overrides = new LinkedHashMap<>();
	public Map<String, Double> modBonus = new LinkedHashMap<>();
	public List<String> banned = new ArrayList<>();
	/** As ferramentas do Tinkers só funcionam montadas com peças, e sairiam vazias. */
	public List<String> bannedMods = new ArrayList<>(List.of("tconstruct"));
	public Map<String, Entry> items = new LinkedHashMap<>();

	public static final class Entry {
		public String tier;
		public double score;
		public List<String> why;

		Entry(String tier, double score, List<String> why) {
			this.tier = tier;
			this.score = score;
			this.why = why;
		}
	}

	static Path path() {
		return LangCraft.platform.configDir().resolve("langcraft").resolve("treasure.json");
	}

	public static TreasureFile load() {
		Path path = path();
		if (Files.exists(path)) {
			try {
				TreasureFile file = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), TreasureFile.class);
				if (file != null) {
					return file;
				}
			} catch (IOException | JsonParseException e) {
				LangCraft.LOGGER.error("Arquivo do tesouro inválido em {}, usando os padrões", path, e);
			}
		}
		return new TreasureFile();
	}

	public void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LangCraft.LOGGER.warn("Não foi possível salvar {}", path, e);
		}
	}
}
