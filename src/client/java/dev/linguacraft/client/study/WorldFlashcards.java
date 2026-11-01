package dev.linguacraft.client.study;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * As anotações do mundo aberto: flashcards e progresso do alfabeto. Em um mundo próprio, ficam dentro da pasta
 * do mundo, e são apagadas com ele. Em um servidor, a pasta do mundo não está acessível, então ficam na
 * configuração, por endereço.
 */
public final class WorldFlashcards {
	private static final String UNKNOWN_SERVER = "servidor";
	private static @Nullable Path deckFile;
	private static @Nullable FlashcardDeck deck;
	private static @Nullable Path alphabetFile;
	private static @Nullable AlphabetProgress alphabet;

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

	public static void clear() {
		deckFile = null;
		deck = null;
		alphabetFile = null;
		alphabet = null;
	}

	private static Path fileFor(Minecraft minecraft, String name) {
		MinecraftServer server = minecraft.getSingleplayerServer();
		if (server != null) {
			return server.getWorldPath(LevelResource.ROOT).resolve("linguacraft/" + name + ".json").normalize();
		}
		ServerData data = minecraft.getCurrentServer();
		String address = data == null ? UNKNOWN_SERVER : data.ip;
		return FabricLoader.getInstance().getConfigDir().resolve("linguacraft/" + name).resolve(safeName(address) + ".json");
	}

	static String safeName(String address) {
		String name = address.replaceAll("[^a-zA-Z0-9._-]", "_");
		return name.isBlank() ? UNKNOWN_SERVER : name;
	}
}
