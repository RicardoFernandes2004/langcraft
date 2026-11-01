package dev.langcraft.fabric;

import dev.langcraft.net.CustomPacketPayload;
import dev.langcraft.net.Payloads;
import dev.langcraft.platform.Platform;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

final class FabricPlatform implements Platform {
	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return ServerPlayNetworking.canSend(player, Payloads.CHANNEL);
	}

	@Override
	public void send(ServerPlayer player, CustomPacketPayload payload) {
		ServerPlayNetworking.send(player, Payloads.CHANNEL, encode(payload));
	}

	@Override
	public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
		return ClientPlayNetworking.canSend(Payloads.CHANNEL);
	}

	@Override
	public void sendToServer(CustomPacketPayload payload) {
		ClientPlayNetworking.send(Payloads.CHANNEL, encode(payload));
	}

	private static FriendlyByteBuf encode(CustomPacketPayload payload) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		Payloads.encode(payload, buf);
		return buf;
	}
}
