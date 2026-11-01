package dev.linguacraft.client.ui;

import dev.linguacraft.data.PlayerProgress;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Aparece na primeira entrada do jogador no mundo, quando não fecha sem uma escolha, e depois pelo comando. */
public final class LanguageScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 6;
	private static final int PANEL_WIDTH = 260;

	private final Consumer<String> onChosen;
	private final boolean required;
	private final @Nullable Screen parent;

	/** A escolha obrigatória da primeira entrada. */
	public LanguageScreen(Consumer<String> onChosen) {
		this(onChosen, true, null);
	}

	/** A troca de idioma, que pode ser cancelada com Esc. */
	public LanguageScreen(@Nullable Screen parent, Consumer<String> onChosen) {
		this(onChosen, false, parent);
	}

	private LanguageScreen(Consumer<String> onChosen, boolean required, @Nullable Screen parent) {
		super(Component.translatable("linguacraft.setup.title"));
		this.onChosen = onChosen;
		this.required = required;
		this.parent = parent;
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
		addRenderableWidget(new ThemedButton(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable(key), button -> onChosen.accept(language)));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		UiTheme.Palette palette = UiDraw.palette();
		UiDraw.panel(graphics, (width - PANEL_WIDTH) / 2, height / 2 - 58, PANEL_WIDTH, 136);
		super.render(graphics, mouseX, mouseY, partialTick);
		UiDraw.centered(graphics, font, title, width / 2, height / 2 - 48, palette.ink());
		UiDraw.centered(graphics, font, Component.translatable("linguacraft.setup.hint"), width / 2, height / 2 + 62, palette.muted());
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !required;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
