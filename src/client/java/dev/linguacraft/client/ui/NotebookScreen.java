package dev.linguacraft.client.ui;

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

	/** Um botão que passa por todos os idiomas com palavras no caderno. Com cinco idiomas, não caberia um botão para cada. */
	protected static Component languageFilterLabel(@Nullable String language) {
		Component name = language == null ? Component.translatable("linguacraft.study.all") : Component.translatable("linguacraft.language." + language);
		return Component.translatable("linguacraft.study.filter", name);
	}

	/** O idioma seguinte do filtro. Depois do último, volta para todos. */
	protected static @Nullable String nextLanguage(@Nullable String language, List<String> languages) {
		int next = languages.indexOf(language) + 1;
		return next < languages.size() ? languages.get(next) : null;
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
