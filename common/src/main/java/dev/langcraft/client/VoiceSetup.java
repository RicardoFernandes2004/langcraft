package dev.langcraft.client;

import dev.langcraft.LangCraft;
import dev.langcraft.client.study.WorldFlashcards;
import dev.langcraft.client.ui.LanguageScreen;
import dev.langcraft.client.ui.MicTestScreen;
import dev.langcraft.client.ui.MicrophoneScreen;
import dev.langcraft.client.ui.ThemeScreen;
import dev.langcraft.client.ui.VoiceSetupScreen;
import dev.langcraft.client.voice.ModelManager;
import dev.langcraft.data.PlayerProgress;
import dev.langcraft.net.ClientReadyPayload;
import dev.langcraft.net.LanguageChoicePayload;
import dev.langcraft.net.VoiceSetupPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/** Conduz tema, idioma, microfone, download e palavra de teste, antes de os desafios começarem. */
public final class VoiceSetup {
	private static @Nullable VoiceSetupPayload pending;
	private static List<String> currentLanguages = List.of();

	private VoiceSetup() {
	}

	/** Os idiomas escolhidos pelo jogador neste mundo. */
	public static List<String> currentLanguages() {
		return currentLanguages;
	}

	public static void onSetup(VoiceSetupPayload payload) {
		pending = payload;
		currentLanguages = PlayerProgress.languagesOf(payload.language());
	}

	/** Espera não haver nenhuma tela aberta: a tela de carregamento do mundo fecharia a nossa. */
	public static void tick(Minecraft minecraft) {
		if (pending == null || minecraft.player == null || minecraft.screen != null || ChallengeClient.current() != null) {
			return;
		}
		VoiceSetupPayload setup = pending;
		pending = null;
		if (WorldFlashcards.themeId() == null) {
			minecraft.setScreen(new ThemeScreen(() -> begin(minecraft, setup)));
		} else {
			begin(minecraft, setup);
		}
	}

	private static void begin(Minecraft minecraft, VoiceSetupPayload setup) {
		if (setup.chooseLanguage()) {
			minecraft.setScreen(new LanguageScreen(VoiceSetup::onLanguageChosen));
		} else {
			prepareModels(minecraft, setup.language(), false);
		}
	}

	/** Troca de idioma pedida pelo jogador. O servidor responde pedindo os modelos do novo idioma. */
	public static void openLanguageChange(@Nullable Screen parent) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.setScreen(new LanguageScreen(parent, currentLanguages, language -> {
			LangCraft.platform.sendToServer(new LanguageChoicePayload(language));
			minecraft.setScreen(null);
		}));
	}

	private static void onLanguageChosen(String language) {
		LangCraft.platform.sendToServer(new LanguageChoicePayload(language));
		currentLanguages = PlayerProgress.languagesOf(language);
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.setScreen(new MicrophoneScreen(null, microphone -> {
			LangCraft.config.microphone = microphone;
			LangCraft.config.save();
			prepareModels(minecraft, language, true);
		}));
	}

	/**
	 * A tela de download só aparece se faltar baixar algo. A palavra de teste vem depois dela,
	 * porque precisa do modelo. Quem não espera o download pula o teste.
	 */
	private static void prepareModels(Minecraft minecraft, String language, boolean testMicrophone) {
		List<String> languages = PlayerProgress.languagesOf(language);
		boolean needsDownload = languages.stream().anyMatch(code -> !ModelManager.isDownloaded(code));
		languages.forEach(ModelManager::model);
		Runnable afterDownload = testMicrophone
				? () -> minecraft.setScreen(new MicTestScreen(languages, VoiceSetup::close))
				: VoiceSetup::close;
		if (needsDownload) {
			minecraft.setScreen(new VoiceSetupScreen(languages, afterDownload, VoiceSetup::close));
		} else {
			afterDownload.run();
		}
	}

	private static void close() {
		Minecraft.getInstance().setScreen(null);
		LangCraft.platform.sendToServer(new ClientReadyPayload());
	}

	public static void clear() {
		pending = null;
	}
}
