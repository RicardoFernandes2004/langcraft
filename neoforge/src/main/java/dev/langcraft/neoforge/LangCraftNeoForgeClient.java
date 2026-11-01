package dev.langcraft.neoforge;

import dev.langcraft.LangCraft;
import dev.langcraft.client.LangCraftClient;
import dev.langcraft.client.ui.LangConfigScreen;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = LangCraft.MOD_ID, dist = Dist.CLIENT)
public final class LangCraftNeoForgeClient {
	public LangCraftNeoForgeClient(IEventBus modBus, ModContainer container) {
		container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new LangConfigScreen(parent));
		modBus.addListener((RegisterKeyMappingsEvent event) -> {
			KeyMapping.Category category = new KeyMapping.Category(LangCraft.id("main"));
			event.registerCategory(category);
			LangCraftClient.createKeys(category, event::register);
		});
		modBus.addListener((RegisterClientPayloadHandlersEvent event) -> LangCraftClient.registerReceivers(new LangCraftClient.Receivers() {
			@Override
			public <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
				event.register(type, (payload, context) -> handler.accept(payload));
			}
		}));
		modBus.addListener((RegisterGuiLayersEvent event) -> LangCraftClient.registerHud((name, layer) -> event.registerAboveAll(LangCraft.id(name), layer::render)));
		NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> LangCraftClient.onTick(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> LangCraftClient.onJoin());
		NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> LangCraftClient.onDisconnect(Minecraft.getInstance()));
	}
}
