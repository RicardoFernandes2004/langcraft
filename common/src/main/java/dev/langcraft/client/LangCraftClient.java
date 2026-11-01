package dev.langcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.langcraft.client.ui.DownloadHud;
import dev.langcraft.client.ui.NotebookSounds;
import dev.langcraft.client.ui.ScoreHud;
import dev.langcraft.client.ui.SpeakAlertHud;
import dev.langcraft.client.ui.StudyScreen;
import dev.langcraft.net.ChallengeEndedPayload;
import dev.langcraft.net.ChallengeStartPayload;
import dev.langcraft.net.OpenScreenPayload;
import dev.langcraft.net.RouletteResultPayload;
import dev.langcraft.net.RouletteStartPayload;
import dev.langcraft.net.ScorePayload;
import dev.langcraft.net.VoiceSetupPayload;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.lwjgl.glfw.GLFW;

/** O lado do cliente, que é igual em todos os loaders. Eles só avisam quando cada coisa acontece. */
public final class LangCraftClient {
	public interface Receivers {
		<T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type, Consumer<T> handler);
	}

	public interface HudLayer {
		void render(GuiGraphics graphics, DeltaTracker delta);
	}

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
