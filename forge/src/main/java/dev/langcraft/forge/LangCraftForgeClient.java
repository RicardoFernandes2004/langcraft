package dev.langcraft.forge;

import dev.langcraft.LangCraft;
import dev.langcraft.client.LangCraftClient;
import dev.langcraft.client.ui.LangConfigScreen;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

final class LangCraftForgeClient {
	private LangCraftForgeClient() {
	}

	static void init(FMLJavaModLoadingContext context) {
		context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new LangConfigScreen(parent)));
		LangCraftClient.registerReceivers(new LangCraftClient.Receivers() {
			@Override
			public <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
				ForgeNetwork.setClientHandler(type, handler);
			}
		});
		RegisterKeyMappingsEvent.BUS.addListener(event ->
				LangCraftClient.createKeys(KeyMapping.Category.register(LangCraft.id("main")), event::register));
		AddGuiOverlayLayersEvent.BUS.addListener(event ->
				LangCraftClient.registerHud((name, layer) -> event.getLayeredDraw().add(LangCraft.id(name), layer::render)));
		TickEvent.ClientTickEvent.Post.BUS.addListener(event -> LangCraftClient.onTick(Minecraft.getInstance()));
		ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(event -> LangCraftClient.onJoin());
		ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> LangCraftClient.onDisconnect(Minecraft.getInstance()));
		ScreenEvent.Init.Post.BUS.addListener(event -> {
			Button button = LangCraftClient.pauseButton(event.getScreen());
			if (button != null) {
				event.addListener(button);
			}
		});
		ItemTooltipEvent.BUS.addListener(event -> LangCraftClient.appendTooltip(event.getItemStack(), event.getToolTip()));
	}
}
