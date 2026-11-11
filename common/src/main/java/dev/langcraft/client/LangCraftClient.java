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
import dev.langcraft.net.PreferencesPayload;
import dev.langcraft.net.RouletteResultPayload;
import dev.langcraft.net.RouletteStartPayload;
import dev.langcraft.net.ScorePayload;
import dev.langcraft.net.TreasurePayload;
import dev.langcraft.net.VoiceSetupPayload;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** O lado do cliente, que é igual em todos os loaders. Eles só avisam quando cada coisa acontece. */
public final class LangCraftClient {
	public interface Receivers {
		<T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type, Consumer<T> handler);
	}

	public interface HudLayer {
		void render(GuiGraphics graphics, DeltaTracker delta);
	}

	private static final int PAUSE_BUTTON_MARGIN = 6;
	private static final int PAUSE_BUTTON_WIDTH = 80;
	private static final int PAUSE_BUTTON_HEIGHT = 20;

	public static KeyMapping typeAnswerKey;
	public static KeyMapping studyKey;

	private LangCraftClient() {
	}

	/** Cria as teclas e entrega cada uma ao loader. */
	public static void createKeys(KeyMapping.Category category, Consumer<KeyMapping> register) {
		typeAnswerKey = new KeyMapping("key.langcraft.type_answer", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, category);
		studyKey = new KeyMapping("key.langcraft.study", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category);
		register.accept(typeAnswerKey);
		register.accept(studyKey);
	}

	public static void registerReceivers(Receivers receivers) {
		receivers.receive(ChallengeStartPayload.TYPE, ChallengeClient::onStart);
		receivers.receive(ChallengeEndedPayload.TYPE, ChallengeClient::onEnded);
		receivers.receive(RouletteStartPayload.TYPE, ChallengeClient::onRouletteStart);
		receivers.receive(RouletteResultPayload.TYPE, ChallengeClient::onRouletteResult);
		receivers.receive(VoiceSetupPayload.TYPE, VoiceSetup::onSetup);
		receivers.receive(OpenScreenPayload.TYPE, ScreenRequests::onRequest);
		receivers.receive(ScorePayload.TYPE, ChallengeClient::onScore);
		receivers.receive(PreferencesPayload.TYPE, ClientPreferences::onSync);
		receivers.receive(TreasurePayload.TYPE, ChallengeClient::onTreasure);
	}

	/** O botão da configuração no canto do menu do Esc. O loader coloca na tela. */
	public static @Nullable Button pauseButton(Screen screen) {
		if (!(screen instanceof PauseScreen pause) || !pause.showsPauseMenu()) {
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
