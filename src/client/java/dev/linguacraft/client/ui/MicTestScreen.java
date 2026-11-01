package dev.linguacraft.client.ui;

import dev.linguacraft.LinguaCraft;
import dev.linguacraft.challenge.SubjectType;
import dev.linguacraft.client.ChallengeClient;
import dev.linguacraft.client.lang.Furigana;
import dev.linguacraft.client.lang.GameNames;
import dev.linguacraft.client.voice.Microphones;
import dev.linguacraft.client.voice.VoiceRecognizer;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** Pede uma palavra fácil em cada idioma, para testar o microfone antes dos desafios. Nada aqui tem punição. */
public final class MicTestScreen extends Screen {
	private static final float WORD_SCALE = 2f;
	private static final int BAR_WIDTH = 200;
	private static final int PANEL_WIDTH = 340;
	private static final int WRONG_FLASH_TICKS = 16;
	private static final float LEVEL_FALLOFF = 0.85f;
	/** Reconhecida com facilidade nos dois idiomas, e o nome é quase igual em coreano e japonês. */
	private static final ItemStack TEST_ITEM = new ItemStack(Items.DIAMOND);

	private final List<String> languages;
	private final Runnable onDone;
	private int languageIndex;
	private String word = "";
	private String nativeName = "";
	private List<Furigana.Segment> segments = List.of();
	private @Nullable VoiceRecognizer voice;
	private @Nullable String heard;
	private boolean success;
	private int wrongFlashTicks;
	private float shownLevel;
	private Button continueButton;

	public MicTestScreen(List<String> languages, Runnable onDone) {
		super(Component.translatable("linguacraft.test.title"));
		this.languages = languages;
		this.onDone = onDone;
		startWord();
	}

	private void startWord() {
		stopVoice();
		String language = languages.get(languageIndex);
		String subject = BuiltInRegistries.ITEM.getKey(TEST_ITEM.getItem()).toString();
		word = GameNames.name(language, SubjectType.ITEM, subject).orElse(subject);
		nativeName = GameNames.nativeName(SubjectType.ITEM, subject);
		segments = ChallengeClient.segments(word, language);
		heard = null;
		success = false;
		voice = ChallengeClient.listenFor(word, language, GameNames.allNames(language, SubjectType.ITEM), this::onHeard);
	}

