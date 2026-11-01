package dev.langcraft.client.study;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.langcraft.LangCraft;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * O que é de cada mundo: flashcards, progresso do alfabeto e tema. Em um mundo próprio, ficam dentro da pasta
 * do mundo, e são apagadas com ele. Em um servidor, a pasta do mundo não está acessível, então ficam na
 * configuração, por endereço.
 */
public final class WorldFlashcards {
	private static final String UNKNOWN_SERVER = "server";
	private static final String THEME_KEY = "theme";
	private static @Nullable Path deckFile;
	private static @Nullable FlashcardDeck deck;
	private static @Nullable Path alphabetFile;
	private static @Nullable AlphabetProgress alphabet;
	private static boolean themeLoaded;
	private static @Nullable String themeId;

	private WorldFlashcards() {
	}

	/** Retorna os flashcards do mundo aberto, ou nulo fora de um mundo. */
	public static @Nullable FlashcardDeck current() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return null;
		}
		Path file = fileFor(minecraft, "flashcards");
		if (!file.equals(deckFile)) {
			deckFile = file;
			deck = new FlashcardDeck(file);
		}
		return deck;
	}

	/** Retorna o progresso do alfabeto do mundo aberto, ou nulo fora de um mundo. */
	public static @Nullable AlphabetProgress alphabet() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return null;
		}
		Path file = fileFor(minecraft, "alphabet");
		if (!file.equals(alphabetFile)) {
			alphabetFile = file;
			alphabet = new AlphabetProgress(file);
		}
		return alphabet;
	}

	/** O tema escolhido neste mundo, ou nulo se ainda não houve escolha. O desenho das telas chama isto toda hora, então o arquivo só é lido uma vez. */
	public static @Nullable String themeId() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return null;
		}
		if (!themeLoaded) {
			themeId = readTheme(fileFor(minecraft, "theme"));
			themeLoaded = true;
		}
		return themeId;
	}

	public static void saveThemeId(String id) {
		themeId = id;
		themeLoaded = true;
		writeTheme(fileFor(Minecraft.getInstance(), "theme"), id);
	}

	/** Um arquivo que não dá para ler equivale a não ter escolhido: o jogo pergunta de novo. */
	static @Nullable String readTheme(Path file) {
		if (!Files.exists(file)) {
			return null;
		}
		try {
			JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			return json.has(THEME_KEY) ? json.get(THEME_KEY).getAsString() : null;
		} catch (IOException | JsonParseException | IllegalStateException | ClassCastException e) {
			LangCraft.LOGGER.warn("Tema inválido em {}", file, e);
			return null;
		}
	}

	static void writeTheme(Path file, String id) {
		JsonObject json = new JsonObject();
		json.addProperty(THEME_KEY, id);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LangCraft.LOGGER.warn("Não foi possível salvar {}", file, e);
		}
	}

	public static void clear() {
		deckFile = null;
		deck = null;
		alphabetFile = null;
		alphabet = null;
		themeLoaded = false;
		themeId = null;
	}

	private static Path fileFor(Minecraft minecraft, String name) {
		MinecraftServer server = minecraft.getSingleplayerServer();
		if (server != null) {
			return server.getWorldPath(LevelResource.ROOT).resolve("langcraft/" + name + ".json").normalize();
		}
		ServerData data = minecraft.getCurrentServer();
		String address = data == null ? UNKNOWN_SERVER : data.ip;
		return LangCraft.platform.configDir().resolve("langcraft/" + name).resolve(safeName(address) + ".json");
	}

	static String safeName(String address) {
		String name = address.replaceAll("[^a-zA-Z0-9._-]", "_");
		return name.isBlank() ? UNKNOWN_SERVER : name;
	}
}
