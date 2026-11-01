package dev.langcraft.fabric;

import dev.langcraft.LangCraft;
import dev.langcraft.command.LangCommand;
import dev.langcraft.net.Payloads;
import java.util.function.BiConsumer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public class LangCraftFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		LangCraft.init(new FabricPlatform());
		Payloads.register(new Payloads.Registrar() {
			@Override
			public <T extends CustomPacketPayload> void toClient(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec) {
				PayloadTypeRegistry.playS2C().register(type, codec);
			}

			@Override
			public <T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec, BiConsumer<ServerPlayer, T> handler) {
				PayloadTypeRegistry.playC2S().register(type, codec);
				ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(context.player(), payload));
			}
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
