package dev.linguacraft;

import dev.linguacraft.challenge.ChallengeManager;
import dev.linguacraft.command.LinguaCommand;
import dev.linguacraft.config.LinguaConfig;
import dev.linguacraft.data.PlayerProgress;
import dev.linguacraft.net.ChallengeEndedPayload;
import dev.linguacraft.net.ChallengeResultPayload;
import dev.linguacraft.net.ChallengeStartPayload;
import dev.linguacraft.net.ClientReadyPayload;
import dev.linguacraft.net.LanguageChoicePayload;
import dev.linguacraft.net.OpenScreenPayload;
import dev.linguacraft.net.RouletteResultPayload;
import dev.linguacraft.net.RouletteSpinPayload;
import dev.linguacraft.net.RouletteStartPayload;
import dev.linguacraft.net.ScorePayload;
import dev.linguacraft.net.VoiceSetupPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinguaCraft implements ModInitializer {
	public static final String MOD_ID = "linguacraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final AttachmentType<PlayerProgress> PROGRESS = AttachmentRegistry.create(id("progress"),
			builder -> builder.persistent(PlayerProgress.CODEC).copyOnDeath());

	public static LinguaConfig config;
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
		config = LinguaConfig.load();

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
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> LinguaCommand.register(dispatcher));
	}
}
