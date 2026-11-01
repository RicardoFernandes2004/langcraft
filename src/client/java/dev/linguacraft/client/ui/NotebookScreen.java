package dev.linguacraft.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
