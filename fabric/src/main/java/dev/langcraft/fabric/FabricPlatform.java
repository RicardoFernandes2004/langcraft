package dev.langcraft.fabric;

import dev.langcraft.LangCraft;
import dev.langcraft.data.PlayerProgress;
import dev.langcraft.platform.Platform;
import java.nio.file.Path;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

final class FabricPlatform implements Platform {
	private static final AttachmentType<PlayerProgress> PROGRESS = AttachmentRegistry.create(LangCraft.id("progress"),
			builder -> builder.persistent(PlayerProgress.CODEC).copyOnDeath());

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public PlayerProgress progress(ServerPlayer player) {
		return player.getAttachedOrCreate(PROGRESS, LangCraft::newProgress);
	}

	@Override
	public void setProgress(ServerPlayer player, PlayerProgress progress) {
		player.setAttached(PROGRESS, progress);
	}

	@Override
	public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return ServerPlayNetworking.canSend(player, type);
	}

	@Override
	public void send(ServerPlayer player, CustomPacketPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}

	@Override
	public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
		return ClientPlayNetworking.canSend(type);
	}

	@Override
	public void sendToServer(CustomPacketPayload payload) {
		ClientPlayNetworking.send(payload);
	}
}
