package dev.langcraft;

import dev.langcraft.challenge.ChallengeManager;
import dev.langcraft.command.LangCommand;
import dev.langcraft.config.LangConfig;
import dev.langcraft.data.PlayerProgress;
import dev.langcraft.net.ChallengeEndedPayload;
import dev.langcraft.net.ChallengeResultPayload;
import dev.langcraft.net.ChallengeStartPayload;
import dev.langcraft.net.ClientReadyPayload;
import dev.langcraft.net.LanguageChoicePayload;
import dev.langcraft.net.OpenScreenPayload;
import dev.langcraft.net.RouletteResultPayload;
import dev.langcraft.net.RouletteSpinPayload;
import dev.langcraft.net.RouletteStartPayload;
import dev.langcraft.net.ScorePayload;
import dev.langcraft.net.VoiceSetupPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LangCraft implements ModInitializer {
	public static final String MOD_ID = "langcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final AttachmentType<PlayerProgress> PROGRESS = AttachmentRegistry.create(id("progress"),
			builder -> builder.persistent(PlayerProgress.CODEC).copyOnDeath());

	public static LangConfig config;
	public static ChallengeManager challenges;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static PlayerProgress progress(ServerPlayer player) {
		return player.getAttachedOrCreate(PROGRESS, () -> PlayerProgress.initial(config.defaultLanguage));
	}

	public static void setProgress(ServerPlayer player, PlayerProgress progress) {
		player.setAttached(PROGRESS, progress);
	}

	@Override
	public void onInitialize() {
		config = LangConfig.load();

		PayloadTypeRegistry.playS2C().register(ChallengeStartPayload.TYPE, ChallengeStartPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ChallengeEndedPayload.TYPE, ChallengeEndedPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RouletteStartPayload.TYPE, RouletteStartPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RouletteResultPayload.TYPE, RouletteResultPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(VoiceSetupPayload.TYPE, VoiceSetupPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenScreenPayload.TYPE, OpenScreenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ScorePayload.TYPE, ScorePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ChallengeResultPayload.TYPE, ChallengeResultPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RouletteSpinPayload.TYPE, RouletteSpinPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(LanguageChoicePayload.TYPE, LanguageChoicePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ClientReadyPayload.TYPE, ClientReadyPayload.CODEC);

		challenges = new ChallengeManager(config);
		challenges.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> LangCommand.register(dispatcher));
	}
}
