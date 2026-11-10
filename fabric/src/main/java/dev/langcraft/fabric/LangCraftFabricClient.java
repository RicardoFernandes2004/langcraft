package dev.langcraft.fabric;

import dev.langcraft.client.LangCraftClient;
import dev.langcraft.net.CustomPacketPayload;
import dev.langcraft.net.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;

public class LangCraftFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LangCraftClient.createKeys(KeyBindingHelper::registerKeyBinding);
		LangCraftClient.registerReceivers();
		ClientPlayNetworking.registerGlobalReceiver(Payloads.CHANNEL, (client, handler, buf, sender) -> {
			CustomPacketPayload payload = Payloads.decode(buf);
			client.execute(() -> Payloads.handleOnClient(payload));
		});
		ClientTickEvents.END_CLIENT_TICK.register(LangCraftClient::onTick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> LangCraftClient.onJoin());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LangCraftClient.onDisconnect(client));
		LangCraftClient.registerHud((name, layer) -> HudRenderCallback.EVENT.register(layer::render));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			Button button = LangCraftClient.pauseButton(screen);
			if (button != null) {
				Screens.getButtons(screen).add(button);
			}
		});
		ItemTooltipCallback.EVENT.register((stack, context, lines) -> LangCraftClient.appendTooltip(stack, lines));
	}
}
