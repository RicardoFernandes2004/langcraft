package dev.langcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.langcraft.LangCraft;
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
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class LangCraftClient implements ClientModInitializer {
	public static KeyMapping typeAnswerKey;
	public static KeyMapping studyKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(LangCraft.id("main"));
		typeAnswerKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.langcraft.type_answer",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, category));
		studyKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.langcraft.study",
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

		HudElementRegistry.addLast(LangCraft.id("score"), ScoreHud::render);
		HudElementRegistry.addLast(LangCraft.id("download"), DownloadHud::render);
		HudElementRegistry.addLast(LangCraft.id("alert"), SpeakAlertHud::render);
	}
}
