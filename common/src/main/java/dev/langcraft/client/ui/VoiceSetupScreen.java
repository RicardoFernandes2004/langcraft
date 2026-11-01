package dev.langcraft.client.ui;

import dev.langcraft.client.voice.ModelManager;
import dev.langcraft.data.StudyLanguage;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Mostra o download dos modelos de voz e fecha sozinha quando todos ficam prontos. */
public final class VoiceSetupScreen extends Screen {
	private static final int ROW_WIDTH = 280;
	private static final int ROW_HEIGHT = 28;
	private static final int BAR_HEIGHT = 6;
	private static final int BUTTON_WIDTH = 160;
	private static final int MAX_PANEL_WIDTH = 380;

	private final List<String> languages;
	private final Runnable onReady;
	private final Runnable onSkip;
	private Button retryButton;
	private Button continueButton;

	public VoiceSetupScreen(List<String> languages, Runnable onReady, Runnable onSkip) {
		super(Component.translatable("langcraft.download.title"));
		this.languages = languages;
		this.onReady = onReady;
		this.onSkip = onSkip;
	}

	@Override
	protected void init() {
		int y = rowsTop() + languages.size() * ROW_HEIGHT + 32;
		retryButton = addRenderableWidget(new ThemedButton(width / 2 - BUTTON_WIDTH - 4, y, BUTTON_WIDTH, 20, Component.translatable("langcraft.download.retry"), button -> retry()));
		continueButton = addRenderableWidget(new ThemedButton(width / 2 + 4, y, BUTTON_WIDTH, 20, Component.translatable("langcraft.download.skip"), button -> onSkip.run()));
		updateButtons();
	}

	/** Com muitos idiomas para baixar, a lista cresce, mas nunca passa do topo da tela. */
	private int rowsTop() {
		return Math.max(32, height / 2 - 36 - languages.size() * ROW_HEIGHT / 2);
	}

	private boolean anyFailed() {
		return languages.stream().anyMatch(language -> ModelManager.phase(language) == ModelManager.Phase.FAILED);
	}

	private void retry() {
		languages.forEach(ModelManager::model);
		updateButtons();
	}

	private void updateButtons() {
		boolean failed = anyFailed();
		retryButton.visible = failed;
		continueButton.setX(failed ? width / 2 + 4 : width / 2 - BUTTON_WIDTH / 2);
		continueButton.setMessage(Component.translatable(failed ? "langcraft.download.no_voice" : "langcraft.download.skip"));
	}

	@Override
	public void tick() {
		updateButtons();
		if (languages.stream().allMatch(language -> ModelManager.phase(language) == ModelManager.Phase.READY)) {
			onReady.run();
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		UiTheme.Palette palette = UiDraw.palette();
		int panelWidth = Math.min(MAX_PANEL_WIDTH, width - 4);
		int panelTop = rowsTop() - 28;
		UiDraw.panel(graphics, (width - panelWidth) / 2, panelTop, panelWidth, rowsTop() + languages.size() * ROW_HEIGHT + 60 - panelTop);
		super.render(graphics, mouseX, mouseY, partialTick);
		UiDraw.centered(graphics, font, title, width / 2, rowsTop() - 20, palette.ink());
		int left = (width - ROW_WIDTH) / 2;
		int y = rowsTop();
		for (String language : languages) {
			drawRow(graphics, language, left, y);
			y += ROW_HEIGHT;
		}
		String failed = languages.stream().filter(language -> ModelManager.phase(language) == ModelManager.Phase.FAILED).findFirst().orElse(null);
		int textWidth = panelWidth - 12;
		if (failed == null) {
			UiDraw.centered(graphics, font, UiDraw.fit(font, Component.translatable("langcraft.download.hint"), textWidth), width / 2, y + 4, palette.muted());
			return;
		}
		String key = ModelManager.failedAfterDownload(failed) ? "langcraft.download.load_failed_hint" : "langcraft.download.failed_hint";
		UiDraw.centered(graphics, font, UiDraw.fit(font, Component.translatable(key), textWidth), width / 2, y + 2, palette.red());
		UiDraw.centered(graphics, font, UiDraw.fit(font, ModelManager.failureReason(failed), textWidth), width / 2, y + 14, palette.muted());
	}

	private void drawRow(GuiGraphics graphics, String language, int left, int y) {
		ModelManager.Phase phase = ModelManager.phase(language);
		Component name = Component.translatable("langcraft.download.size",
				Component.translatable("langcraft.language." + language), Objects.requireNonNull(StudyLanguage.byCode(language), language).downloadSize());
		Component status = status(phase, language);
		UiTheme.Palette palette = UiDraw.palette();
		UiDraw.text(graphics, font, name, left, y, palette.ink());
		UiDraw.text(graphics, font, status, left + ROW_WIDTH - font.width(status), y, phase == ModelManager.Phase.FAILED ? palette.red() : palette.accent());

		float fraction = switch (phase) {
			case QUEUED, FAILED -> 0f;
			case DOWNLOADING -> ModelManager.downloadPercent(language) / 100f;
			case EXTRACTING, LOADING, READY -> 1f;
		};
		int barY = y + 12;
		graphics.fill(left, barY, left + ROW_WIDTH, barY + BAR_HEIGHT, palette.track());
		graphics.fill(left, barY, left + Math.round(ROW_WIDTH * fraction), barY + BAR_HEIGHT, palette.green());
	}

	private static Component status(ModelManager.Phase phase, String language) {
		return switch (phase) {
			case QUEUED -> Component.translatable("langcraft.download.queued");
			case DOWNLOADING -> Component.translatable("langcraft.download.percent", ModelManager.downloadPercent(language));
			case EXTRACTING -> Component.translatable("langcraft.download.extracting");
			case LOADING -> Component.translatable("langcraft.download.loading");
			case READY -> Component.translatable("langcraft.download.ready");
			case FAILED -> Component.translatable("langcraft.download.failed");
		};
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
