package dev.langcraft;

import dev.langcraft.challenge.ChallengeManager;
import dev.langcraft.config.LangConfig;
import dev.langcraft.data.PlayerProgress;
import dev.langcraft.data.ProgressStore;
import dev.langcraft.platform.Platform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** O que é igual em todos os loaders. Cada um chama {@link #init} ao carregar. */
public final class LangCraft {
	public static final String MOD_ID = "langcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Platform platform;
	public static LangConfig config;
	public static ChallengeManager challenges;

	private LangCraft() {
	}

	public static void init(Platform loader) {
		platform = loader;
		config = LangConfig.load();
		challenges = new ChallengeManager(config);
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}

	public static PlayerProgress newProgress() {
		return PlayerProgress.initial(config.defaultLanguage);
	}

	public static PlayerProgress progress(ServerPlayer player) {
		return ProgressStore.get(player);
	}

	public static void setProgress(ServerPlayer player, PlayerProgress progress) {
		ProgressStore.set(player, progress);
	}
}
