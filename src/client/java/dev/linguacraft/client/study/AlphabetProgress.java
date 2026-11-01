package dev.linguacraft.client.study;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.linguacraft.LinguaCraft;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** O quanto o jogador já domina cada letra neste mundo, e a escolha das letras do treino. */
public final class AlphabetProgress {
	public static final int MAX_LEVEL = 5;
	private static final int LEARNED_LEVEL = 3;
	private static final int LEVELS_LOST_ON_MISS = 2;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final class Stored {
		String lastGroup = "";
		Map<String, Integer> levels = new HashMap<>();
	}

	private final Path file;
	private Stored stored = new Stored();

	public AlphabetProgress(Path file) {
		this.file = file;
		load();
	}

	public int level(Alphabet.Letter letter) {
		return stored.levels.getOrDefault(letter.glyph(), 0);
	}

	public int learned(Alphabet.Group group) {
		return (int) group.letters().stream().filter(letter -> level(letter) >= LEARNED_LEVEL).count();
	}

	/** Acertar sobe um nível, e errar desce dois, para a letra voltar logo ao treino. */
	public void answered(Alphabet.Letter letter, boolean right) {
		int level = level(letter);
		stored.levels.put(letter.glyph(), right ? Math.min(MAX_LEVEL, level + 1) : Math.max(0, level - LEVELS_LOST_ON_MISS));
		save();
	}

	public String lastGroup() {
		return stored.lastGroup;
	}

	public void rememberGroup(String id) {
		if (!id.equals(stored.lastGroup)) {
			stored.lastGroup = id;
			save();
		}
	}

	/** Uma rodada de treino. As letras menos dominadas vêm primeiro, e as empatadas vêm embaralhadas. */
	public List<Alphabet.Letter> session(Alphabet.Group group, int size, Random random) {
		List<Alphabet.Letter> letters = new ArrayList<>(group.letters());
		Collections.shuffle(letters, random);
		letters.sort(Comparator.comparingInt(this::level));
		List<Alphabet.Letter> session = new ArrayList<>(letters.subList(0, Math.min(size, letters.size())));
		Collections.shuffle(session, random);
		return session;
	}

	/**
	 * A resposta certa e alternativas erradas, embaralhadas. Letras com a mesma leitura, como じ e ぢ,
	 * nunca aparecem juntas, porque as duas estariam certas.
	 */
	public static List<Alphabet.Letter> options(Alphabet.Letter target, Alphabet.Group group, int count, Random random) {
		List<Alphabet.Letter> others = new ArrayList<>(group.letters());
		Collections.shuffle(others, random);
		List<Alphabet.Letter> options = new ArrayList<>();
		options.add(target);
		for (Alphabet.Letter other : others) {
			if (options.size() < count && options.stream().noneMatch(option -> option.romaji().equals(other.romaji()))) {
				options.add(other);
			}
		}
		Collections.shuffle(options, random);
		return options;
	}

	private void load() {
		if (!Files.exists(file)) {
			return;
		}
		try {
			Stored loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Stored.class);
			if (loaded != null && loaded.levels != null) {
				stored = loaded;
			}
		} catch (IOException | JsonParseException e) {
			LinguaCraft.LOGGER.error("Progresso do alfabeto inválido em {}", file, e);
		}
	}

	private void save() {
		try {
			Files.createDirectories(file.getParent());
			Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temporary, GSON.toJson(stored), StandardCharsets.UTF_8);
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LinguaCraft.LOGGER.warn("Não foi possível salvar {}", file, e);
		}
	}
}