	/** Recebe o que o reconhecimento entendeu. */
	public void onHeard(String shown, boolean correct) {
		if (success) {
			return;
		}
		heard = shown;
		if (correct) {
			success = true;
			stopVoice();
			ChallengeClient.play(SoundEvents.PLAYER_LEVELUP, 1.4f);
		} else {
			wrongFlashTicks = WRONG_FLASH_TICKS;
			ChallengeClient.play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f);
		}
		updateButtons();
	}

	public String word() {
		return word;
	}

	public @Nullable VoiceRecognizer voice() {
		return voice;
	}

	@Override
	protected void init() {
		int y = height - 30;
		addRenderableWidget(new ThemedButton(width / 2 - 154, y, 150, 20, Component.translatable("linguacraft.test.change_microphone"), button -> changeMicrophone()));
		continueButton = addRenderableWidget(new ThemedButton(width / 2 + 4, y, 150, 20, Component.empty(), button -> advance()));
		updateButtons();
	}

	private void updateButtons() {
		if (continueButton != null) {
			boolean more = languageIndex < languages.size() - 1;
			String key = !success ? (more ? "linguacraft.test.skip_word" : "linguacraft.test.skip") : (more ? "linguacraft.test.next" : "linguacraft.test.continue");
			continueButton.setMessage(Component.translatable(key));
		}
	}

	private void advance() {
		if (languageIndex < languages.size() - 1) {
			languageIndex++;
			startWord();
			updateButtons();
		} else {
			stopVoice();
			onDone.run();
		}
	}

	private void changeMicrophone() {
		stopVoice();
		minecraft.setScreen(new MicrophoneScreen(this, name -> {
			LinguaCraft.config.microphone = name;
			LinguaCraft.config.save();
			minecraft.setScreen(this);
			startWord();
			updateButtons();
		}));
	}

	private void stopVoice() {
		if (voice != null) {
			voice.stop();
			voice = null;
		}
	}

	@Override
	public void tick() {
		if (wrongFlashTicks > 0) {
			wrongFlashTicks--;
		}
		float level = voice != null ? voice.level() : 0f;
		shownLevel = Math.max(level, shownLevel * LEVEL_FALLOFF);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		UiTheme.Palette palette = UiDraw.palette();
		int centerX = width / 2;
		int top = Math.max(8, height / 2 - 92);
		int panelWidth = Math.min(PANEL_WIDTH, width - 4);
		int textWidth = panelWidth - 16;
		int wordHeight = FuriganaText.height(font, segments, WORD_SCALE);
		int wordY = top + 44;
		int barY = wordY + wordHeight + 20;
		UiDraw.panel(graphics, centerX - panelWidth / 2, top - 6, panelWidth, barY + 48 - (top - 6));
		super.render(graphics, mouseX, mouseY, partialTick);

		UiDraw.centered(graphics, font, title, centerX, top, palette.ink());
		String mic = LinguaCraft.config.microphone;
		Component micName = mic.equals(Microphones.SYSTEM_DEFAULT) ? Component.translatable(Microphones.defaultLabelKey()) : Component.literal(Microphones.label(mic));
		UiDraw.centered(graphics, font, UiDraw.fit(font, micName, textWidth), centerX, top + 12, palette.muted());
		UiDraw.centered(graphics, font, Component.translatable("linguacraft.test.say",
				Component.translatable("linguacraft.language." + languages.get(languageIndex))), centerX, top + 30, palette.ink());

		int wordWidth = FuriganaText.width(font, segments, WORD_SCALE);
		int rowWidth = wordWidth + 22;
		int rowX = centerX - rowWidth / 2;
		graphics.renderItem(TEST_ITEM, rowX, wordY + wordHeight - 18);
		FuriganaText.draw(graphics, font, segments, rowX + 22, wordY, WORD_SCALE, success ? palette.green() : palette.ink(), palette.shadow());
		UiDraw.centered(graphics, font, nativeName, centerX, wordY + wordHeight + 4, palette.accent());

		UiDraw.centered(graphics, font, Component.translatable("linguacraft.test.level"), centerX, barY, palette.muted());
		graphics.fill(centerX - BAR_WIDTH / 2, barY + 11, centerX + BAR_WIDTH / 2, barY + 17, palette.track());
		graphics.fill(centerX - BAR_WIDTH / 2, barY + 11, centerX - BAR_WIDTH / 2 + Math.round(BAR_WIDTH * shownLevel), barY + 17, palette.green());

		UiDraw.centered(graphics, font, UiDraw.fit(font, status(), textWidth), centerX, barY + 23, statusColor());
		if (heard != null) {
			UiDraw.centered(graphics, font, UiDraw.fit(font, Component.translatable("linguacraft.hud.heard", heard), textWidth), centerX, barY + 35, palette.muted());
		}
	}

	private Component status() {
		if (success) {
			return Component.translatable("linguacraft.test.success");
		}
		if (wrongFlashTicks > 0) {
			return Component.translatable("linguacraft.test.try_again");
		}
		return voice == null ? Component.empty() : SpeakAlertHud.voiceStatus(voice);
	}

	private int statusColor() {
		UiTheme.Palette palette = UiDraw.palette();
		if (success) {
			return palette.green();
		}
		return wrongFlashTicks > 0 ? palette.red() : palette.accent();
	}

	@Override
	public void removed() {
		// Trocar de microfone reabre esta tela e inicia a voz de novo.
		stopVoice();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
