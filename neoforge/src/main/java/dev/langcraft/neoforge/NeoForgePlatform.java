package dev.langcraft.neoforge;

import dev.langcraft.data.PlayerProgress;
import dev.langcraft.platform.Platform;
import java.nio.file.Path;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

final class NeoForgePlatform implements Platform {
	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public PlayerProgress progress(ServerPlayer player) {
		return player.getData(LangCraftNeoForge.PROGRESS);
	}

	@Override
	public void setProgress(ServerPlayer player, PlayerProgress progress) {
		player.setData(LangCraftNeoForge.PROGRESS, progress);
	}

	@Override
	public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return player.connection.hasChannel(type);
	}

	@Override
	public void send(ServerPlayer player, CustomPacketPayload payload) {
		PacketDistributor.sendToPlayer(player, payload);
	}

	@Override
	public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
		return NeoForgeClientNetwork.canSend(type);
	}

	@Override
	public void sendToServer(CustomPacketPayload payload) {
		NeoForgeClientNetwork.send(payload);
	}
}
