package dev.langcraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.langcraft.LangCraft;
import dev.langcraft.challenge.MobPunishment;
import dev.langcraft.challenge.Roulette;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** Lido de {@code config/langcraft.json}. Se o arquivo não existir, é criado com estes padrões. */
public class LangConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public int itemIntervalMinSeconds = 300;
	public int itemIntervalMaxSeconds = 600;
	public int mobIntervalMinSeconds = 480;
	public int mobIntervalMaxSeconds = 900;

	public int speakSeconds = 30;
	public int quizSeconds = 20;
	public int holdSeconds = 30;

	/** Similaridade mínima, de 0 a 1, para aceitar uma resposta falada. */
	public double voiceSimilarity = 0.75;

	/** Nome do microfone escolhido. Vazio usa o padrão do sistema. */
	public String microphone = "";

	/** De onde baixar os modelos de voz. Termina com barra. */
	public String voiceModelBaseUrl = "https://alphacephei.com/vosk/models/";

	/** De onde baixar o modelo do Whisper, usado no coreano. */
	public String whisperModelUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin";

	/** Mostra a leitura em hiragana sobre os kanjis. */
	public boolean showFurigana = true;

	/** Mostra o placar de sequência, acertos e erros no canto da tela. */
	public boolean showScoreboard = true;

	/** Mostra o título antes do nome e os acertos na lista do Tab, para todos verem. */
	public boolean showTitles = true;

	/** Idioma de quem ainda não escolheu: um ou mais códigos, separados por vírgula, como ko_kr,de_de. */
	public String defaultLanguage = "both";

	public int rouletteAutoSpinSeconds = 10;
	public List<Roulette.SliceSpec> rouletteSlices = List.of(
			new Roulette.SliceSpec(0, 25),
			new Roulette.SliceSpec(25, 25),
			new Roulette.SliceSpec(50, 20),
			new Roulette.SliceSpec(75, 15),
			new Roulette.SliceSpec(100, 15));

	public boolean rewardRouletteEnabled = true;
	public int rewardStreak = 5;
	public int rewardFallbackLevels = 5;
	public List<String> rewardBlockedItems = new ArrayList<>();

	/** Coloca os mobs de outros mods nas categorias abaixo, pelo tipo e pela vida de cada um. */
	public boolean includeModMobs = true;
	/** Mobs que nunca são sorteados, como um chefe de outro mod. Exemplo: "botania:doppleganger". */
	public List<String> blockedMobs = new ArrayList<>();

	public List<MobPunishment.Tier> mobTiers = List.of(
			new MobPunishment.Tier("harmless", 40, List.of(
					"minecraft:chicken", "minecraft:axolotl", "minecraft:rabbit", "minecraft:sheep", "minecraft:pig",
					"minecraft:cow", "minecraft:cat", "minecraft:frog", "minecraft:turtle", "minecraft:parrot")),
			new MobPunishment.Tier("neutral", 25, List.of(
					"minecraft:bee", "minecraft:wolf", "minecraft:llama", "minecraft:polar_bear", "minecraft:panda",
					"minecraft:iron_golem")),
			new MobPunishment.Tier("hostile", 22, List.of(
					"minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:creeper", "minecraft:witch",
					"minecraft:slime")),
			new MobPunishment.Tier("dangerous", 11, List.of(
					"minecraft:vindicator", "minecraft:blaze", "minecraft:ravager", "minecraft:piglin_brute",
					"minecraft:evoker")),
			new MobPunishment.Tier("extreme", 2, List.of("minecraft:warden")));

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("langcraft.json");
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

	public static LangConfig load() {
		Path path = path();
		LangConfig config = null;
		if (Files.exists(path)) {
			try {
				config = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), LangConfig.class);
			} catch (IOException | JsonParseException e) {
				LangCraft.LOGGER.error("Configuração inválida em {}, usando os padrões", path, e);
				return new LangConfig();
			}
		}
		if (config == null) {
			config = new LangConfig();
		}
		config.save();
		return config;
	}
}
