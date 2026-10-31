package dev.linguacraft.client.ui;

import dev.linguacraft.client.voice.ModelManager;
import dev.linguacraft.data.PlayerProgress;
import java.util.List;
import java.util.Map;
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
	private static final Map<String, String> DOWNLOAD_SIZES = Map.of(
			PlayerProgress.KOREAN, "160 MB",
			PlayerProgress.JAPANESE, "48 MB");

	private final List<String> languages;
	private final Runnable onReady;
	private final Runnable onSkip;
	private Button retryButton;
	private Button continueButton;

	public VoiceSetupScreen(List<String> languages, Runnable onReady, Runnable onSkip) {
		super(Component.translatable("linguacraft.download.title"));
		this.languages = languages;
		this.onReady = onReady;
		this.onSkip = onSkip;
	}

	@Override
	protected void init() {
		int y = rowsTop() + languages.size() * ROW_HEIGHT + 32;
		retryButton = addRenderableWidget(Button.builder(Component.translatable("linguacraft.download.retry"), button -> retry())
				.bounds(width / 2 - BUTTON_WIDTH - 4, y, BUTTON_WIDTH, 20)
				.build());
		continueButton = addRenderableWidget(Button.builder(Component.translatable("linguacraft.download.skip"), button -> onSkip.run())
				.bounds(width / 2 + 4, y, BUTTON_WIDTH, 20)
				.build());
		updateButtons();
	}

	private int rowsTop() {
		return height / 2 - 36 - languages.size() * ROW_HEIGHT / 2;
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
		continueButton.setMessage(Component.translatable(failed ? "linguacraft.download.no_voice" : "linguacraft.download.skip"));
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
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, rowsTop() - 20, UiColors.WHITE);
		int left = (width - ROW_WIDTH) / 2;
		int y = rowsTop();
		for (String language : languages) {
			drawRow(graphics, language, left, y);
			y += ROW_HEIGHT;
		}
		String failed = languages.stream().filter(language -> ModelManager.phase(language) == ModelManager.Phase.FAILED).findFirst().orElse(null);
		if (failed == null) {
			graphics.drawCenteredString(font, Component.translatable("linguacraft.download.hint"), width / 2, y + 4, UiColors.MUTED);
			return;
		}
		String key = ModelManager.failedAfterDownload(failed) ? "linguacraft.download.load_failed_hint" : "linguacraft.download.failed_hint";
		graphics.drawCenteredString(font, Component.translatable(key), width / 2, y + 2, UiColors.RED);
		graphics.drawCenteredString(font, font.plainSubstrByWidth(ModelManager.failureReason(failed), width - 20), width / 2, y + 14, UiColors.MUTED);
	}

	private void drawRow(GuiGraphics graphics, String language, int left, int y) {
		ModelManager.Phase phase = ModelManager.phase(language);
		Component name = Component.translatable("linguacraft.download.size",
				Component.translatable("linguacraft.language." + language), DOWNLOAD_SIZES.get(language));
		Component status = status(phase, language);
		graphics.drawString(font, name, left, y, UiColors.WHITE);
		graphics.drawString(font, status, left + ROW_WIDTH - font.width(status), y, phase == ModelManager.Phase.FAILED ? UiColors.RED : UiColors.GOLD);

		float fraction = switch (phase) {
			case QUEUED, FAILED -> 0f;
			case DOWNLOADING -> ModelManager.downloadPercent(language) / 100f;
			case EXTRACTING, LOADING, READY -> 1f;
		};
		int barY = y + 12;
		graphics.fill(left, barY, left + ROW_WIDTH, barY + BAR_HEIGHT, UiColors.TRACK);
		graphics.fill(left, barY, left + Math.round(ROW_WIDTH * fraction), barY + BAR_HEIGHT, UiColors.GREEN);
	}

	private static Component status(ModelManager.Phase phase, String language) {
		return switch (phase) {
			case QUEUED -> Component.translatable("linguacraft.download.queued");
			case DOWNLOADING -> Component.translatable("linguacraft.download.percent", ModelManager.downloadPercent(language));
			case EXTRACTING -> Component.translatable("linguacraft.download.extracting");
			case LOADING -> Component.translatable("linguacraft.download.loading");
			case READY -> Component.translatable("linguacraft.download.ready");
			case FAILED -> Component.translatable("linguacraft.download.failed");
		};
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
