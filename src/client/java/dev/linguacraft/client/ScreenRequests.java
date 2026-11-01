package dev.linguacraft.client;

import dev.linguacraft.client.ui.AlphabetScreen;
import dev.linguacraft.client.ui.LinguaConfigScreen;
import dev.linguacraft.client.ui.StudyScreen;
import dev.linguacraft.client.ui.WordListScreen;
import dev.linguacraft.client.ui.ThemeScreen;
import dev.linguacraft.net.OpenScreenPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/** Abre as telas pedidas por comando. */
final class ScreenRequests {
	private static OpenScreenPayload.@Nullable Target pending;

	private ScreenRequests() {
	}

	static void onRequest(OpenScreenPayload payload) {
		pending = payload.target();
	}

	static void tick(Minecraft minecraft) {
		if (pending == null) {
			return;
		}
		OpenScreenPayload.Target target = pending;
		pending = null;
		// Um desafio pode ter aberto a tela dele nesse meio-tempo, e ela tem prioridade. O chat pode ser
		// substituído: clicar em um comando da lista deixa o chat aberto.
		boolean otherScreen = minecraft.screen != null && !(minecraft.screen instanceof ChatScreen);
		if (otherScreen || minecraft.player == null) {
			return;
		}
		switch (target) {
			case THEME -> minecraft.setScreen(new ThemeScreen((Screen) null));
			case LANGUAGE -> VoiceSetup.openLanguageChange(null);
			case CONFIG -> minecraft.setScreen(new LinguaConfigScreen(null));
			case STUDY -> minecraft.setScreen(new StudyScreen(null));
			case WORDS -> minecraft.setScreen(new WordListScreen(null));
			case ALPHABET -> minecraft.setScreen(new AlphabetScreen(null));
		}
	}

	static void clear() {
		pending = null;
	}
}
