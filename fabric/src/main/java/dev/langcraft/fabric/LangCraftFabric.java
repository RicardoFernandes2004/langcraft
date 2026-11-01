package dev.langcraft.fabric;

import dev.langcraft.LangCraft;
import dev.langcraft.command.LangCommand;
import dev.langcraft.net.CustomPacketPayload;
import dev.langcraft.net.Payloads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public class LangCraftFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		LangCraft.init(new FabricPlatform());
		Payloads.register();
		ServerPlayNetworking.registerGlobalReceiver(Payloads.CHANNEL, (server, player, handler, buf, sender) -> {
			CustomPacketPayload payload = Payloads.decode(buf);
			server.execute(() -> Payloads.handleOnServer(player, payload));
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> LangCraft.challenges.onServerTick(server));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LangCraft.challenges.onPlayerLeft(handler.player));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				LangCraft.challenges.cancel(player);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> LangCommand.register(dispatcher));
	}
}
