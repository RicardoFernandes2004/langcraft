package dev.linguacraft.client.ui;

import dev.linguacraft.LinguaCraft;
import dev.linguacraft.client.voice.Microphones;
import dev.linguacraft.client.voice.ModelManager;
import dev.linguacraft.config.LinguaConfig;
import dev.linguacraft.data.PlayerProgress;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** A tela da engrenagem no Mod Menu. Salva ao fechar. */
public final class LinguaConfigScreen extends OptionsSubScreen {
	private static final int SECONDS_PER_MINUTE = 60;

	public LinguaConfigScreen(@Nullable Screen parent) {
		super(parent, net.minecraft.client.Minecraft.getInstance().options, Component.translatable("linguacraft.config.title"));
	}

	@Override
	protected void addOptions() {
		LinguaConfig config = LinguaCraft.config;

		list.addHeader(Component.translatable("linguacraft.config.voice"));
		list.addSmall(microphoneButton(config), testButton());
		list.addSmall(
				range("linguacraft.config.similarity", 50, 100, (int) Math.round(config.voiceSimilarity * 100), "linguacraft.config.percent", value -> config.voiceSimilarity = value / 100.0),
				OptionInstance.createBoolean("linguacraft.config.furigana", config.showFurigana, value -> config.showFurigana = value));

		list.addHeader(Component.translatable("linguacraft.config.intervals"));
		list.addSmall(
				minutes("linguacraft.config.item_min", config.itemIntervalMinSeconds, value -> config.itemIntervalMinSeconds = value),
				minutes("linguacraft.config.item_max", config.itemIntervalMaxSeconds, value -> config.itemIntervalMaxSeconds = value));
		list.addSmall(
				minutes("linguacraft.config.mob_min", config.mobIntervalMinSeconds, value -> config.mobIntervalMinSeconds = value),
				minutes("linguacraft.config.mob_max", config.mobIntervalMaxSeconds, value -> config.mobIntervalMaxSeconds = value));

		list.addHeader(Component.translatable("linguacraft.config.times"));
		list.addSmall(
				seconds("linguacraft.config.speak", 5, 120, config.speakSeconds, value -> config.speakSeconds = value),
				seconds("linguacraft.config.quiz", 5, 120, config.quizSeconds, value -> config.quizSeconds = value));
		list.addSmall(
				seconds("linguacraft.config.hold", 5, 120, config.holdSeconds, value -> config.holdSeconds = value),
				seconds("linguacraft.config.auto_spin", 3, 60, config.rouletteAutoSpinSeconds, value -> config.rouletteAutoSpinSeconds = value));

		list.addHeader(Component.translatable("linguacraft.config.reward"));
		list.addSmall(
				OptionInstance.createBoolean("linguacraft.config.reward_enabled", config.rewardRouletteEnabled, value -> config.rewardRouletteEnabled = value),
				range("linguacraft.config.reward_streak", 1, 20, config.rewardStreak, "linguacraft.config.wins", value -> config.rewardStreak = value));
	}

	private Button microphoneButton(LinguaConfig config) {
		Component name = config.microphone.equals(Microphones.SYSTEM_DEFAULT) ? Component.translatable(Microphones.defaultLabelKey()) : Component.literal(Microphones.label(config.microphone));
		return Button.builder(Options.genericValueLabel(Component.translatable("linguacraft.config.microphone"), name), button ->
				minecraft.setScreen(new MicrophoneScreen(this, chosen -> {
					config.microphone = chosen;
					config.save();
					minecraft.setScreen(this);
				}))).build();
	}

	/** O teste precisa de um modelo já baixado, o que acontece ao escolher o idioma dentro de um mundo. */
	private Button testButton() {
		List<String> downloaded = PlayerProgress.languagesOf(PlayerProgress.BOTH).stream().filter(ModelManager::isDownloaded).toList();
		Button button = Button.builder(Component.translatable("linguacraft.config.test"), pressed -> {
			downloaded.forEach(ModelManager::model);
			minecraft.setScreen(new MicTestScreen(downloaded, () -> minecraft.setScreen(this)));
		}).build();
		if (downloaded.isEmpty()) {
			button.active = false;
			button.setTooltip(Tooltip.create(Component.translatable("linguacraft.config.test_unavailable")));
		}
		return button;
	}

	private static OptionInstance<Integer> minutes(String key, int seconds, IntConsumer setter) {
		return range(key, 1, 60, Math.round((float) seconds / SECONDS_PER_MINUTE), "linguacraft.config.minutes", value -> setter.accept(value * SECONDS_PER_MINUTE));
	}

	private static OptionInstance<Integer> seconds(String key, int min, int max, int value, IntConsumer setter) {
		return range(key, min, max, value, "linguacraft.config.seconds", setter);
	}

	private static OptionInstance<Integer> range(String key, int min, int max, int value, String unitKey, IntConsumer setter) {
		return new OptionInstance<>(key, OptionInstance.noTooltip(),
				(caption, current) -> Options.genericValueLabel(caption, Component.translatable(unitKey, current)),
				new OptionInstance.IntRange(min, max), Math.max(min, Math.min(max, value)), setter::accept);
	}

	@Override
	public void removed() {
		super.removed();
		LinguaCraft.config.save();
	}
}
