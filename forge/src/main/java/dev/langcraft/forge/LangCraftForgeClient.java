package dev.langcraft.forge;

import dev.langcraft.client.LangCraftClient;
import dev.langcraft.client.ui.LangConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

final class LangCraftForgeClient {
	private LangCraftForgeClient() {
	}

	static void init() {
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new LangConfigScreen(parent)));
		LangCraftClient.registerReceivers();
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		modBus.addListener((RegisterKeyMappingsEvent event) -> LangCraftClient.createKeys(event::register));
		modBus.addListener((RegisterGuiOverlaysEvent event) -> LangCraftClient.registerHud((name, layer) ->
				event.registerAboveAll(name, (gui, graphics, partialTick, width, height) -> layer.render(graphics, partialTick))));
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				LangCraftClient.onTick(Minecraft.getInstance());
			}
		});
		MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> LangCraftClient.onJoin());
		MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> LangCraftClient.onDisconnect(Minecraft.getInstance()));
	}
}
