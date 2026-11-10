package dev.langcraft.fabric;

import dev.langcraft.LangCraft;
import dev.langcraft.client.LangCraftClient;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class LangCraftFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LangCraftClient.createKeys(KeyMapping.Category.register(LangCraft.id("main")), KeyBindingHelper::registerKeyBinding);
		LangCraftClient.registerReceivers(new LangCraftClient.Receivers() {
			@Override
			public <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
				ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(LangCraftClient::onTick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> LangCraftClient.onJoin());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LangCraftClient.onDisconnect(client));
		LangCraftClient.registerHud((name, layer) -> HudElementRegistry.addLast(LangCraft.id(name), layer::render));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			Button button = LangCraftClient.pauseButton(screen);
			if (button != null) {
				Screens.getButtons(screen).add(button);
			}
		});
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> LangCraftClient.appendTooltip(stack, lines));
	}
}
