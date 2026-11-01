package dev.linguacraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.linguacraft.LinguaCraft;
import dev.linguacraft.client.ui.DownloadHud;
import dev.linguacraft.client.ui.NotebookSounds;
import dev.linguacraft.client.ui.ScoreHud;
import dev.linguacraft.client.ui.SpeakAlertHud;
import dev.linguacraft.client.ui.StudyScreen;
import dev.linguacraft.net.ChallengeEndedPayload;
import dev.linguacraft.net.ChallengeStartPayload;
import dev.linguacraft.net.OpenScreenPayload;
import dev.linguacraft.net.RouletteResultPayload;
import dev.linguacraft.net.RouletteStartPayload;
import dev.linguacraft.net.ScorePayload;
import dev.linguacraft.net.VoiceSetupPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class LinguaCraftClient implements ClientModInitializer {
	public static KeyMapping typeAnswerKey;
	public static KeyMapping studyKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(LinguaCraft.id("main"));
		typeAnswerKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.linguacraft.type_answer",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, category));
		studyKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.linguacraft.study",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

		ClientPlayNetworking.registerGlobalReceiver(ChallengeStartPayload.TYPE, (payload, context) -> ChallengeClient.onStart(payload));
		ClientPlayNetworking.registerGlobalReceiver(ChallengeEndedPayload.TYPE, (payload, context) -> ChallengeClient.onEnded(payload));
		ClientPlayNetworking.registerGlobalReceiver(RouletteStartPayload.TYPE, (payload, context) -> ChallengeClient.onRouletteStart(payload));
		ClientPlayNetworking.registerGlobalReceiver(RouletteResultPayload.TYPE, (payload, context) -> ChallengeClient.onRouletteResult(payload));
		ClientPlayNetworking.registerGlobalReceiver(VoiceSetupPayload.TYPE, (payload, context) -> VoiceSetup.onSetup(payload));

		ClientPlayNetworking.registerGlobalReceiver(OpenScreenPayload.TYPE, (payload, context) -> ScreenRequests.onRequest(payload));
		ClientPlayNetworking.registerGlobalReceiver(ScorePayload.TYPE, (payload, context) -> ChallengeClient.onScore(payload));

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (studyKey.consumeClick() && minecraft.screen == null && minecraft.player != null) {
				minecraft.setScreen(new StudyScreen(null));
			}
			ScreenRequests.tick(minecraft);
			VoiceSetup.tick(minecraft);
			NotebookSounds.tick(minecraft);
			ChallengeClient.tick(minecraft, typeAnswerKey);
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ChallengeClient.preloadDictionary());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			ScreenRequests.clear();
			VoiceSetup.clear();
			ChallengeClient.clear();
		}));

		HudElementRegistry.addLast(LinguaCraft.id("score"), ScoreHud::render);
		HudElementRegistry.addLast(LinguaCraft.id("download"), DownloadHud::render);
		HudElementRegistry.addLast(LinguaCraft.id("alert"), SpeakAlertHud::render);
	}
}
