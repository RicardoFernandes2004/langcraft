package dev.langcraft.client;

import dev.langcraft.client.lang.CommunityNames;
import dev.langcraft.client.lang.GameNames;
import dev.langcraft.client.ui.AlphabetScreen;
import dev.langcraft.client.ui.LangConfigScreen;
import dev.langcraft.client.ui.StudyScreen;
import dev.langcraft.client.ui.WordListScreen;
import dev.langcraft.client.ui.ThemeScreen;
import dev.langcraft.net.OpenScreenPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

/** Faz no cliente o que os comandos pedem, como abrir telas. */
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
			case CONFIG -> minecraft.setScreen(new LangConfigScreen(null));
			case STUDY -> minecraft.setScreen(new StudyScreen(null));
			case WORDS -> minecraft.setScreen(new WordListScreen(null));
			case ALPHABET -> {
				if (AlphabetScreen.canOpen()) {
					minecraft.setScreen(new AlphabetScreen(null));
				} else {
					minecraft.player.displayClientMessage(Component.translatable("langcraft.alphabet.unavailable").withStyle(ChatFormatting.GRAY), false);
				}
			}
			case EXPORT_MISSING -> exportMissing(minecraft);
		}
	}

	/** Não abre tela. Gera os arquivos e diz no chat o que falta, com um link para cada arquivo. */
	private static void exportMissing(Minecraft minecraft) {
		minecraft.player.displayClientMessage(Component.translatable("langcraft.missing.heading").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		for (String language : VoiceSetup.currentLanguages()) {
			CommunityNames names = GameNames.exportMissing(language);
			Component file = Component.translatable("langcraft.missing.open").withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)
					.withClickEvent(new ClickEvent.OpenFile(names.file()))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal(names.file().toString()))));
			minecraft.player.displayClientMessage(Component.translatable("langcraft.missing.line",
					Component.translatable("langcraft.language." + language), names.pending(), file).withStyle(ChatFormatting.GRAY), false);
		}
		minecraft.player.displayClientMessage(Component.translatable("langcraft.missing.hint").withStyle(ChatFormatting.DARK_GRAY), false);
	}

	static void clear() {
		pending = null;
	}
}
