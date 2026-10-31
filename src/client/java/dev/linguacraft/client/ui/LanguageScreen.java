package dev.linguacraft.client.ui;

import dev.linguacraft.data.PlayerProgress;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Aparece na primeira entrada do jogador no mundo. Não fecha sem uma escolha. */
public final class LanguageScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 6;

	private final Consumer<String> onChosen;

	public LanguageScreen(Consumer<String> onChosen) {
		super(Component.translatable("linguacraft.setup.title"));
		this.onChosen = onChosen;
	}

	@Override
	protected void init() {
		int x = (width - BUTTON_WIDTH) / 2;
		int y = height / 2 - 24;
		addOption("linguacraft.setup.korean", PlayerProgress.KOREAN, x, y);
		addOption("linguacraft.setup.japanese", PlayerProgress.JAPANESE, x, y + BUTTON_HEIGHT + BUTTON_GAP);
		addOption("linguacraft.setup.both", PlayerProgress.BOTH, x, y + (BUTTON_HEIGHT + BUTTON_GAP) * 2);
	}

	private void addOption(String key, String language, int x, int y) {
		addRenderableWidget(Button.builder(Component.translatable(key), button -> onChosen.accept(language))
				.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 48, UiColors.WHITE);
		graphics.drawCenteredString(font, Component.translatable("linguacraft.setup.hint"), width / 2, height / 2 + 62, UiColors.MUTED);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
