package dev.langcraft.client.ui;

import dev.langcraft.client.VoiceSetup;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * As telas de estudo. Sempre têm o visual de caderno, não importa o tema escolhido, porque são as anotações
 * do jogador e não parte do jogo. O caderno só vale durante o desenho da tela: o placar por baixo continua no tema.
 * Nenhum desafio começa com uma dessas telas aberta.
 */
public abstract class NotebookScreen extends Screen {
	/** A espiral e a margem, à esquerda da área de conteúdo. */
	private static final int BINDING = 16;

	protected NotebookScreen(Component title) {
		super(title);
	}

	protected final int pageWidth(int maxWidth) {
		return Math.min(maxWidth, width - 8 - BINDING);
	}

	protected final int pageLeft(int maxWidth) {
		return (width - pageWidth(maxWidth) + BINDING) / 2;
	}

	protected final int pageCenter(int maxWidth) {
		return pageLeft(maxWidth) + pageWidth(maxWidth) / 2;
	}

	protected final int pageHeight(int maxHeight) {
		return Math.min(maxHeight, height - 8);
	}

	protected final int pageTop(int maxHeight) {
		return (height - pageHeight(maxHeight)) / 2;
	}

	/**
	 * O filtro passa pelos idiomas escolhidos pelo jogador. Palavras de um idioma que ele deixou de estudar
	 * ficam guardadas, mas não aparecem, e voltam se ele escolher o idioma de novo.
	 */
	protected static Component languageFilterLabel(@Nullable String language) {
		List<String> chosen = VoiceSetup.currentLanguages();
		String shown = language == null && chosen.size() == 1 ? chosen.getFirst() : language;
		Component name = shown == null ? Component.translatable("langcraft.study.all") : Component.translatable("langcraft.language." + shown);
		return Component.translatable("langcraft.study.filter", name);
	}

	/** Com um idioma só, não há o que filtrar. */
	protected static boolean hasLanguageChoice() {
		return VoiceSetup.currentLanguages().size() > 1;
	}

	/** O idioma seguinte do filtro. Depois do último, volta para todos os escolhidos. */
	protected static @Nullable String nextLanguage(@Nullable String language) {
		List<String> chosen = VoiceSetup.currentLanguages();
		int next = chosen.indexOf(language) + 1;
		return next < chosen.size() ? chosen.get(next) : null;
	}

	/** Os idiomas que a tela mostra: o do filtro ou todos os escolhidos. */
	protected static @Nullable Collection<String> scope(@Nullable String language) {
		if (language != null) {
			return List.of(language);
		}
		List<String> chosen = VoiceSetup.currentLanguages();
		return chosen.isEmpty() ? null : chosen;
	}

	protected final void drawPage(GuiGraphics graphics, int maxWidth, int maxHeight) {
		UiDraw.panel(graphics, pageLeft(maxWidth) - BINDING, pageTop(maxHeight), pageWidth(maxWidth) + BINDING, pageHeight(maxHeight));
	}

	@Override
	public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		UiTheme.useNotebook(true);
		try {
			renderPage(graphics, mouseX, mouseY, partialTick);
		} finally {
			UiTheme.useNotebook(false);
		}
	}

	protected abstract void renderPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

	protected final void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
	}
}
