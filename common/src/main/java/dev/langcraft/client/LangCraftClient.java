package dev.langcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.langcraft.client.ui.DownloadHud;
import dev.langcraft.client.ui.LangConfigScreen;
import dev.langcraft.client.ui.NotebookSounds;
import dev.langcraft.client.ui.ScoreHud;
import dev.langcraft.client.ui.SpeakAlertHud;
import dev.langcraft.client.ui.StudyScreen;
import dev.langcraft.net.ChallengeEndedPayload;
import dev.langcraft.net.ChallengeStartPayload;
import dev.langcraft.net.OpenScreenPayload;
import dev.langcraft.net.Payloads;
import dev.langcraft.net.PreferencesPayload;
import dev.langcraft.net.RouletteResultPayload;
import dev.langcraft.net.RouletteStartPayload;
import dev.langcraft.net.ScorePayload;
import dev.langcraft.net.TreasurePayload;
import dev.langcraft.net.VoiceSetupPayload;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** O lado do cliente, que é igual em todos os loaders. Eles só avisam quando cada coisa acontece. */
public final class LangCraftClient {
	public interface HudLayer {
		void render(GuiGraphics graphics, float partialTick);
	}

	private static final String KEY_CATEGORY = "key.category.langcraft.main";
	private static final int PAUSE_BUTTON_MARGIN = 6;
	private static final int PAUSE_BUTTON_WIDTH = 80;
	private static final int PAUSE_BUTTON_HEIGHT = 20;

	public static KeyMapping typeAnswerKey;
	public static KeyMapping studyKey;

	private LangCraftClient() {
	}

	/** Cria as teclas e entrega cada uma ao loader. */
	public static void createKeys(Consumer<KeyMapping> register) {
		typeAnswerKey = new KeyMapping("key.langcraft.type_answer", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, KEY_CATEGORY);
		studyKey = new KeyMapping("key.langcraft.study", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, KEY_CATEGORY);
		register.accept(typeAnswerKey);
		register.accept(studyKey);
	}

	public static void registerReceivers() {
		Payloads.setClientHandler(ChallengeStartPayload.TYPE, ChallengeClient::onStart);
		Payloads.setClientHandler(ChallengeEndedPayload.TYPE, ChallengeClient::onEnded);
		Payloads.setClientHandler(RouletteStartPayload.TYPE, ChallengeClient::onRouletteStart);
		Payloads.setClientHandler(RouletteResultPayload.TYPE, ChallengeClient::onRouletteResult);
		Payloads.setClientHandler(VoiceSetupPayload.TYPE, VoiceSetup::onSetup);
		Payloads.setClientHandler(OpenScreenPayload.TYPE, ScreenRequests::onRequest);
		Payloads.setClientHandler(ScorePayload.TYPE, ChallengeClient::onScore);
		Payloads.setClientHandler(PreferencesPayload.TYPE, ClientPreferences::onSync);
		Payloads.setClientHandler(TreasurePayload.TYPE, ChallengeClient::onTreasure);
	}

	/** O botão da configuração no canto do menu do Esc. O loader coloca na tela. Sem o menu, como no F3+Esc, a tela fica vazia. */
	public static @Nullable Button pauseButton(Screen screen) {
		if (!(screen instanceof PauseScreen) || screen.children().isEmpty()) {
			return null;
		}
		return Button.builder(Component.literal("LangCraft"), pressed -> Minecraft.getInstance().setScreen(new LangConfigScreen(screen)))
				.bounds(PAUSE_BUTTON_MARGIN, PAUSE_BUTTON_MARGIN, PAUSE_BUTTON_WIDTH, PAUSE_BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("langcraft.config.title")))
				.build();
	}

	/** Coloca no tooltip de um item o nome dele nos idiomas estudados. */
	public static void appendTooltip(ItemStack stack, List<Component> lines) {
		TooltipNames.append(stack, lines);
	}

	public static void registerHud(BiConsumer<String, HudLayer> register) {
		register.accept("score", ScoreHud::render);
		register.accept("download", DownloadHud::render);
		register.accept("alert", SpeakAlertHud::render);
	}

	public static void onTick(Minecraft minecraft) {
		if (studyKey.consumeClick() && minecraft.screen == null && minecraft.player != null) {
			minecraft.setScreen(new StudyScreen(null));
		}
		ScreenRequests.tick(minecraft);
		VoiceSetup.tick(minecraft);
		NotebookSounds.tick(minecraft);
		ChallengeClient.tick(minecraft, typeAnswerKey);
	}

	public static void onJoin() {
		ChallengeClient.preloadDictionary();
	}

	public static void onDisconnect(Minecraft minecraft) {
		minecraft.execute(() -> {
			ScreenRequests.clear();
			VoiceSetup.clear();
			ChallengeClient.clear();
		});
	}
}
