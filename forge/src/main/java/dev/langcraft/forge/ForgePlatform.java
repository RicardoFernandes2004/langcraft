package dev.langcraft.forge;

import dev.langcraft.net.CustomPacketPayload;
import dev.langcraft.platform.Platform;
import java.nio.file.Path;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

final class ForgePlatform implements Platform {
	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return ForgeNetwork.isPresent(player);
	}

	@Override
	public void send(ServerPlayer player, CustomPacketPayload payload) {
		ForgeNetwork.send(player, payload);
	}

	@Override
	public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
		return ForgeClientNetwork.canSend();
	}

	@Override
	public void sendToServer(CustomPacketPayload payload) {
		ForgeNetwork.sendToServer(payload);
	}
}
